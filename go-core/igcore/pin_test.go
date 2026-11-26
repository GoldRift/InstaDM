package igcore

import (
	"context"
	"strings"
	"testing"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow"
	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/util/jsontime"
)

func TestBuildPinMessageRequest(t *testing.T) {
	req := buildPinMessageRequest("thread-1", "mid.1", false)
	if req.ThreadID != "thread-1" {
		t.Errorf("thread_fbid = %q, want thread-1", req.ThreadID)
	}
	if req.MessageID != "mid.1" {
		t.Errorf("message_id = %q, want mid.1", req.MessageID)
	}
	if req.ShouldReplaceOldestPin != nil {
		t.Errorf("should_replace_oldest_pin = %v, want nil", *req.ShouldReplaceOldestPin)
	}

	reqReplace := buildPinMessageRequest("thread-1", "mid.2", true)
	if reqReplace.ShouldReplaceOldestPin == nil || !*reqReplace.ShouldReplaceOldestPin {
		t.Errorf("should_replace_oldest_pin = %v, want true", reqReplace.ShouldReplaceOldestPin)
	}
}

func TestNewPinMessageResult(t *testing.T) {
	res := newPinMessageResult("thread-1", "mid.2", true, slidetypes.PinMessageResponseData{
		ID: "thread-1",
		PinnedMessages: []slidetypes.PinnedMessage{
			{ID: "node-1", MessageID: "mid.1"},
			{ID: "mid.2", MessageID: ""},
		},
	})
	if res.ProtocolVersion != ProtocolVersion {
		t.Errorf("protocol_version = %q", res.ProtocolVersion)
	}
	if res.ThreadID != "thread-1" || res.MessageID != "mid.2" || !res.Pinned {
		t.Errorf("result = %+v", res)
	}
	if len(res.PinnedMessageIDs) != 2 || res.PinnedMessageIDs[0] != "mid.1" || res.PinnedMessageIDs[1] != "mid.2" {
		t.Errorf("pinned_message_ids = %v, want [mid.1 mid.2]", res.PinnedMessageIDs)
	}
}

func TestPinMessageValidation(t *testing.T) {
	var nilClient *Client
	if _, err := nilClient.PinMessage(context.Background(), "t", "m", false); err == nil || !strings.Contains(err.Error(), "client is not initialized") {
		t.Errorf("nil client pin error = %v", err)
	}
	if _, err := nilClient.UnpinMessage(context.Background(), "t", "m"); err == nil || !strings.Contains(err.Error(), "client is not initialized") {
		t.Errorf("nil client unpin error = %v", err)
	}

	client := &Client{inner: &instameow.Client{}}
	if _, err := client.PinMessage(context.Background(), "", "m", false); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("empty thread pin error = %v", err)
	}
	if _, err := client.PinMessage(context.Background(), "t", "", false); err == nil || !strings.Contains(err.Error(), "message ID is required") {
		t.Errorf("empty message pin error = %v", err)
	}
	if _, err := client.UnpinMessage(context.Background(), "", "m"); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("empty thread unpin error = %v", err)
	}
	if _, err := client.UnpinMessage(context.Background(), "t", ""); err == nil || !strings.Contains(err.Error(), "message ID is required") {
		t.Errorf("empty message unpin error = %v", err)
	}
}

func TestThreadDetailPinnedMessagesFromFullObjects(t *testing.T) {
	thread := &slidetypes.ThreadInfo{
		ID:         "thread-1",
		ThreadFBID: "thread-1",
		Users: []*slidetypes.User{
			{InteropMessagingUserFBID: 222, FullName: "Maya Chen"},
		},
		SlideMessages: &slidetypes.SlideMessages{
			Edges: []slidetypes.Node[*slidetypes.Message]{
				{
					Node: &slidetypes.Message{
						MessageID:   "mid.recent",
						SenderFBID:  222,
						TimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_002_000)},
						IsPinned:    true,
						Content: slidetypes.MessageContentWrapper{
							TypeName: "SlideMessageText",
							Content:  &slidetypes.MessageContentText{TextBody: "recent pin"},
						},
					},
				},
			},
		},
		PinnedMessages: []any{
			map[string]any{
				"message_id":   "mid.older",
				"sender_fbid":  "111",
				"timestamp_ms": "1700000001000",
				"text_body":    "older pin outside page",
			},
			map[string]any{
				"message_id": "mid.recent",
			},
		},
	}

	detail := newThreadDetailView(thread, 111, 20)
	if len(detail.PinnedMessages) != 2 {
		t.Fatalf("pinned_messages len = %d, want 2: %+v", len(detail.PinnedMessages), detail.PinnedMessages)
	}

	first := detail.PinnedMessages[0]
	if first.ID != "mid.older" || first.Text != "older pin outside page" || !first.IsFromViewer || first.TimestampMS != 1_700_000_001_000 {
		t.Errorf("first pin = %+v", first)
	}

	second := detail.PinnedMessages[1]
	if second.ID != "mid.recent" || second.Text != "recent pin" || second.SenderName != "Maya Chen" || second.IsFromViewer {
		t.Errorf("second pin = %+v", second)
	}

	if len(detail.Messages) != 1 || !detail.Messages[0].IsPinned {
		t.Errorf("messages[0].IsPinned = %v, want true", detail.Messages)
	}
}

