package igcore

import (
	"bytes"
	"context"
	"errors"
	"image"
	"image/color"
	"image/png"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow"
	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/util/jsontime"
)

func TestMaxMediaBytes(t *testing.T) {
	if got := maxMediaBytes("image/png"); got != MaxImageBytes {
		t.Errorf("image limit = %d, want %d", got, MaxImageBytes)
	}
	if got := maxMediaBytes("video/mp4"); got != MaxMediaBytes {
		t.Errorf("video limit = %d, want %d", got, MaxMediaBytes)
	}
	if got := maxMediaBytes("application/pdf"); got != MaxMediaBytes {
		t.Errorf("file limit = %d, want %d", got, MaxMediaBytes)
	}
}

func TestBuildSendMediaRequest(t *testing.T) {
	request := buildSendMediaRequest("thread-id", "attachment-id", "", "otid-1")
	if request.AttachmentFBID != "attachment-id" || request.ThreadID != "thread-id" || request.OfflineThreadingID != "otid-1" {
		t.Errorf("request = %+v", request)
	}
	if request.ReplyToMessageID != nil {
		t.Errorf("reply_to_message_id = %v, want nil", request.ReplyToMessageID)
	}

	withReply := buildSendMediaRequest("thread-id", "attachment-id", "message-id", "otid-1")
	if withReply.ReplyToMessageID == nil || *withReply.ReplyToMessageID != "message-id" {
		t.Errorf("reply_to_message_id = %v", withReply.ReplyToMessageID)
	}
}

func TestNewMediaSendResult(t *testing.T) {
	file := &mediaFile{
		Filename:  "photo.png",
		MimeType:  "image/png",
		SizeBytes: 1234,
	}
	message := slidetypes.SentMessage{
		MessageID:   "message-id",
		ID:          "internal-id",
		TimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)},
	}

	result := newMediaSendResult("thread-id", "otid-1", "attachment-id", file, message)
	if result.ProtocolVersion != ProtocolVersion || result.ThreadID != "thread-id" || result.OfflineThreadingID != "otid-1" {
		t.Errorf("result = %+v", result)
	}
	if result.AttachmentFBID != "attachment-id" || result.Filename != "photo.png" || result.MimeType != "image/png" || result.SizeBytes != 1234 {
		t.Errorf("result file fields = %+v", result)
	}
	if result.MessageID != "message-id" || result.ID != "internal-id" || result.TimestampMS != 1_700_000_000_000 {
		t.Errorf("result message fields = %+v", result)
	}
}

func TestNewMediaData(t *testing.T) {
	data := encodeTestPNG(t)
	file, err := newMediaData("photo.bin", "", data)
	if err != nil {
		t.Fatalf("newMediaData: %v", err)
	}
	if file.Filename != "photo.bin" || file.MimeType != "image/png" {
		t.Errorf("file = %+v", file)
	}
	if file.SizeBytes != int64(len(data)) || len(file.Data) != len(data) {
		t.Errorf("size/data = %d/%d, want %d", file.SizeBytes, len(file.Data), len(data))
	}

	unnamed, err := newMediaData("", "", data)
	if err != nil {
		t.Fatalf("newMediaData unnamed: %v", err)
	}
	if unnamed.Filename != "upload.png" {
		t.Errorf("unnamed filename = %q, want upload.png", unnamed.Filename)
	}
}

func TestSendMediaBytesValidation(t *testing.T) {
	client := &Client{inner: &instameow.Client{}}
	pngData := encodeTestPNG(t)

	if _, err := client.SendMediaBytes(context.Background(), "", "photo.png", "image/png", pngData, "", ""); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("empty thread error = %v", err)
	}
	if _, err := client.SendMediaBytes(context.Background(), "not-a-number", "photo.png", "image/png", pngData, "", ""); err == nil || !strings.Contains(err.Error(), "numeric") {
		t.Errorf("non-numeric thread error = %v", err)
	}
	if _, err := client.SendMediaBytes(context.Background(), "123", "photo.png", "image/png", nil, "", ""); err == nil || !strings.Contains(err.Error(), "empty") {
		t.Errorf("empty data error = %v", err)
	}
	if _, err := client.SendMediaBytes(context.Background(), "123", "page.html", "text/html", []byte("<!doctype html>"), "", ""); err == nil || !strings.Contains(err.Error(), "unsupported media type") {
		t.Errorf("unsupported type error = %v", err)
	}
}

func TestReadMediaFile(t *testing.T) {
	path := writeTestPNG(t)

	file, err := readMediaFile(path)
	if err != nil {
		t.Fatalf("readMediaFile: %v", err)
	}
	if file.Filename != "test.png" {
		t.Errorf("filename = %q", file.Filename)
	}
	if file.MimeType != "image/png" {
		t.Errorf("mime type = %q", file.MimeType)
	}
	if file.SizeBytes == 0 || len(file.Data) == 0 {
		t.Errorf("empty media data: size=%d data=%d", file.SizeBytes, len(file.Data))
	}
}

