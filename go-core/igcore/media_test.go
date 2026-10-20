package igcore

import (
	"context"
	"image"
	"image/color"
	"image/png"
	"os"
	"path/filepath"
	"strings"
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
	file, err := os.Create(path)
	if err != nil {
		t.Fatalf("create PNG: %v", err)
	}
	defer file.Close()

	img := image.NewRGBA(image.Rect(0, 0, 2, 2))
	img.Set(0, 0, color.RGBA{R: 255, A: 255})
	img.Set(1, 0, color.RGBA{G: 255, A: 255})
	img.Set(0, 1, color.RGBA{B: 255, A: 255})
	if err := png.Encode(file, img); err != nil {
		t.Fatalf("encode PNG: %v", err)
	}
	return path
}