func TestThreadDetailPinnedMessagesFromMessageFlagAndIDStubs(t *testing.T) {
	thread := &slidetypes.ThreadInfo{
		ID:         "thread-1",
		ThreadFBID: "thread-1",
		Users: []*slidetypes.User{
			{InteropMessagingUserFBID: 222, FullName: "Maya Chen"},
		},
		SlideMessages: &slidetypes.SlideMessages{
			Edges: []slidetypes.Node[*slidetypes.Message]{
				{
					Node: &slidetypes.Message{
						MessageID:   "mid.flag",
						SenderFBID:  222,
						TimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_002_000)},
						IsPinned:    true,
						Content: slidetypes.MessageContentWrapper{
							TypeName: "SlideMessageText",
							Content:  &slidetypes.MessageContentText{TextBody: "flagged pin"},
						},
					},
				},
				{
					Node: &slidetypes.Message{
						MessageID:   "mid.stub",
						SenderFBID:  111,
						TimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_001_000)},
						IsPinned:    false,
						Content: slidetypes.MessageContentWrapper{
							TypeName: "SlideMessageText",
							Content:  &slidetypes.MessageContentText{TextBody: "stub pin"},
						},
					},
				},
			},
		},
		PinnedMessages: []any{
			map[string]any{"id": "node-stub", "message_id": "mid.stub"},
		},
	}

	detail := newThreadDetailView(thread, 111, 20)
	if len(detail.PinnedMessages) != 2 {
		t.Fatalf("pinned_messages len = %d, want 2: %+v", len(detail.PinnedMessages), detail.PinnedMessages)
	}
	if detail.PinnedMessages[0].ID != "mid.stub" || detail.PinnedMessages[0].Text != "stub pin" {
		t.Errorf("first pin = %+v", detail.PinnedMessages[0])
	}
	if detail.PinnedMessages[1].ID != "mid.flag" || detail.PinnedMessages[1].Text != "flagged pin" {
		t.Errorf("second pin = %+v", detail.PinnedMessages[1])
	}
	if !detail.Messages[0].IsPinned || !detail.Messages[1].IsPinned {
		t.Errorf("expected both messages marked IsPinned: %+v", detail.Messages)
	}
}

func TestHandleDeltaPinMessageEvent(t *testing.T) {
	client, events := newEventCapture()
	client.handleEvent(&slidetypes.Delta{
		TypeName:   "SlideUQPPPinUnpinMessage",
		ThreadIGID: "thread-1",
		UQSeqID:    99,
		Data: &slidetypes.PinMessageEvent{
			PinnedMessages:   []string{"mid.1", "mid.2"},
			UnpinnedMessages: []string{"mid.0"},
		},
	})

	if len(*events) != 1 {
		t.Fatalf("emitted %d events, want 1", len(*events))
	}
	ev := (*events)[0]
	if ev.Type != "message.pin" || ev.ThreadID != "thread-1" || ev.SeqID != 99 {
		t.Errorf("event = %+v", ev)
	}
	if len(ev.PinnedMessageIDs) != 2 || ev.PinnedMessageIDs[0] != "mid.1" || ev.PinnedMessageIDs[1] != "mid.2" {
		t.Errorf("pinned_message_ids = %v", ev.PinnedMessageIDs)
	}
	if len(ev.UnpinnedMessageIDs) != 1 || ev.UnpinnedMessageIDs[0] != "mid.0" {
		t.Errorf("unpinned_message_ids = %v", ev.UnpinnedMessageIDs)
	}
}
