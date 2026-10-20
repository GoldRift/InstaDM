package igcore

import (
	"testing"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/util/jsontime"
)

func newEventCapture() (*Client, *[]Event) {
	var events []Event
	client := &Client{
		ownFBID: 111,
		onEvent: func(event Event) {
			events = append(events, event)
		},
	}
	return client, &events
}

func textMessage(id string, sender int64, body string) *slidetypes.Message {
	return &slidetypes.Message{
		MessageID:   id,
		SenderFBID:  sender,
		ThreadFBID:  "thread-1",
		TimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)},
		Content: slidetypes.MessageContentWrapper{
			TypeName: "SlideMessageText",
			Content:  &slidetypes.MessageContentText{TextBody: body},
		},
	}
}

func TestHandleEventIgnoresReconnectionState(t *testing.T) {
	client, events := newEventCapture()
	client.handleEvent(&slidetypes.ReconnectionStateUpdate{State: []byte(`{"tokens":"secret"}`)})
	if len(*events) != 0 {
		t.Errorf("emitted %d events, want 0", len(*events))
	}
}

func TestHandleEventConnectedAndDisconnected(t *testing.T) {
	client, events := newEventCapture()
	client.handleEvent(&slidetypes.Connected{SubscribedSeqID: 42, LatestSeqID: 50})
	client.handleEvent(&slidetypes.Disconnected{Error: errTest, FailureCount: 3})

	if len(*events) != 2 {
		t.Fatalf("emitted %d events, want 2", len(*events))
	}
	if (*events)[0].Type != "connected" || (*events)[0].SeqID != 42 {
		t.Errorf("connected event = %+v", (*events)[0])
	}
	if (*events)[1].Type != "disconnected" || (*events)[1].FailureCount != 3 || (*events)[1].Error != "test error" {
		t.Errorf("disconnected event = %+v", (*events)[1])
	}
}

func TestHandleDeltaNewMessage(t *testing.T) {
	client, events := newEventCapture()
	client.handleEvent(&slidetypes.Delta{
		TypeName:   "SlideUQPPNewMessage",
		ThreadIGID: "thread-1",
		UQSeqID:    42,
		Data: &slidetypes.NewMessageEvent{
			Message: textMessage("message-1", 222, "hello"),
		},
	})

	if len(*events) != 1 {
		t.Fatalf("emitted %d events, want 1", len(*events))
	}
	event := (*events)[0]
	if event.Type != "message.new" || event.ThreadID != "thread-1" || event.SeqID != 42 {
		t.Errorf("event = %+v", event)
	}
	if event.Message == nil {
		t.Fatal("message payload is nil")
	}
	if event.Message.ID != "message-1" || event.Message.Text != "hello" || event.Message.SenderFBID != 222 {
		t.Errorf("message = %+v", event.Message)
	}
	if event.Message.IsFromViewer {
		t.Error("other sender marked as viewer")
	}
	if event.TimestampMS != 1_700_000_000_000 {
		t.Errorf("timestamp = %d", event.TimestampMS)
	}
}

func TestHandleDeltaViewerMessage(t *testing.T) {
	client, events := newEventCapture()
	client.handleEvent(&slidetypes.Delta{
		TypeName:   "SlideUQPPNewMessage",
		ThreadIGID: "thread-1",
		Data: &slidetypes.NewMessageEvent{
			Message: textMessage("message-1", 111, "mine"),
		},
	})

	if len(*events) != 1 || (*events)[0].Message == nil {
		t.Fatalf("events = %+v", *events)
	}
	if !(*events)[0].Message.IsFromViewer {
		t.Error("viewer message not marked as from viewer")
	}
}

func TestHandleDeltaReactionAndDelete(t *testing.T) {
	client, events := newEventCapture()
	client.handleEvent(&slidetypes.Delta{
		TypeName:   "SlideUQPPCreateReaction",
		ThreadIGID: "thread-1",
		Data: &slidetypes.CreateReactionEvent{
			MessageID: "message-1",
			Reaction: slidetypes.Reaction{
				Reaction:   "❤",
				SenderFBID: 222,
			},
		},
	})
	client.handleEvent(&slidetypes.Delta{
		TypeName:   "SlideUQPPDeleteMessage",
		ThreadIGID: "thread-1",
		Data:       &slidetypes.DeleteMessageEvent{MessageID: "message-2"},
	})

	if len(*events) != 2 {
		t.Fatalf("emitted %d events, want 2", len(*events))
	}
	if (*events)[0].Type != "reaction.add" || (*events)[0].Reaction != "❤" || (*events)[0].Message.ID != "message-1" {
		t.Errorf("reaction event = %+v", (*events)[0])
	}
	if (*events)[0].ParticipantFBID != 222 || (*events)[0].FromViewer {
		t.Errorf("reaction sender = %+v", (*events)[0])
	}
	if (*events)[1].Type != "message.delete" || (*events)[1].Message.ID != "message-2" {
		t.Errorf("delete event = %+v", (*events)[1])
	}
}

