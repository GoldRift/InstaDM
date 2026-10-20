package igcore

import (
	"testing"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/util/jsontime"
)

func TestBuildReactionRequest(t *testing.T) {
	added := buildReactionRequest("thread-id", "message-id", "❤", false)
	if added.Input.Emoji != "❤" || added.Input.ThreadID != "thread-id" || added.Input.MessageID != "message-id" {
		t.Errorf("reaction input = %+v", added.Input)
	}
	if added.Input.ItemID != "" {
		t.Errorf("item_id = %q, want empty", added.Input.ItemID)
	}
	if added.Input.ReactionStatus != slidetypes.ReactionStatusCreated {
		t.Errorf("status = %q, want created", added.Input.ReactionStatus)
	}

	removed := buildReactionRequest("thread-id", "message-id", "❤", true)
	if removed.Input.ReactionStatus != slidetypes.ReactionStatusDeleted {
		t.Errorf("status = %q, want deleted", removed.Input.ReactionStatus)
	}
}

func TestNewReactionResult(t *testing.T) {
	response := &slidetypes.SendReactionResponse{
		Message: slidetypes.ReactionUpdateMessage{
			ID: "message-id",
			Reactions: []slidetypes.Reaction{
				{
					Reaction:            "❤",
					SenderFBID:          222,
					ReactionTimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_001_000)},
				},
				{
					Reaction:            "❤",
					SenderFBID:          111,
					ReactionTimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)},
				},
			},
		},
	}

	result := newReactionResult("thread-id", "message-id", "❤", false, 111, response)
	if result.ProtocolVersion != ProtocolVersion || result.ThreadID != "thread-id" || result.MessageID != "message-id" || result.Emoji != "❤" {
		t.Errorf("result = %+v", result)
	}
	if result.Removed {
		t.Error("removed = true, want false")
	}
	if result.TimestampMS != 1_700_000_000_000 {
		t.Errorf("timestamp = %d, want 1700000000000", result.TimestampMS)
	}

	empty := newReactionResult("thread-id", "message-id", "❤", false, 111, nil)
	if empty.TimestampMS != 0 {
		t.Errorf("nil response timestamp = %d, want 0", empty.TimestampMS)
	}
}
