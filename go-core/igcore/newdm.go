package igcore

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"strings"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/mautrix-meta/pkg/messagix/methods"
)

type NewDMResult struct {
	ProtocolVersion    string `json:"protocol_version"`
	RecipientFBID      string `json:"recipient_fbid"`
	ThreadID           string `json:"thread_id,omitempty"`
	ThreadTitle        string `json:"thread_title,omitempty"`
	OfflineThreadingID string `json:"offline_threading_id"`
	MessageID          string `json:"message_id,omitempty"`
	ID                 string `json:"id,omitempty"`
	TimestampMS        int64  `json:"timestamp_ms,omitempty"`
	Text               string `json:"text"`
}

// SendNewDM starts a DM by sending text with no thread ID and the recipient
// FBID in recipient_igids. The thread ID is resolved from the mailbox after the
// send when possible.
func (c *Client) SendNewDM(ctx context.Context, recipientFBID, text, otid string) (*NewDMResult, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}

	request, err := buildNewDMRequest(recipientFBID, text, otid)
	if err != nil {
		return nil, err
	}
	if _, err := c.bootstrap(ctx); err != nil {
		return nil, err
	}

	response, err := c.inner.SendMessage(ctx, request)
	if err != nil {
		return nil, err
	}
	if response == nil {
		return nil, errors.New("Instagram returned an empty new-DM response")
	}

	result := newNewDMResult(request, recipientFBID, response.Message)
	if mailbox, err := c.LoadMailbox(ctx); err == nil {
		recipientID, _ := strconv.ParseInt(strings.TrimSpace(recipientFBID), 10, 64)
		for _, thread := range mailbox.Threads {
			if thread.ThreadKey == recipientID || threadHasParticipant(thread, recipientID) {
				result.ThreadID = thread.ID
				result.ThreadTitle = thread.Title
				break
			}
		}
	}
	return result, nil
}

func buildNewDMRequest(recipientFBID, text, otid string) (*slidetypes.SendTextRequest, error) {
	recipientFBID = strings.TrimSpace(recipientFBID)
	if recipientFBID == "" {
		return nil, errors.New("recipient FBID is required")
	}
	if _, err := strconv.ParseInt(recipientFBID, 10, 64); err != nil {
		return nil, errors.New("recipient FBID must be numeric")
	}
	if strings.TrimSpace(text) == "" {
		return nil, errors.New("text is required")
	}
	if length := len([]rune(text)); length > MaxTextLength {
		return nil, fmt.Errorf("text is %d characters; the maximum is %d", length, MaxTextLength)
	}
	if otid == "" {
		otid = strconv.FormatInt(methods.GenerateEpochID(), 10)
	}
	attribution := "igd_web_chat_tab:in_thread"
	return &slidetypes.SendTextRequest{
		IGThreadIGID:       nil,
		OfflineThreadingID: otid,
		RecipientIGIDs:     []string{recipientFBID},
		Text:               slidetypes.SensitiveString{Value: text},
		Mentions:           []slidetypes.InputMention{},
		MentionedUserIDs:   []string{},
		Commands:           []slidetypes.InputCommand{},
		SendAttribution:    &attribution,
	}, nil
}

func newNewDMResult(request *slidetypes.SendTextRequest, recipientFBID string, message slidetypes.SentMessage) *NewDMResult {
	var timestampMS int64
	if !message.TimestampMS.IsZero() {
		timestampMS = message.TimestampMS.UnixMilli()
	}
	return &NewDMResult{
		ProtocolVersion:    ProtocolVersion,
		RecipientFBID:      strings.TrimSpace(recipientFBID),
		OfflineThreadingID: request.OfflineThreadingID,
		MessageID:          message.MessageID,
		ID:                 message.ID,
		TimestampMS:        timestampMS,
		Text:               request.Text.Value,
	}
}

func threadHasParticipant(thread ThreadView, fbid int64) bool {
	if fbid == 0 {
		return false
	}
	for _, participant := range thread.Participants {
		if participant.FBID == fbid {
			return true
		}
	}
	return false
}
