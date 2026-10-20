package igcore

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"strings"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/mautrix-meta/pkg/messagix/methods"
)

type EditResult struct {
	ProtocolVersion    string `json:"protocol_version"`
	ThreadID           string `json:"thread_id"`
	MessageID          string `json:"message_id"`
	OfflineThreadingID string `json:"offline_threading_id"`
	Text               string `json:"text"`
	TimestampMS        int64  `json:"timestamp_ms,omitempty"`
}

// EditText replaces the text of one of the viewer's own messages. Instagram
// enforces a 15-minute window and a maximum of five edits.
func (c *Client) EditText(ctx context.Context, threadID, messageID, text, otid string) (*EditResult, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}

	request, err := buildEditMessageRequest(threadID, messageID, text, otid)
	if err != nil {
		return nil, err
	}
	if _, err := c.bootstrap(ctx); err != nil {
		return nil, err
	}

	response, err := c.inner.EditMessage(ctx, request)
	if err != nil {
		return nil, err
	}
	if response == nil {
		return nil, errors.New("Instagram returned an empty edit response")
	}
	return newEditResult(request, response.Message), nil
}

func buildEditMessageRequest(threadID, messageID, text, otid string) (*slidetypes.EditMessageRequest, error) {
	threadID = strings.TrimSpace(threadID)
	messageID = strings.TrimSpace(messageID)
	if threadID == "" {
		return nil, errors.New("thread ID is required")
	}
	if messageID == "" {
		return nil, errors.New("message ID is required")
	}
	if strings.TrimSpace(text) == "" {
		return nil, errors.New("text is required")
	}
	if length := len([]rune(text)); length > MaxTextLength {
		return nil, fmt.Errorf("text is %d characters; the maximum is %d", length, MaxTextLength)
	}
	if otid == "" {
		otid = strconv.FormatInt(methods.GenerateEpochID(), 10)
	}
	return &slidetypes.EditMessageRequest{
		ThreadID:           threadID,
		TargetItemID:       "",
		Body:               slidetypes.SensitiveString{Value: text},
		OfflineThreadingID: otid,
		TargetMessageID:    messageID,
	}, nil
}

func newEditResult(request *slidetypes.EditMessageRequest, message slidetypes.SentMessage) *EditResult {
	var timestampMS int64
	if !message.TimestampMS.IsZero() {
		timestampMS = message.TimestampMS.UnixMilli()
	}
	return &EditResult{
		ProtocolVersion:    ProtocolVersion,
		ThreadID:           request.ThreadID,
		MessageID:          request.TargetMessageID,
		OfflineThreadingID: request.OfflineThreadingID,
		Text:               request.Body.Value,
		TimestampMS:        timestampMS,
	}
}
