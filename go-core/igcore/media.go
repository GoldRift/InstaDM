package igcore

import (
	"context"
	"errors"
	"fmt"
	"io"
	"mime"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/gabriel-vasile/mimetype"
	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/mautrix-meta/pkg/messagix/httpclient"
	"go.mau.fi/mautrix-meta/pkg/messagix/methods"
	"go.mau.fi/mautrix-meta/pkg/messagix/useragent"
)

const (
	MaxImageBytes = 8 * 1024 * 1024
	MaxMediaBytes = 25 * 1024 * 1024
)

type MediaSendResult struct {
	ProtocolVersion    string `json:"protocol_version"`
	ThreadID           string `json:"thread_id"`
	OfflineThreadingID string `json:"offline_threading_id"`
	AttachmentFBID     string `json:"attachment_fbid"`
	Filename           string `json:"filename"`
	MimeType           string `json:"mime_type"`
	SizeBytes          int64  `json:"size_bytes"`
	MessageID          string `json:"message_id,omitempty"`
	ID                 string `json:"id,omitempty"`
	TimestampMS        int64  `json:"timestamp_ms,omitempty"`
}

type mediaFile struct {
	Path      string
	Filename  string
	MimeType  string
	SizeBytes int64
	Data      []byte
}

// SendMedia uploads a local file through the web upload endpoint and sends the
// resulting attachment to an existing thread. threadID is the short IG chat ID.
func (c *Client) SendMedia(ctx context.Context, threadID, filePath, replyTo, otid string) (*MediaSendResult, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}

	threadID = strings.TrimSpace(threadID)
	if threadID == "" {
		return nil, errors.New("thread ID is required")
	}
	threadIDInt, err := strconv.ParseInt(threadID, 10, 64)
	if err != nil {
		return nil, errors.New("thread ID must be numeric for media upload")
	}

	file, err := readMediaFile(filePath)
	if err != nil {
		return nil, err
	}
	if otid == "" {
		otid = strconv.FormatInt(methods.GenerateEpochID(), 10)
	}
	if _, err := c.bootstrap(ctx); err != nil {
		return nil, err
	}

	upload, err := c.inner.GetHTTP().SendMercuryUploadRequest(ctx, threadIDInt, &httpclient.MercuryUploadMedia{
		Filename:  file.Filename,
		MimeType:  file.MimeType,
		MediaData: file.Data,
	})
	if err != nil {
		return nil, err
	}
	if upload == nil || upload.Payload.RealMetadata == nil {
		return nil, errors.New("Instagram returned no upload metadata")
	}
	attachmentFBID := upload.Payload.RealMetadata.GetFbId()
	if attachmentFBID == 0 {
		return nil, errors.New("Instagram returned no attachment ID")
	}
	attachmentID := strconv.FormatInt(attachmentFBID, 10)

	response, err := c.inner.SendMedia(ctx, buildSendMediaRequest(threadID, attachmentID, replyTo, otid))
	if err != nil {
		return nil, err
	}
	if response == nil {
		return nil, errors.New("Instagram returned an empty media send response")
	}

	return newMediaSendResult(threadID, otid, attachmentID, file, response.Message), nil
}

func buildSendMediaRequest(threadID, attachmentFBID, replyTo, otid string) *slidetypes.SendMediaRequest {
	request := &slidetypes.SendMediaRequest{
		AttachmentFBID:     attachmentFBID,
		ThreadID:           threadID,
		OfflineThreadingID: otid,
	}
	if replyTo = strings.TrimSpace(replyTo); replyTo != "" {
		request.ReplyToMessageID = &replyTo
	}
	return request
}

