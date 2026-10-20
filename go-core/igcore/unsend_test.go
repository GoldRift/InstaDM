package igcore

import (
	"context"
	"strings"
	"testing"

	"go.mau.fi/mautrix-meta/pkg/instameow"
)

func TestBuildUnsendMessageRequest(t *testing.T) {
	request := buildUnsendMessageRequest("message-id", "long-thread-id")
	if request.MessageID != "message-id" {
		t.Errorf("message ID = %q", request.MessageID)
	}
	if request.SendData.ThreadID != "long-thread-id" {
		t.Errorf("send_data.thread_id = %q", request.SendData.ThreadID)
	}
}

func TestUnsendValidation(t *testing.T) {
	var client *Client
	if _, err := client.UnsendMessage(nil, "thread-id", "message-id"); err == nil || !strings.Contains(err.Error(), "client is not initialized") {
		t.Errorf("nil client error = %v", err)
	}

	client = &Client{inner: &instameow.Client{}}
	if _, err := client.UnsendMessage(context.Background(), "", "message-id"); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("empty thread error = %v", err)
	}
	if _, err := client.UnsendMessage(context.Background(), "thread-id", ""); err == nil || !strings.Contains(err.Error(), "message ID is required") {
		t.Errorf("empty message error = %v", err)
	}
}
