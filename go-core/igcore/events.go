package igcore

import (
	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
)

// Event is the small, normalized subset of protocol events exposed to callers.
// Message text is included; callers must treat it as sensitive.
type Event struct {
	Type            string       `json:"type"`
	ThreadID        string       `json:"thread_id,omitempty"`
	SeqID           int64        `json:"seq_id,omitempty"`
	FailureCount    int          `json:"failure_count,omitempty"`
	Error           string       `json:"error,omitempty"`
	RawType         string       `json:"raw_type,omitempty"`
	Reaction        string       `json:"reaction,omitempty"`
	ParticipantFBID int64        `json:"participant_fbid,omitempty"`
	FromViewer      bool         `json:"from_viewer,omitempty"`
	TimestampMS     int64        `json:"timestamp_ms,omitempty"`
	Message         *MessageView `json:"message,omitempty"`
}

func (c *Client) handleEvent(event slidetypes.ClientEvent) {
	if c.onEvent == nil {
		return
	}

	switch event := event.(type) {
	case *slidetypes.ReconnectionStateUpdate:
		// DumpState contains bootstrap tokens. Never expose it.
		return
	case *slidetypes.Connected:
		c.onEvent(Event{
			Type:  "connected",
			SeqID: event.SubscribedSeqID,
		})
	case *slidetypes.Disconnected:
		c.onEvent(Event{
			Type:         "disconnected",
			FailureCount: event.FailureCount,
			Error:        errorString(event.Error),
		})
	case *slidetypes.ResnapshotRequired:
		c.onEvent(Event{Type: "resnapshot"})
	case *slidetypes.SeqIDUpdate:
		c.onEvent(Event{
			Type:  "seq_id",
			SeqID: event.SeqID,
		})
	case *slidetypes.AuthError:
		c.onEvent(Event{
			Type:  "auth.error",
			Error: errorString(event.Error),
		})
	case *slidetypes.Delta:
		c.handleDelta(event)
	}
}

func (c *Client) handleDelta(delta *slidetypes.Delta) {
	event := Event{
		Type:     "delta",
		ThreadID: delta.ThreadIGID,
		SeqID:    delta.UQSeqID,
		RawType:  delta.TypeName,
	}

	switch data := delta.Data.(type) {
	case *slidetypes.NewMessageEvent:
		event.Type = "message.new"
		c.attachMessage(&event, data.Message)
	case *slidetypes.AdminMessageEvent:
		event.Type = "message.admin"
		c.attachMessage(&event, data.Message)
	case *slidetypes.EditMessageEvent:
		event.Type = "message.edit"
		event.Message = &MessageView{
			ID:   data.MessageID,
			Text: firstNonEmpty(data.TextBody, data.IGDSnippet),
		}
		if !data.SlideEditHistoryEntry.TimestampMS.IsZero() {
			event.TimestampMS = data.SlideEditHistoryEntry.TimestampMS.UnixMilli()
		}
	case *slidetypes.DeleteMessageEvent:
		event.Type = "message.delete"
		event.Message = &MessageView{ID: data.MessageID}
	case *slidetypes.CreateReactionEvent:
		event.Type = "reaction.add"
		event.Message = &MessageView{ID: data.MessageID}
		event.Reaction = data.Reaction.Reaction
	case *slidetypes.DeleteReactionEvent:
		event.Type = "reaction.remove"
		event.Message = &MessageView{ID: data.MessageID}
		event.Reaction = data.Reaction.Reaction
	case *slidetypes.MarkReadEvent:
		event.Type = "mark.read"
		if !data.ReadTimestampMS.IsZero() {
			event.TimestampMS = data.ReadTimestampMS.UnixMilli()
		}
	case *slidetypes.MarkUnreadEvent:
		event.Type = "mark.unread"
	case *slidetypes.ReadReceiptEvent:
		event.Type = "read.receipt"
		event.ParticipantFBID = data.ReadReceipt.ParticipantFBID
		c.mu.Lock()
		ownFBID := c.ownFBID
		c.mu.Unlock()
		event.FromViewer = ownFBID != 0 && event.ParticipantFBID == ownFBID
		if !data.ReadReceipt.WatermarkTimestampMS.IsZero() {
			event.TimestampMS = data.ReadReceipt.WatermarkTimestampMS.UnixMilli()
		}
	case slidetypes.UnknownEvent:
		event.Type = "unknown"
	case slidetypes.IgnoredEvent:
		return
	}

	c.onEvent(event)
}

func (c *Client) attachMessage(event *Event, message *slidetypes.Message) {
	if message == nil {
		return
	}
	event.Message = c.messageView(message)
	if message.ThreadFBID != "" {
		event.ThreadID = message.ThreadFBID
	}
	if !message.TimestampMS.IsZero() {
		event.TimestampMS = message.TimestampMS.UnixMilli()
	}
}

func (c *Client) messageView(message *slidetypes.Message) *MessageView {
	c.mu.Lock()
	ownFBID := c.ownFBID
	c.mu.Unlock()

	view := newMessageView(message, ownFBID, nil)
	return &view
}

func errorString(err error) string {
	if err == nil {
		return ""
	}
	return err.Error()
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}