func TestValidateMediaType(t *testing.T) {
	for _, mimeType := range []string{
		"image/jpeg", "image/png", "image/gif", "image/webp",
		"video/mp4", "video/quicktime", "video/webm", "video/ogg",
		"audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav",
		"application/pdf", "application/octet-stream",
	} {
		if err := validateMediaType(mimeType); err != nil {
			t.Errorf("validateMediaType(%q) = %v", mimeType, err)
		}
	}
	for _, mimeType := range []string{"text/html", "image/svg+xml"} {
		if err := validateMediaType(mimeType); err == nil {
			t.Errorf("validateMediaType(%q) accepted unsupported type", mimeType)
		}
	}
}

func TestReadMediaFileUnsupported(t *testing.T) {
	path := filepath.Join(t.TempDir(), "page.html")
	if err := os.WriteFile(path, []byte("<!doctype html><title>test</title>"), 0o600); err != nil {
		t.Fatalf("write HTML: %v", err)
	}
	if _, err := readMediaFile(path); err == nil || !strings.Contains(err.Error(), "unsupported media type") {
		t.Errorf("error = %v, want unsupported media type", err)
	}
}

func TestReadMediaFileValidation(t *testing.T) {
	if _, err := readMediaFile(""); err == nil || !strings.Contains(err.Error(), "file path is required") {
		t.Errorf("empty path error = %v", err)
	}
	if _, err := readMediaFile(t.TempDir()); err == nil || !strings.Contains(err.Error(), "directory") {
		t.Errorf("directory error = %v", err)
	}
	empty := filepath.Join(t.TempDir(), "empty.png")
	if err := os.WriteFile(empty, nil, 0o600); err != nil {
		t.Fatalf("write empty file: %v", err)
	}
	if _, err := readMediaFile(empty); err == nil || !strings.Contains(err.Error(), "empty") {
		t.Errorf("empty file error = %v", err)
	}
}

func TestSendMediaValidation(t *testing.T) {
	client := &Client{inner: &instameow.Client{}}

	if _, err := client.SendMedia(context.Background(), "", "file.png", "", ""); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("empty thread error = %v", err)
	}
	if _, err := client.SendMedia(context.Background(), "not-a-number", "file.png", "", ""); err == nil || !strings.Contains(err.Error(), "numeric") {
		t.Errorf("non-numeric thread error = %v", err)
	}
	if _, err := client.SendMedia(context.Background(), "123", "", "", ""); err == nil || !strings.Contains(err.Error(), "file path is required") {
		t.Errorf("empty file error = %v", err)
	}
}

func writeTestPNG(t *testing.T) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "test.png")
	if err := os.WriteFile(path, encodeTestPNG(t), 0o600); err != nil {
		t.Fatalf("write PNG: %v", err)
	}
	return path
}

func encodeTestPNG(t *testing.T) []byte {
	t.Helper()
	var buffer bytes.Buffer
	img := image.NewRGBA(image.Rect(0, 0, 2, 2))
	img.Set(0, 0, color.RGBA{R: 255, A: 255})
	img.Set(1, 0, color.RGBA{G: 255, A: 255})
	img.Set(0, 1, color.RGBA{B: 255, A: 255})
	if err := png.Encode(&buffer, img); err != nil {
		t.Fatalf("encode PNG: %v", err)
	}
	return buffer.Bytes()
}

func TestLoadMessageValidation(t *testing.T) {
	client := &Client{inner: &instameow.Client{}}
	if _, err := client.LoadMessage(context.Background(), "", "message-id"); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("empty thread error = %v", err)
	}
	if _, err := client.LoadMessage(context.Background(), "thread-id", ""); err == nil || !strings.Contains(err.Error(), "message ID is required") {
		t.Errorf("empty message error = %v", err)
	}
}

func TestDownloadMediaPartValidation(t *testing.T) {
	var nilClient *Client
	if _, err := nilClient.DownloadMediaPart(context.Background(), "thread-id", "message-id", 0, "out.png"); err == nil || !strings.Contains(err.Error(), "client is not initialized") {
		t.Errorf("nil client error = %v", err)
	}

	client := &Client{inner: &instameow.Client{}}
	if _, err := client.DownloadMediaPart(context.Background(), "thread-id", "", 0, "out.png"); err == nil || !strings.Contains(err.Error(), "message ID is required") {
		t.Errorf("empty message error = %v", err)
	}
}