func newMediaSendResult(threadID, otid, attachmentFBID string, file *mediaFile, message slidetypes.SentMessage) *MediaSendResult {
	var timestampMS int64
	if !message.TimestampMS.IsZero() {
		timestampMS = message.TimestampMS.UnixMilli()
	}
	return &MediaSendResult{
		ProtocolVersion:    ProtocolVersion,
		ThreadID:           threadID,
		OfflineThreadingID: otid,
		AttachmentFBID:     attachmentFBID,
		Filename:           file.Filename,
		MimeType:           file.MimeType,
		SizeBytes:          file.SizeBytes,
		MessageID:          message.MessageID,
		ID:                 message.ID,
		TimestampMS:        timestampMS,
	}
}

func readMediaFile(path string) (*mediaFile, error) {
	path = strings.TrimSpace(path)
	if path == "" {
		return nil, errors.New("file path is required")
	}

	info, err := os.Stat(path)
	if err != nil {
		return nil, fmt.Errorf("media file: %w", err)
	}
	if info.IsDir() {
		return nil, errors.New("media path is a directory")
	}
	if info.Size() == 0 {
		return nil, errors.New("media file is empty")
	}

	detected, err := mimetype.DetectFile(path)
	if err != nil {
		return nil, fmt.Errorf("detect media type: %w", err)
	}
	mimeType := detected.String()
	if mimeType == "" || mimeType == "application/octet-stream" {
		if byExtension := mime.TypeByExtension(strings.ToLower(filepath.Ext(path))); byExtension != "" {
			mimeType = byExtension
		}
	}
	if mimeType == "" {
		mimeType = "application/octet-stream"
	}
	if err := validateMediaType(mimeType); err != nil {
		return nil, err
	}

	limit := maxMediaBytes(mimeType)
	if info.Size() > limit {
		return nil, fmt.Errorf("media file is %d bytes; the limit for %s is %d bytes", info.Size(), mimeType, limit)
	}

	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read media file: %w", err)
	}

	filename := filepath.Base(path)
	if filename == "" || filename == "." || filename == string(filepath.Separator) {
		filename = "upload" + detected.Extension()
	}

	return &mediaFile{
		Path:      path,
		Filename:  filename,
		MimeType:  mimeType,
		SizeBytes: int64(len(data)),
		Data:      data,
	}, nil
}

func validateMediaType(mimeType string) error {
	switch mimeType {
	case "image/jpeg", "image/png", "image/gif", "image/webp",
		"video/mp4", "video/quicktime", "video/webm", "video/ogg",
		"audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav":
		return nil
	}
	if strings.HasPrefix(mimeType, "application/") {
		return nil
	}
	return fmt.Errorf("unsupported media type %q", mimeType)
}

func maxMediaBytes(mimeType string) int64 {
	if strings.HasPrefix(mimeType, "image/") {
		return MaxImageBytes
	}
	return MaxMediaBytes
}

type DownloadResult struct {
	ProtocolVersion string `json:"protocol_version"`
	ThreadID        string `json:"thread_id"`
	MessageID       string `json:"message_id"`
	PartIndex       int    `json:"part_index"`
	Type            string `json:"type"`
	AttachmentFBID  string `json:"attachment_fbid,omitempty"`
	MimeType        string `json:"mime_type"`
	Path            string `json:"path"`
	SizeBytes       int64  `json:"size_bytes"`
	URL             string `json:"url"`
}

// LoadMessage fetches the newest page and returns one message by IG message ID.
// It is used by the media commands because attachment URLs are not available
// without a thread query.
func (c *Client) LoadMessage(ctx context.Context, threadID, messageID string) (*MessageView, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}
	threadID = strings.TrimSpace(threadID)
	messageID = strings.TrimSpace(messageID)
	if threadID == "" {
		return nil, errors.New("thread ID is required")
	}
	if messageID == "" {
		return nil, errors.New("message ID is required")
	}

	detail, err := c.LoadThread(ctx, threadID, 100)
	if err != nil {
		return nil, err
	}
	message, err := selectMessage(detail.Messages, messageID)
	if err != nil {
		return nil, err
	}
	return &message, nil
}

