package igcore

import (
	"context"
	"strings"
	"testing"

	"go.mau.fi/mautrix-meta/pkg/instameow"
	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
)

func TestBuildMuteThreadRequest(t *testing.T) {
	request := buildMuteThreadRequest("thread-id", -1, "otid-1")
	if request.ThreadID != "thread-id" || request.MuteSeconds != -1 || request.OfflineThreadingID != "otid-1" {
		t.Errorf("request = %+v", request)
	}
}

func TestBuildPinThreadRequest(t *testing.T) {
	request := buildPinThreadRequest("thread-id", true)
	if request.ThreadID != "thread-id" || !request.Pin {
		t.Errorf("request = %+v", request)
	}
}

func TestNewMuteResult(t *testing.T) {
	until := int64(-1)
	response := &slidetypes.MuteThreadResponse{}
	response.Data.IsMuted = false
	response.Data.MuteUntil = &until
	response.Data.ThreadID = "long-thread-id"

	result := newMuteResult("thread-id", -1, "otid-1", response)
	if !result.IsMuted {
		t.Error("MuteUntil=-1 should normalize to muted")
	}
	if result.MuteUntilMS != -1 || result.ResponseThreadID != "long-thread-id" {
		t.Errorf("result = %+v", result)
	}

	unmuted := newMuteResult("thread-id", 0, "otid-2", &slidetypes.MuteThreadResponse{})
	if unmuted.IsMuted || unmuted.MuteUntilMS != 0 {
		t.Errorf("unmuted result = %+v", unmuted)
	}
}

func TestSetMutedValidation(t *testing.T) {
	var nilClient *Client
	if _, err := nilClient.SetMuted(context.Background(), "thread-id", -1); err == nil || !strings.Contains(err.Error(), "client is not initialized") {
		t.Errorf("nil client error = %v", err)
	}

	client := &Client{inner: &instameow.Client{}}
	if _, err := client.SetMuted(context.Background(), "", -1); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("empty thread error = %v", err)
	}
	if _, err := client.SetMuted(context.Background(), "thread-id", -2); err == nil || !strings.Contains(err.Error(), "mute seconds") {
		t.Errorf("negative seconds error = %v", err)
	}
}

func TestSetPinnedValidation(t *testing.T) {
	var nilClient *Client
	if _, err := nilClient.SetPinned(context.Background(), "thread-id", true); err == nil || !strings.Contains(err.Error(), "client is not initialized") {
		t.Errorf("nil client error = %v", err)
	}

	client := &Client{inner: &instameow.Client{}}
	if _, err := client.SetPinned(context.Background(), "", true); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("empty thread error = %v", err)
	}
}
