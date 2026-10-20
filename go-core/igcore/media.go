package igcore

import (
	"context"
	"errors"
	"fmt"
	"mime"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/gabriel-vasile/mimetype"
	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/mautrix-meta/pkg/messagix/httpclient"
	"go.mau.fi/mautrix-meta/pkg/messagix/methods"
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

func maxMediaBytes(mimeType string) int64 {
	if strings.HasPrefix(mimeType, "image/") {
		return MaxImageBytes
	}
	return MaxMediaBytes
}
