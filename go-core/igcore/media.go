package igcore

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
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

	// VoiceWaveformSamplingFrequency is the sample rate Instagram expects for
	// voice clip waveforms. The app samples MediaRecorder amplitudes at the
	// same rate.
	VoiceWaveformSamplingFrequency = 9

	// maxVoiceWaveformSamples bounds the decoded waveform. At the sampling
	// frequency above this covers just over three minutes of audio.
	maxVoiceWaveformSamples = 2000
)

type MediaSendResult struct {
	ProtocolVersion    string `json:"protocol_version"`
	ThreadID           string `json:"thread_id"`
	OfflineThreadingID string `json:"offline_threading_id"`
	AttachmentFBID     string `json:"attachment_fbid"`
	Filename           string `json:"filename"`
	MimeType           string `json:"mime_type"`
	SizeBytes          int64  `json:"size_bytes"`
	Voice              bool   `json:"voice,omitempty"`
	Waveform           bool   `json:"waveform,omitempty"`
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
	Voice     bool
	Waveform  []float64
}

// SendMedia uploads a local file through the web upload endpoint and sends the
// resulting attachment to an existing thread. threadID is the short IG chat ID.
func (c *Client) SendMedia(ctx context.Context, threadID, filePath, replyTo, otid string) (*MediaSendResult, error) {
	threadID, threadIDInt, err := parseMediaThreadID(c, threadID)
	if err != nil {
		return nil, err
	}
	file, err := readMediaFile(filePath)
	if err != nil {
		return nil, err
	}
	return c.sendMediaFile(ctx, threadID, threadIDInt, file, replyTo, otid)
}

// SendMediaBytes is the in-memory variant of SendMedia for mobile callers that
// already have the file contents. Empty mimeType is detected from the data.
func (c *Client) SendMediaBytes(ctx context.Context, threadID, filename, mimeType string, data []byte, replyTo, otid string) (*MediaSendResult, error) {
	threadID, threadIDInt, err := parseMediaThreadID(c, threadID)
	if err != nil {
		return nil, err
	}
	file, err := newMediaData(filename, mimeType, data)
	if err != nil {
		return nil, err
	}
	return c.sendMediaFile(ctx, threadID, threadIDInt, file, replyTo, otid)
}

func parseMediaThreadID(c *Client, threadID string) (string, int64, error) {
	if c == nil || c.inner == nil {
		return "", 0, errors.New("client is not initialized")
	}
	threadID = strings.TrimSpace(threadID)
	if threadID == "" {
		return "", 0, errors.New("thread ID is required")
	}
	threadIDInt, err := strconv.ParseInt(threadID, 10, 64)
	if err != nil {
		return "", 0, errors.New("thread ID must be numeric for media upload")
	}
	return threadID, threadIDInt, nil
}

// SendVoice uploads a local audio file as an Instagram voice note. The upload
// carries the voice_clip marker, so Instagram renders a playable voice bubble
// instead of a generic audio attachment.
func (c *Client) SendVoice(ctx context.Context, threadID, filePath, replyTo, otid string) (*MediaSendResult, error) {
	threadID, threadIDInt, err := parseMediaThreadID(c, threadID)
	if err != nil {
		return nil, err
	}
	file, err := readMediaFile(filePath)
	if err != nil {
		return nil, err
	}
	// M4A files sometimes sniff as video/mp4 because they share the MP4
	// container. Trust an audio extension over content detection.
	if byExtension := mime.TypeByExtension(strings.ToLower(filepath.Ext(filePath))); strings.HasPrefix(byExtension, "audio/") {
		file.MimeType = strings.TrimSpace(strings.Split(byExtension, ";")[0])
	}
	if err := prepareVoiceFile(file); err != nil {
		return nil, err
	}
	return c.sendMediaFile(ctx, threadID, threadIDInt, file, replyTo, otid)
}

