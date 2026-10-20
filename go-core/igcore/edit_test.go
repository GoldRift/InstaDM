package igcore

import (
	"strings"
	"testing"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/util/jsontime"
)

func TestBuildEditMessageRequest(t *testing.T) {
	request, err := buildEditMessageRequest("thread-id", "message-id", "new text", "otid-1")
	if err != nil {
		t.Fatalf("buildEditMessageRequest: %v", err)
	}
	if request.ThreadID != "thread-id" || request.TargetMessageID != "message-id" {
		t.Errorf("request IDs = %+v", request)
	}
	if request.TargetItemID != "" {
		t.Errorf("target_item_id = %q, want empty", request.TargetItemID)
	}
	if request.Body.Value != "new text" {
		t.Errorf("body = %q", request.Body.Value)
	}
	if request.OfflineThreadingID != "otid-1" {
		t.Errorf("otid = %q", request.OfflineThreadingID)
	}

	generated, err := buildEditMessageRequest("thread-id", "message-id", "new text", "")
	if err != nil {
		t.Fatalf("buildEditMessageRequest generated otid: %v", err)
	}
	if generated.OfflineThreadingID == "" {
		t.Error("generated otid is empty")
	}
}

func TestBuildEditMessageRequestValidation(t *testing.T) {
	for _, test := range []struct {
		name      string
		threadID  string
		messageID string
		text      string
		want      string
	}{
		{name: "empty thread", threadID: "", messageID: "message-id", text: "text", want: "thread ID is required"},
		{name: "empty message", threadID: "thread-id", messageID: "", text: "text", want: "message ID is required"},
		{name: "empty text", threadID: "thread-id", messageID: "message-id", text: "   ", want: "text is required"},
		{name: "overlong text", threadID: "thread-id", messageID: "message-id", text: strings.Repeat("a", MaxTextLength+1), want: "maximum"},
	} {
		t.Run(test.name, func(t *testing.T) {
			if _, err := buildEditMessageRequest(test.threadID, test.messageID, test.text, "otid-1"); err == nil || !strings.Contains(err.Error(), test.want) {
				t.Errorf("error = %v, want %q", err, test.want)
			}
		})
	}
}

func TestNewEditResult(t *testing.T) {
	request, err := buildEditMessageRequest("thread-id", "message-id", "new text", "otid-1")
	if err != nil {
		t.Fatalf("buildEditMessageRequest: %v", err)
	}
	result := newEditResult(request, slidetypes.SentMessage{
		MessageID:   "message-id",
		ID:          "internal-id",
		TimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)},
	})
	if result.ProtocolVersion != ProtocolVersion || result.ThreadID != "thread-id" || result.MessageID != "message-id" {
		t.Errorf("result = %+v", result)
	}
	if result.OfflineThreadingID != "otid-1" || result.Text != "new text" || result.TimestampMS != 1_700_000_000_000 {
		t.Errorf("result fields = %+v", result)
	}
}
