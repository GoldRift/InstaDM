package igcore

import (
	"context"
	"errors"
	"strings"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
)

type ReactionResult struct {
	ProtocolVersion string `json:"protocol_version"`
	ThreadID        string `json:"thread_id"`
	MessageID       string `json:"message_id"`
	Emoji           string `json:"emoji"`
	Removed         bool   `json:"removed"`
	TimestampMS     int64  `json:"timestamp_ms,omitempty"`
}

// SendReaction adds or removes a reaction on a message. threadID is the short
// IG chat ID. Instagram allows one reaction per sender per message, so adding a
// different emoji replaces the previous one.
func (c *Client) SendReaction(ctx context.Context, threadID, messageID, emoji string, remove bool) (*ReactionResult, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}

	threadID = strings.TrimSpace(threadID)
	messageID = strings.TrimSpace(messageID)
	emoji = strings.TrimSpace(emoji)
	if threadID == "" {
		return nil, errors.New("thread ID is required")
	}
	if messageID == "" {
		return nil, errors.New("message ID is required")
	}
	if emoji == "" {
		return nil, errors.New("emoji is required")
	}

	ownFBID, err := c.bootstrap(ctx)
	if err != nil {
		return nil, err
	}

	response, err := c.inner.SendReaction(ctx, buildReactionRequest(threadID, messageID, emoji, remove))
	if err != nil {
		return nil, err
	}

	return newReactionResult(threadID, messageID, emoji, remove, ownFBID, response), nil
}

func buildReactionRequest(threadID, messageID, emoji string, remove bool) *slidetypes.CreateReactionRequest {
	status := slidetypes.ReactionStatusCreated
	if remove {
		status = slidetypes.ReactionStatusDeleted
	}
	return &slidetypes.CreateReactionRequest{Input: slidetypes.ReactionInput{
		Emoji:          emoji,
		ItemID:         "",
		MessageID:      messageID,
		ReactionStatus: status,
		ThreadID:       threadID,
	}}
}

func newReactionResult(threadID, messageID, emoji string, remove bool, ownFBID int64, response *slidetypes.SendReactionResponse) *ReactionResult {
	result := &ReactionResult{
		ProtocolVersion: ProtocolVersion,
		ThreadID:        threadID,
		MessageID:       messageID,
		Emoji:           emoji,
		Removed:         remove,
	}
	if response == nil {
		return result
	}

	for _, reaction := range response.Message.Reactions {
		if reaction.SenderFBID != ownFBID || reaction.Reaction != emoji {
			continue
		}
		if !reaction.ReactionTimestampMS.IsZero() {
			result.TimestampMS = reaction.ReactionTimestampMS.UnixMilli()
		}
		break
	}
	return result
}
