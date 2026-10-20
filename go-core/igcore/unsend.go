package igcore

import (
	"context"
	"errors"
	"strings"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
)

type UnsendResult struct {
	ProtocolVersion string `json:"protocol_version"`
	ThreadID        string `json:"thread_id"`
	LongThreadID    string `json:"long_thread_id"`
	MessageID       string `json:"message_id"`
	Unsent          bool   `json:"unsent"`
}

// UnsendMessage removes one of the viewer's own messages for everyone. The
// unsend mutation requires the long thread ID, so the short ID is resolved
// through IGDThreadDetailQuery first.
func (c *Client) UnsendMessage(ctx context.Context, threadID, messageID string) (*UnsendResult, error) {
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

	detail, err := c.LoadThread(ctx, threadID, 20)
	if err != nil {
		return nil, err
	}
	longThreadID := strings.TrimSpace(detail.Thread.ThreadID)
	if longThreadID == "" {
		return nil, errors.New("Instagram did not return a long thread ID")
	}

	response, err := c.inner.UnsendMessage(ctx, buildUnsendMessageRequest(messageID, longThreadID))
	if err != nil {
		return nil, err
	}
	if response == nil {
		return nil, errors.New("Instagram returned an empty unsend response")
	}

	return &UnsendResult{
		ProtocolVersion: ProtocolVersion,
		ThreadID:        threadID,
		LongThreadID:    longThreadID,
		MessageID:       messageID,
		Unsent:          response.DirectUnsendMessage,
	}, nil
}

func buildUnsendMessageRequest(messageID, longThreadID string) *slidetypes.UnsendMessageRequest {
	return &slidetypes.UnsendMessageRequest{
		MessageID: messageID,
		SendData: slidetypes.SendData{
			ThreadID: longThreadID,
		},
	}
}
