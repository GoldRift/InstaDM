package igcore

import (
	"context"
	"errors"
	"strconv"
	"strings"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/mautrix-meta/pkg/messagix/methods"
)

type MuteResult struct {
	ProtocolVersion    string `json:"protocol_version"`
	ThreadID           string `json:"thread_id"`
	MuteSeconds        int    `json:"mute_seconds"`
	IsMuted            bool   `json:"is_muted"`
	MuteUntilMS        int64  `json:"mute_until_ms,omitempty"`
	ResponseThreadID   string `json:"response_thread_id,omitempty"`
	OfflineThreadingID string `json:"offline_threading_id"`
}

type PinResult struct {
	ProtocolVersion  string `json:"protocol_version"`
	ThreadID         string `json:"thread_id"`
	Pinned           bool   `json:"pinned"`
	ResponseThreadID string `json:"response_thread_id,omitempty"`
}

// SetMuted mutes a thread for muteSeconds seconds. -1 means forever and 0
// unmutes.
func (c *Client) SetMuted(ctx context.Context, threadID string, muteSeconds int) (*MuteResult, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}
	threadID = strings.TrimSpace(threadID)
	if threadID == "" {
		return nil, errors.New("thread ID is required")
	}
	if muteSeconds < -1 {
		return nil, errors.New("mute seconds must be -1, 0, or a positive number")
	}
	otid := strconv.FormatInt(methods.GenerateEpochID(), 10)
	if _, err := c.bootstrap(ctx); err != nil {
		return nil, err
	}

	response, err := c.inner.MuteThread(ctx, buildMuteThreadRequest(threadID, muteSeconds, otid))
	if err != nil {
		return nil, err
	}
	if response == nil {
		return nil, errors.New("Instagram returned an empty mute response")
	}

	return newMuteResult(threadID, muteSeconds, otid, response), nil
}

func newMuteResult(threadID string, muteSeconds int, otid string, response *slidetypes.MuteThreadResponse) *MuteResult {
	result := &MuteResult{
		ProtocolVersion:    ProtocolVersion,
		ThreadID:           threadID,
		MuteSeconds:        muteSeconds,
		IsMuted:            response.Data.IsMuted,
		ResponseThreadID:   response.Data.ThreadID,
		OfflineThreadingID: otid,
	}
	if response.Data.MuteUntil != nil {
		result.MuteUntilMS = *response.Data.MuteUntil
		if result.MuteUntilMS != 0 {
			result.IsMuted = true
		}
	}
	return result
}

// SetPinned pins or unpins a thread.
func (c *Client) SetPinned(ctx context.Context, threadID string, pin bool) (*PinResult, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}
	threadID = strings.TrimSpace(threadID)
	if threadID == "" {
		return nil, errors.New("thread ID is required")
	}
	if _, err := c.bootstrap(ctx); err != nil {
		return nil, err
	}

	response, err := c.inner.PinThread(ctx, buildPinThreadRequest(threadID, pin))
	if err != nil {
		return nil, err
	}
	if response == nil {
		return nil, errors.New("Instagram returned an empty pin response")
	}

	return &PinResult{
		ProtocolVersion:  ProtocolVersion,
		ThreadID:         threadID,
		Pinned:           response.Data.IsPin,
		ResponseThreadID: response.Data.ThreadID,
	}, nil
}

func buildMuteThreadRequest(threadID string, muteSeconds int, otid string) *slidetypes.MuteThreadRequest {
	return &slidetypes.MuteThreadRequest{
		ThreadID:           threadID,
		MuteSeconds:        muteSeconds,
		OfflineThreadingID: otid,
	}
}

func buildPinThreadRequest(threadID string, pin bool) *slidetypes.PinThreadRequest {
	return &slidetypes.PinThreadRequest{
		ThreadID: threadID,
		Pin:      pin,
	}
}
