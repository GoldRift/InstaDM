package igcore

import (
	"context"
	"testing"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow"
	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/util/jsontime"
)

func typingNotification(sender int64, status int) *slidetypes.TypingNotification {
	return &slidetypes.TypingNotification{
		ThreadID:       "thread-1",
		Timestamp:      jsontime.UnixMicro{Time: time.UnixMilli(1_700_000_000_000)},
		SenderID:       sender,
		ActivityStatus: status,
	}
}

func TestHandleEventTyping(t *testing.T) {
	client, events := newEventCapture()
	client.handleEvent(typingNotification(222, 1))

	if len(*events) != 1 {
		t.Fatalf("emitted %d events, want 1", len(*events))
	}
	event := (*events)[0]
	if event.Type != "typing" || event.LongThreadID != "thread-1" {
		t.Errorf("event = %+v", event)
	}
	if event.ThreadID != "" {
		t.Errorf("typing thread ID = %q, want the long ID only", event.ThreadID)
	}
	if event.ParticipantIGID != 222 || event.ActivityStatus != 1 {
		t.Errorf("typing sender/status = %d/%d", event.ParticipantIGID, event.ActivityStatus)
	}
	if event.TimestampMS != 1_700_000_000_000 {
		t.Errorf("timestamp = %d", event.TimestampMS)
	}
}

func TestHandleEventTypingStopped(t *testing.T) {
	client, events := newEventCapture()
	client.handleEvent(typingNotification(222, 0))

	event := (*events)[0]
	if event.ActivityStatus != 0 {
		t.Errorf("stop signal = %+v", event)
	}
}

func TestSetTypingValidatesInput(t *testing.T) {
	client := &Client{inner: &instameow.Client{}}
	if err := client.SetTyping(context.Background(), "  ", true); err == nil {
		t.Error("SetTyping accepted a blank thread ID")
	}
	if err := client.SetTypingOnce(context.Background(), "", false); err == nil {
		t.Error("SetTypingOnce accepted a blank thread ID")
	}

	var uninitialized *Client
	if err := uninitialized.SetTyping(context.Background(), "thread-1", true); err == nil {
		t.Error("SetTyping ran on an uninitialized client")
	}
}