// SendVoiceBytes is the in-memory variant of SendVoice for mobile callers.
// waveformJSON is an optional JSON array of amplitudes in the 0..1 range; an
// empty string lets Instagram render the note without a waveform.
func (c *Client) SendVoiceBytes(ctx context.Context, threadID, filename, mimeType string, data []byte, waveformJSON, replyTo, otid string) (*MediaSendResult, error) {
	threadID, threadIDInt, err := parseMediaThreadID(c, threadID)
	if err != nil {
		return nil, err
	}
	file, err := newMediaData(filename, mimeType, data)
	if err != nil {
		return nil, err
	}
	waveform, err := parseVoiceWaveform(waveformJSON)
	if err != nil {
		return nil, err
	}
	file.Waveform = waveform
	if err := prepareVoiceFile(file); err != nil {
		return nil, err
	}
	return c.sendMediaFile(ctx, threadID, threadIDInt, file, replyTo, otid)
}

// prepareVoiceFile marks an audio attachment as a voice clip. The MIME check
// keeps callers from accidentally sending an image as a voice note.
func prepareVoiceFile(file *mediaFile) error {
	if file == nil {
		return errors.New("media file is required")
	}
	if !strings.HasPrefix(file.MimeType, "audio/") {
		return fmt.Errorf("voice notes must be audio; got %q", file.MimeType)
	}
	file.Voice = true
	return nil
}

// parseVoiceWaveform decodes the amplitude array produced by the recorder.
// Invalid or empty input is not fatal: the note is still sent, just without a
// waveform.
func parseVoiceWaveform(raw string) ([]float64, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, nil
	}
	var amplitudes []float64
	if err := json.Unmarshal([]byte(raw), &amplitudes); err != nil {
		return nil, fmt.Errorf("voice waveform must be a JSON array of numbers: %w", err)
	}
	if len(amplitudes) > maxVoiceWaveformSamples {
		amplitudes = amplitudes[:maxVoiceWaveformSamples]
	}
	for index, amplitude := range amplitudes {
		if math.IsNaN(amplitude) || amplitude < 0 {
			amplitudes[index] = 0
			continue
		}
		if amplitude > 1 {
			amplitudes[index] = 1
		}
	}
	return amplitudes, nil
}

func (c *Client) sendMediaFile(ctx context.Context, threadID string, threadIDInt int64, file *mediaFile, replyTo, otid string) (*MediaSendResult, error) {
	if otid == "" {
		otid = strconv.FormatInt(methods.GenerateEpochID(), 10)
	}
	if _, err := c.bootstrap(ctx); err != nil {
		return nil, err
	}

	upload, err := c.inner.GetHTTP().SendMercuryUploadRequest(ctx, threadIDInt, newMercuryUpload(file))
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

func newMercuryUpload(file *mediaFile) *httpclient.MercuryUploadMedia {
	upload := &httpclient.MercuryUploadMedia{
		Filename:  file.Filename,
		MimeType:  file.MimeType,
		MediaData: file.Data,
	}
	if file.Voice {
		upload.IsVoiceClip = true
		if len(file.Waveform) > 0 {
			upload.WaveformData = &httpclient.WaveformData{
				Amplitudes:        file.Waveform,
				SamplingFrequency: VoiceWaveformSamplingFrequency,
			}
		}
	}
	return upload
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
		Voice:              file.Voice,
		Waveform:           len(file.Waveform) > 0,
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
	if info.Size() > MaxMediaBytes {
		return nil, fmt.Errorf("media file is %d bytes; the maximum is %d bytes", info.Size(), MaxMediaBytes)
	}

	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read media file: %w", err)
	}

	file, err := newMediaData(filepath.Base(path), "", data)
	if err != nil {
		return nil, err
	}
	file.Path = path
	return file, nil
}

