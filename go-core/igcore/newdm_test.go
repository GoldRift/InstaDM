package igcore

import (
	"strings"
	"testing"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/util/jsontime"
)

func TestBuildNewDMRequest(t *testing.T) {
	request, err := buildNewDMRequest("18084008816129069", "hello", "otid-1")
	if err != nil {
		t.Fatalf("buildNewDMRequest: %v", err)
	}
	if request.IGThreadIGID != nil {
		t.Errorf("ig_thread_igid = %v, want nil", request.IGThreadIGID)
	}
	if len(request.RecipientIGIDs) != 1 || request.RecipientIGIDs[0] != "18084008816129069" {
		t.Errorf("recipient_igids = %v", request.RecipientIGIDs)
	}
	if request.Text.Value != "hello" || request.OfflineThreadingID != "otid-1" {
		t.Errorf("request = %+v", request)
	}
	if request.SendAttribution == nil || *request.SendAttribution == "" {
		t.Errorf("send_attribution = %v", request.SendAttribution)
	}

	generated, err := buildNewDMRequest("123", "hello", "")
	if err != nil {
		t.Fatalf("buildNewDMRequest generated otid: %v", err)
	}
	if generated.OfflineThreadingID == "" {
		t.Error("generated otid is empty")
	}
}

func TestBuildNewDMRequestValidation(t *testing.T) {
	for _, test := range []struct {
		name      string
		recipient string
		text      string
		want      string
	}{
		{name: "empty recipient", recipient: "", text: "hello", want: "recipient FBID is required"},
		{name: "non-numeric recipient", recipient: "abc", text: "hello", want: "must be numeric"},
		{name: "empty text", recipient: "123", text: " ", want: "text is required"},
		{name: "overlong text", recipient: "123", text: strings.Repeat("a", MaxTextLength+1), want: "maximum"},
	} {
		t.Run(test.name, func(t *testing.T) {
			if _, err := buildNewDMRequest(test.recipient, test.text, "otid-1"); err == nil || !strings.Contains(err.Error(), test.want) {
				t.Errorf("error = %v, want %q", err, test.want)
			}
		})
	}
}

func TestNewNewDMResult(t *testing.T) {
	request, err := buildNewDMRequest("123", "hello", "otid-1")
	if err != nil {
		t.Fatalf("buildNewDMRequest: %v", err)
	}
	result := newNewDMResult(request, "123", slidetypes.SentMessage{
		MessageID:   "message-id",
		ID:          "internal-id",
		TimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)},
	})
	if result.ProtocolVersion != ProtocolVersion || result.RecipientFBID != "123" || result.Text != "hello" {
		t.Errorf("result = %+v", result)
	}
	if result.MessageID != "message-id" || result.ID != "internal-id" || result.TimestampMS != 1_700_000_000_000 {
		t.Errorf("result fields = %+v", result)
	}
}

func TestThreadHasParticipant(t *testing.T) {
	thread := ThreadView{Participants: []ParticipantView{{FBID: 111}, {FBID: 222}}}
	if !threadHasParticipant(thread, 111) || !threadHasParticipant(thread, 222) {
		t.Error("known participant not found")
	}
	if threadHasParticipant(thread, 333) || threadHasParticipant(thread, 0) {
		t.Error("unknown participant matched")
	}
}
