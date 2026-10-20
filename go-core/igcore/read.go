package igcore

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/util/jsontime"
)

type MarkReadResult struct {
	ProtocolVersion string `json:"protocol_version"`
	ThreadID        string `json:"thread_id"`
	LongThreadID    string `json:"long_thread_id"`
	MessageID       string `json:"message_id"`
	TimestampMS     int64  `json:"timestamp_ms"`
	Validated       bool   `json:"validated"`
}

// MarkThreadRead marks a thread read up to messageID, or up to the newest
// message when messageID is empty. Instagram requires the long thread ID for
// read receipts, so the short ID is resolved through IGDThreadDetailQuery.
func (c *Client) MarkThreadRead(ctx context.Context, threadID, messageID string) (*MarkReadResult, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}

	threadID = strings.TrimSpace(threadID)
	if threadID == "" {
		return nil, errors.New("thread ID is required")
	}
	messageID = strings.TrimSpace(messageID)

	limit := 20
	if messageID != "" {
		limit = 100
	}
	detail, err := c.LoadThread(ctx, threadID, limit)
	if err != nil {
		return nil, err
	}

	longThreadID := strings.TrimSpace(detail.Thread.ThreadID)
	if longThreadID == "" {
		return nil, errors.New("Instagram did not return a long thread ID")
	}

	target, err := selectMessage(detail.Messages, messageID)
	if err != nil {
		return nil, err
	}
	if target.TimestampMS == 0 {
		return nil, errors.New("target message has no timestamp; cannot mark read")
	}

	first, validation := buildMarkReadRequests(longThreadID, target.ID, target.TimestampMS)
	if _, err := c.inner.MarkRead(ctx, first); err != nil {
		return nil, err
	}
	validationResponse, err := c.inner.MarkReadValidation(ctx, validation)
	if err != nil {
		return nil, err
	}

	validated := validationResponse != nil && validationResponse.Validated
	return &MarkReadResult{
		ProtocolVersion: ProtocolVersion,
		ThreadID:        threadID,
		LongThreadID:    longThreadID,
		MessageID:       target.ID,
		TimestampMS:     target.TimestampMS,
		Validated:       validated,
	}, nil
}

func selectMessage(messages []MessageView, messageID string) (MessageView, error) {
	if len(messages) == 0 {
		return MessageView{}, errors.New("thread has no messages")
	}
	if messageID == "" {
		return messages[len(messages)-1], nil
	}
	for _, message := range messages {
		if message.ID == messageID {
			return message, nil
		}
	}
	return MessageView{}, fmt.Errorf("message %q not found in the newest %d messages", messageID, len(messages))
}

func buildMarkReadRequests(longThreadID, messageID string, timestampMS int64) (*slidetypes.MarkReadRequest, *slidetypes.MarkReadRequest) {
	itemID := ""
	metadata := slidetypes.MarkReadMetadata{IGThreadIGID: longThreadID}
	return &slidetypes.MarkReadRequest{
		Metadata: metadata,
		Data: slidetypes.MarkReadData{
			MessageID: messageID,
			ItemID:    &itemID,
		},
	}, &slidetypes.MarkReadRequest{
		Metadata: metadata,
		Data: slidetypes.MarkReadData{
			MessageID:          messageID,
			MessageTimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(timestampMS)},
		},
	}
}