func TestHandleDeltaViewerReactionRemove(t *testing.T) {
	client, events := newEventCapture()
	client.handleEvent(&slidetypes.Delta{
		TypeName:   "SlideUQPPDeleteReaction",
		ThreadIGID: "thread-1",
		Data: &slidetypes.DeleteReactionEvent{
			MessageID: "message-1",
			Reaction:  slidetypes.Reaction{Reaction: "👍", SenderFBID: 111},
		},
	})

	if len(*events) != 1 {
		t.Fatalf("emitted %d events, want 1", len(*events))
	}
	event := (*events)[0]
	if event.Type != "reaction.remove" || event.Reaction != "👍" || event.Message.ID != "message-1" {
		t.Errorf("reaction event = %+v", event)
	}
	if event.ParticipantFBID != 111 || !event.FromViewer {
		t.Errorf("viewer reaction attribution = %+v", event)
	}
}

func TestHandleDeltaEditAndReadReceipt(t *testing.T) {
	client, events := newEventCapture()
	edit := &slidetypes.EditMessageEvent{MessageID: "message-1", TextBody: "edited"}
	edit.SlideEditHistoryEntry.TimestampMS = jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_001_000)}
	client.handleEvent(&slidetypes.Delta{
		TypeName:   "SlideUQPPEditMessage",
		ThreadIGID: "thread-1",
		Data:       edit,
	})
	client.handleEvent(&slidetypes.Delta{
		TypeName:   "SlideUQPPReadReceipt",
		ThreadIGID: "thread-1",
		Data: &slidetypes.ReadReceiptEvent{
			ReadReceipt: slidetypes.ReadReceipt{
				ParticipantFBID:      222,
				WatermarkTimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_002_000)},
			},
		},
	})

	if len(*events) != 2 {
		t.Fatalf("emitted %d events, want 2", len(*events))
	}
	if (*events)[0].Type != "message.edit" || (*events)[0].Message.Text != "edited" || (*events)[0].TimestampMS != 1_700_000_001_000 {
		t.Errorf("edit event = %+v", (*events)[0])
	}
	if (*events)[1].Type != "read.receipt" || (*events)[1].TimestampMS != 1_700_000_002_000 {
		t.Errorf("read receipt event = %+v", (*events)[1])
	}
	if (*events)[1].ParticipantFBID != 222 {
		t.Errorf("read receipt participant = %d, want 222", (*events)[1].ParticipantFBID)
	}
	if (*events)[1].FromViewer {
		t.Error("other participant's read receipt marked as from the viewer")
	}
}

func TestHandleDeltaReadReceiptFromViewer(t *testing.T) {
	client, events := newEventCapture()
	client.handleEvent(&slidetypes.Delta{
		TypeName:   "SlideUQPPReadReceipt",
		ThreadIGID: "thread-1",
		Data: &slidetypes.ReadReceiptEvent{
			ReadReceipt: slidetypes.ReadReceipt{
				ParticipantFBID:      111,
				WatermarkTimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_002_000)},
			},
		},
	})

	if len(*events) != 1 {
		t.Fatalf("emitted %d events, want 1", len(*events))
	}
	if !(*events)[0].FromViewer {
		t.Error("viewer's own read receipt not marked as from the viewer")
	}
}

func TestHandleDeltaUnknownAndIgnored(t *testing.T) {
	client, events := newEventCapture()
	client.handleEvent(&slidetypes.Delta{
		TypeName:   "SlideUQPPFutureMutation",
		ThreadIGID: "thread-1",
		Data:       slidetypes.UnknownEvent{"future": true},
	})
	client.handleEvent(&slidetypes.Delta{
		TypeName:   "SlideUQPPNoOpMutation",
		ThreadIGID: "thread-1",
		Data:       slidetypes.IgnoredEvent{"ignored": true},
	})

	if len(*events) != 1 {
		t.Fatalf("emitted %d events, want 1", len(*events))
	}
	if (*events)[0].Type != "unknown" || (*events)[0].RawType != "SlideUQPPFutureMutation" {
		t.Errorf("unknown event = %+v", (*events)[0])
	}
}

var errTest = testError("test error")

type testError string

func (e testError) Error() string { return string(e) }
