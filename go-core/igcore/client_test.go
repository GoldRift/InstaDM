package igcore

import (
	"context"
	"strconv"
	"strings"
	"testing"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow"
	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/util/jsontime"
)

func TestBuildSendTextRequest(t *testing.T) {
	request, err := buildSendTextRequest("thread-id", "hello", "", "")
	if err != nil {
		t.Fatalf("buildSendTextRequest: %v", err)
	}
	if request.IGThreadIGID == nil || *request.IGThreadIGID != "thread-id" {
		t.Errorf("ig_thread_igid = %v, want thread-id", request.IGThreadIGID)
	}
	if request.Text.Value != "hello" {
		t.Errorf("text = %q, want hello", request.Text.Value)
	}
	if request.OfflineThreadingID == "" {
		t.Error("offline_threading_id is empty")
	}
	if _, err := strconv.ParseInt(request.OfflineThreadingID, 10, 64); err != nil {
		t.Errorf("offline_threading_id is not numeric: %q", request.OfflineThreadingID)
	}
	if request.SendAttribution == nil || *request.SendAttribution != "igd_web_chat_tab:in_thread" {
		t.Errorf("send_attribution = %v, want igd_web_chat_tab:in_thread", request.SendAttribution)
	}
	if request.ReplyToMessageID != nil {
		t.Errorf("reply_to_message_id = %v, want nil", request.ReplyToMessageID)
	}
	if request.RecipientIGIDs == nil || len(request.RecipientIGIDs) != 0 {
		t.Errorf("recipient_igids = %#v, want empty slice", request.RecipientIGIDs)
	}
	if request.Mentions == nil || request.MentionedUserIDs == nil || request.Commands == nil {
		t.Error("mention/command slices should be empty, not nil")
	}
}

func TestBuildSendTextRequestReplyAndOTID(t *testing.T) {
	request, err := buildSendTextRequest("thread-id", "hello", "message-id", "123456789")
	if err != nil {
		t.Fatalf("buildSendTextRequest: %v", err)
	}
	if request.ReplyToMessageID == nil || *request.ReplyToMessageID != "message-id" {
		t.Errorf("reply_to_message_id = %v, want message-id", request.ReplyToMessageID)
	}
	if request.OfflineThreadingID != "123456789" {
		t.Errorf("offline_threading_id = %q, want 123456789", request.OfflineThreadingID)
	}
}

func TestBuildSendTextRequestValidation(t *testing.T) {
	if _, err := buildSendTextRequest("", "hello", "", ""); err == nil {
		t.Error("empty thread ID accepted")
	}
	if _, err := buildSendTextRequest("thread-id", "   ", "", ""); err == nil {
		t.Error("blank text accepted")
	}
	if _, err := buildSendTextRequest("thread-id", strings.Repeat("a", MaxTextLength+1), "", ""); err == nil {
		t.Error("overlong text accepted")
	}
	if _, err := buildSendTextRequest("thread-id", strings.Repeat("a", MaxTextLength), "", ""); err != nil {
		t.Errorf("text at limit rejected: %v", err)
	}
}

func TestBuildPaginateMessagesRequest(t *testing.T) {
	request := buildPaginateMessagesRequest("thread-id", "cursor-1", 5)

	if request.AfterCursor == nil || *request.AfterCursor != "cursor-1" {
		t.Errorf("after cursor = %v", request.AfterCursor)
	}
	if request.ThreadID != "thread-id" {
		t.Errorf("thread ID = %q", request.ThreadID)
	}
	if request.FirstN != 5 || request.InitialMessagePageCount != 5 {
		t.Errorf("limits = %d / %d, want 5 / 5", request.FirstN, request.InitialMessagePageCount)
	}
	if request.BeforeCursor != nil || request.LastN != nil || request.NewerThanMessageID != nil || request.OlderThanMessageID != nil {
		t.Errorf("unexpected cursor fields: %+v", request)
	}
}

func TestLoadOlderMessagesValidation(t *testing.T) {
	client := &Client{inner: &instameow.Client{}}

	if _, err := client.LoadOlderMessages(context.Background(), "", "cursor", 20); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("empty thread ID error = %v", err)
	}
	if _, err := client.LoadOlderMessages(context.Background(), "thread-id", "", 20); err == nil || !strings.Contains(err.Error(), "after cursor is required") {
		t.Errorf("empty cursor error = %v", err)
	}
}

func TestNewOTID(t *testing.T) {
	if _, err := strconv.ParseInt(NewOTID(), 10, 64); err != nil {
		t.Errorf("NewOTID is not numeric: %v", err)
	}
}

func TestNewSendResult(t *testing.T) {
	message := slidetypes.SentMessage{
		MessageID:   "mid.$1",
		ID:          "message-id",
		TimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)},
	}
	result := newSendResult("thread-id", "otid", "hello", message)

	if result.ProtocolVersion != ProtocolVersion {
		t.Errorf("protocol version = %q, want %q", result.ProtocolVersion, ProtocolVersion)
	}
	if result.ThreadID != "thread-id" || result.OfflineThreadingID != "otid" || result.Text != "hello" {
		t.Errorf("result = %+v", result)
	}
	if result.MessageID != "mid.$1" || result.ID != "message-id" {
		t.Errorf("message IDs = %q / %q", result.MessageID, result.ID)
	}
	if result.TimestampMS != 1_700_000_000_000 {
		t.Errorf("timestamp = %d, want 1700000000000", result.TimestampMS)
	}
}