func newMediaData(filename, mimeType string, data []byte) (*mediaFile, error) {
	if len(data) == 0 {
		return nil, errors.New("media file is empty")
	}

	filename = filepath.Base(strings.TrimSpace(filename))
	if filename == "" || filename == "." || filename == string(filepath.Separator) {
		filename = "upload"
	}

	detected := mimetype.Detect(data)
	mimeType = strings.TrimSpace(mimeType)
	if mimeType == "" || mimeType == "application/octet-stream" {
		mimeType = detected.String()
	}
	if mimeType == "" || mimeType == "application/octet-stream" {
		if byExtension := mime.TypeByExtension(strings.ToLower(filepath.Ext(filename))); byExtension != "" {
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
	if int64(len(data)) > limit {
		return nil, fmt.Errorf("media file is %d bytes; the limit for %s is %d bytes", len(data), mimeType, limit)
	}

	if filename == "upload" && detected.Extension() != "" {
		filename += detected.Extension()
	}

	return &mediaFile{
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

// ErrMediaForbidden marks a CDN response that usually means the signed URL
// expired. Callers can refresh the message and retry once.
var ErrMediaForbidden = errors.New("media download forbidden")

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
// without a thread query. A message from a recently loaded thread page is
// served from the short-lived cache.
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

	if c.cache != nil {
		if message, ok := c.cache.lookupMessage(threadID, messageID, threadCacheTTL); ok {
			return &message, nil
		}
	}
	return c.loadMessageFresh(ctx, threadID, messageID)
}

// loadMessageFresh bypasses the cache. The downloader uses it when the CDN
// rejects a cached URL, because the retry needs a newly signed URL.
func (c *Client) loadMessageFresh(ctx context.Context, threadID, messageID string) (*MessageView, error) {
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
// If the CDN rejects the URL with 403, the thread is queried once more,
// bypassing the message cache, and the download is retried with the refreshed
// URL.
func (c *Client) DownloadMediaPart(ctx context.Context, threadID, messageID string, partIndex int, destPath string) (*DownloadResult, error) {
	return c.downloadMediaPart(ctx, threadID, messageID, partIndex, destPath, c.LoadMessage, c.loadMessageFresh)
}

type messageLoader func(ctx context.Context, threadID, messageID string) (*MessageView, error)

func (c *Client) downloadMediaPart(ctx context.Context, threadID, messageID string, partIndex int, destPath string, load, refresh messageLoader) (*DownloadResult, error) {
	message, err := load(ctx, threadID, messageID)
	if err != nil {
		return nil, err
	}
	media, mediaURL, err := selectMediaPart(message, partIndex)
	if err != nil {
		return nil, err
	}
	destPath = strings.TrimSpace(destPath)
	if destPath == "" {
		return nil, errors.New("destination path is required")
	}

	result, err := downloadMediaURL(ctx, mediaURL, media.Type, media.AttachmentFBID, destPath, MaxMediaBytes)
	if errors.Is(err, ErrMediaForbidden) && refresh != nil {
		refreshed, refreshErr := refresh(ctx, threadID, messageID)
		if refreshErr != nil {
			return nil, fmt.Errorf("%w (refresh failed: %v)", err, refreshErr)
		}
		media, mediaURL, refreshErr = selectMediaPart(refreshed, partIndex)
		if refreshErr != nil {
			return nil, fmt.Errorf("%w (refresh failed: %v)", err, refreshErr)
		}
		result, err = downloadMediaURL(ctx, mediaURL, media.Type, media.AttachmentFBID, destPath, MaxMediaBytes)
	}
	if err != nil {
		return nil, err
	}

	result.ThreadID = strings.TrimSpace(threadID)
	result.MessageID = strings.TrimSpace(messageID)
	result.PartIndex = partIndex
	return result, nil
}

func selectMediaPart(message *MessageView, partIndex int) (MediaView, string, error) {
	if len(message.Media) == 0 {
		return MediaView{}, "", errors.New("message has no downloadable media")
	}
	if partIndex < 0 || partIndex >= len(message.Media) {
		return MediaView{}, "", fmt.Errorf("media part %d is out of range; message has %d part(s)", partIndex, len(message.Media))
	}
	media := message.Media[partIndex]
	if media.ViewOnce {
		return MediaView{}, "", errors.New("view-once media can only be viewed in the Instagram app")
	}
	mediaURL := firstNonEmpty(media.URL, media.FallbackURL, media.PreviewURL, media.PreviewFallbackURL)
	if mediaURL == "" {
		return MediaView{}, "", errors.New("media attachment has no download URL")
	}
	return media, mediaURL, nil
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

	if response.StatusCode == http.StatusForbidden {
		return nil, ErrMediaForbidden
	}
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
