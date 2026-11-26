package igcore

import (
	"context"
	"errors"
	"strings"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
)

type PinMessageResult struct {
	ProtocolVersion  string   `json:"protocol_version"`
	ThreadID         string   `json:"thread_id"`
	MessageID        string   `json:"message_id"`
	Pinned           bool     `json:"pinned"`
	PinnedMessageIDs []string `json:"pinned_message_ids"`
}

// PinMessage pins a message in a thread. When replaceOldest is true, Instagram
// drops the oldest pin if the thread has reached the pin limit.
func (c *Client) PinMessage(ctx context.Context, threadID, messageID string, replaceOldest bool) (*PinMessageResult, error) {
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
	if _, err := c.bootstrap(ctx); err != nil {
		return nil, err
	}

	c.rpcMu.Lock()
	response, err := c.inner.PinMessage(ctx, buildPinMessageRequest(threadID, messageID, replaceOldest))
	c.rpcMu.Unlock()
	if err != nil {
		return nil, err
	}
	if response == nil {
		return nil, errors.New("Instagram returned an empty pin message response")
	}

	return newPinMessageResult(threadID, messageID, true, response.Data), nil
}

// UnpinMessage removes a message from the thread's pinned messages.
func (c *Client) UnpinMessage(ctx context.Context, threadID, messageID string) (*PinMessageResult, error) {
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
	if _, err := c.bootstrap(ctx); err != nil {
		return nil, err
	}

	c.rpcMu.Lock()
	response, err := c.inner.UnpinMessage(ctx, buildPinMessageRequest(threadID, messageID, false))
	c.rpcMu.Unlock()
	if err != nil {
		return nil, err
	}
	if response == nil {
		return nil, errors.New("Instagram returned an empty unpin message response")
	}

	return newPinMessageResult(threadID, messageID, false, response.Data), nil
}

func buildPinMessageRequest(threadID, messageID string, replaceOldest bool) *slidetypes.PinMessageRequest {
	req := &slidetypes.PinMessageRequest{
		ThreadID:  threadID,
		MessageID: messageID,
	}
	if replaceOldest {
		v := true
		req.ShouldReplaceOldestPin = &v
	}
	return req
}

func newPinMessageResult(threadID, messageID string, pinned bool, data slidetypes.PinMessageResponseData) *PinMessageResult {
	ids := make([]string, 0, len(data.PinnedMessages))
	for _, item := range data.PinnedMessages {
		if id := firstNonEmpty(item.MessageID, item.ID); id != "" {
			ids = append(ids, id)
		}
	}
	return &PinMessageResult{
		ProtocolVersion:  ProtocolVersion,
		ThreadID:         threadID,
		MessageID:        messageID,
		Pinned:           pinned,
		PinnedMessageIDs: ids,
	}
}