// DownloadMediaPart downloads one attachment from a message to destPath. The
// attachment URL comes from a fresh thread query, so it has the current expiry.
func (c *Client) DownloadMediaPart(ctx context.Context, threadID, messageID string, partIndex int, destPath string) (*DownloadResult, error) {
	message, err := c.LoadMessage(ctx, threadID, messageID)
	if err != nil {
		return nil, err
	}
	if len(message.Media) == 0 {
		return nil, errors.New("message has no downloadable media")
	}
	if partIndex < 0 || partIndex >= len(message.Media) {
		return nil, fmt.Errorf("media part %d is out of range; message has %d part(s)", partIndex, len(message.Media))
	}

	media := message.Media[partIndex]
	mediaURL := firstNonEmpty(media.URL, media.FallbackURL, media.PreviewURL, media.PreviewFallbackURL)
	if mediaURL == "" {
		return nil, errors.New("media attachment has no download URL")
	}
	destPath = strings.TrimSpace(destPath)
	if destPath == "" {
		return nil, errors.New("destination path is required")
	}

	result, err := downloadMediaURL(ctx, mediaURL, media.Type, media.AttachmentFBID, destPath, MaxMediaBytes)
	if err != nil {
		return nil, err
	}
	result.ThreadID = strings.TrimSpace(threadID)
	result.MessageID = strings.TrimSpace(messageID)
	result.PartIndex = partIndex
	return result, nil
}

func downloadMediaURL(ctx context.Context, rawURL, mediaType, attachmentFBID, destPath string, maxBytes int64) (*DownloadResult, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, fmt.Errorf("prepare media download: %w", err)
	}
	setDownloadHeaders(request, mediaType)

	response, err := http.DefaultClient.Do(request)
	if err != nil {
		return nil, fmt.Errorf("download media: %w", err)
	}
	defer response.Body.Close()

	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, fmt.Errorf("media download returned HTTP %d", response.StatusCode)
	}
	if response.ContentLength > maxBytes {
		return nil, fmt.Errorf("media is %d bytes; the download limit is %d bytes", response.ContentLength, maxBytes)
	}

	data, err := io.ReadAll(io.LimitReader(response.Body, maxBytes+1))
	if err != nil {
		return nil, fmt.Errorf("read media response: %w", err)
	}
	if int64(len(data)) > maxBytes {
		return nil, fmt.Errorf("media exceeds the %d byte download limit", maxBytes)
	}
	if len(data) == 0 {
		return nil, errors.New("media response is empty")
	}

	mimeType := response.Header.Get("Content-Type")
	if index := strings.IndexByte(mimeType, ';'); index >= 0 {
		mimeType = mimeType[:index]
	}
	mimeType = strings.TrimSpace(mimeType)
	if mimeType == "" || mimeType == "application/octet-stream" {
		mimeType = http.DetectContentType(data)
	}

	if err := os.WriteFile(destPath, data, 0o600); err != nil {
		return nil, fmt.Errorf("write media file: %w", err)
	}

	return &DownloadResult{
		ProtocolVersion: ProtocolVersion,
		Type:            mediaType,
		AttachmentFBID:  attachmentFBID,
		MimeType:        mimeType,
		Path:            destPath,
		SizeBytes:       int64(len(data)),
		URL:             rawURL,
	}, nil
}

func setDownloadHeaders(request *http.Request, mediaType string) {
	request.Header.Set("Accept", "*/*")
	destination := "empty"
	switch mediaType {
	case "image":
		request.Header.Set("Accept", "image/avif,image/webp,*/*")
		destination = "image"
	case "video":
		destination = "video"
	case "audio":
		destination = "audio"
	}
	request.Header.Set("Sec-Fetch-Dest", destination)
	request.Header.Set("Sec-Fetch-Mode", "no-cors")
	request.Header.Set("Sec-Fetch-Site", "cross-site")
	request.Header.Set("User-Agent", useragent.UserAgent)
	request.Header.Set("sec-ch-ua", useragent.SecCHUserAgent)
	request.Header.Set("sec-ch-ua-platform", useragent.SecCHPlatform)
}