func TestDownloadMediaURL(t *testing.T) {
	pngData := encodeTestPNG(t)
	var fetchDestination string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fetchDestination = r.Header.Get("Sec-Fetch-Dest")
		if r.Header.Get("User-Agent") == "" {
			t.Error("download request has no user agent")
		}
		if !strings.Contains(r.Header.Get("Accept"), "image/") {
			t.Errorf("accept header = %q", r.Header.Get("Accept"))
		}
		w.Header().Set("Content-Type", "image/png")
		_, _ = w.Write(pngData)
	}))
	defer server.Close()

	dest := filepath.Join(t.TempDir(), "downloaded.png")
	result, err := downloadMediaURL(context.Background(), server.URL, "image", "fbid-1", dest, MaxMediaBytes)
	if err != nil {
		t.Fatalf("downloadMediaURL: %v", err)
	}
	if result.Type != "image" || result.AttachmentFBID != "fbid-1" || result.MimeType != "image/png" {
		t.Errorf("result = %+v", result)
	}
	if result.SizeBytes != int64(len(pngData)) {
		t.Errorf("size = %d, want %d", result.SizeBytes, len(pngData))
	}
	if fetchDestination != "image" {
		t.Errorf("sec-fetch-dest = %q, want image", fetchDestination)
	}
	data, err := os.ReadFile(dest)
	if err != nil {
		t.Fatalf("read downloaded file: %v", err)
	}
	if !bytes.Equal(data, pngData) {
		t.Error("downloaded bytes differ from source")
	}
}

func TestDownloadMediaURLDetectMIME(t *testing.T) {
	pngData := encodeTestPNG(t)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/octet-stream")
		_, _ = w.Write(pngData)
	}))
	defer server.Close()

	dest := filepath.Join(t.TempDir(), "downloaded.bin")
	result, err := downloadMediaURL(context.Background(), server.URL, "image", "", dest, MaxMediaBytes)
	if err != nil {
		t.Fatalf("downloadMediaURL: %v", err)
	}
	if result.MimeType != "image/png" {
		t.Errorf("mime type = %q, want image/png", result.MimeType)
	}
}

func TestDownloadMediaURLLimit(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write(make([]byte, 32))
	}))
	defer server.Close()

	dest := filepath.Join(t.TempDir(), "too-large.bin")
	if _, err := downloadMediaURL(context.Background(), server.URL, "image", "", dest, 16); err == nil || !strings.Contains(err.Error(), "limit") {
		t.Errorf("error = %v, want size limit", err)
	}
}

func TestDownloadMediaURLHTTPError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "forbidden", http.StatusForbidden)
	}))
	defer server.Close()

	dest := filepath.Join(t.TempDir(), "forbidden.bin")
	if _, err := downloadMediaURL(context.Background(), server.URL, "image", "", dest, MaxMediaBytes); !errors.Is(err, ErrMediaForbidden) {
		t.Errorf("error = %v, want ErrMediaForbidden", err)
	}
}

func TestDownloadMediaPartRefreshesForbiddenURL(t *testing.T) {
	pngData := encodeTestPNG(t)
	var oldHits, newHits atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/old":
			oldHits.Add(1)
			http.Error(w, "forbidden", http.StatusForbidden)
		case "/new":
			newHits.Add(1)
			w.Header().Set("Content-Type", "image/png")
			_, _ = w.Write(pngData)
		default:
			http.NotFound(w, r)
		}
	}))
	defer server.Close()

	loads := 0
	loader := func(ctx context.Context, threadID, messageID string) (*MessageView, error) {
		loads++
		return &MessageView{
			ID: messageID,
			Media: []MediaView{
				{Type: "image", URL: server.URL + "/old"},
			},
		}, nil
	}
	refreshes := 0
	refresh := func(ctx context.Context, threadID, messageID string) (*MessageView, error) {
		refreshes++
		return &MessageView{
			ID: messageID,
			Media: []MediaView{
				{Type: "image", URL: server.URL + "/new"},
			},
		}, nil
	}

	dest := filepath.Join(t.TempDir(), "refreshed.png")
	result, err := (&Client{}).downloadMediaPart(context.Background(), "thread-id", "message-id", 0, dest, loader, refresh)
	if err != nil {
		t.Fatalf("downloadMediaPart: %v", err)
	}
	if loads != 1 || refreshes != 1 {
		t.Errorf("loads/refreshes = %d/%d, want 1/1", loads, refreshes)
	}
	if oldHits.Load() != 1 || newHits.Load() != 1 {
		t.Errorf("hits old/new = %d/%d, want 1/1", oldHits.Load(), newHits.Load())
	}
	if result.URL != server.URL+"/new" {
		t.Errorf("result URL = %q, want refreshed URL", result.URL)
	}
	data, err := os.ReadFile(dest)
	if err != nil {
		t.Fatalf("read downloaded file: %v", err)
	}
	if !bytes.Equal(data, pngData) {
		t.Error("downloaded bytes differ from source")
	}
}
