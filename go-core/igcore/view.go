package igcore

import (
	"strings"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/mautrix-meta/pkg/messagix/types"
)

// ProtocolVersion identifies the pinned upstream implementation used to
// produce a response. Record it alongside fixtures so a doc-ID or parser
// change is easy to correlate.
const ProtocolVersion = "mautrix-meta@0e6bf6c054c0675176a93ee168333355fbd6a372"

// MailboxView is the normalized, JSON-friendly form of the inbox returned by
// instameow. It intentionally contains no cookies or bootstrap tokens, but it
// does contain account and contact metadata, so fixtures still need redaction
// before they are shared or committed.
type MailboxView struct {
	ProtocolVersion string       `json:"protocol_version"`
	Viewer          ViewerView   `json:"viewer"`
	SeqID           int64        `json:"seq_id"`
	HasNextPage     bool         `json:"has_next_page"`
	EndCursor       string       `json:"end_cursor,omitempty"`
	Threads         []ThreadView `json:"threads"`
}

type ViewerView struct {
	IGID     string `json:"ig_id"`
	Username string `json:"username"`
	Name     string `json:"name"`
	FBID     int64  `json:"fbid"`
}

type ParticipantView struct {
	IGID     string `json:"ig_id"`
	FBID     int64  `json:"fbid"`
	Username string `json:"username"`
	Name     string `json:"name"`
}

type ThreadView struct {
	ID             string            `json:"id"`
	ThreadFBID     string            `json:"thread_fbid"`
	ThreadID       string            `json:"thread_id"`
	ThreadKey      int64             `json:"thread_key"`
	Subtype        string            `json:"subtype"`
	IsGroup        bool              `json:"is_group"`
	Title          string            `json:"title"`
	Participants   []ParticipantView `json:"participants"`
	LastActivityMS int64             `json:"last_activity_ms"`
	Folder         string            `json:"folder,omitempty"`
	SystemFolder   string            `json:"system_folder,omitempty"`
	MarkedAsUnread bool              `json:"marked_as_unread"`
	IsMuted        bool              `json:"is_muted"`
	IsPinned       bool              `json:"is_pinned"`
}

func newMailboxView(viewer *types.PolarisViewer, mailbox *slidetypes.Mailbox, ownFBID int64) *MailboxView {
	view := &MailboxView{
		ProtocolVersion: ProtocolVersion,
		SeqID:           mailbox.UQSeqID,
		HasNextPage:     mailbox.ThreadsByFolder.PageInfo.HasNextPage,
		EndCursor:       mailbox.ThreadsByFolder.PageInfo.EndCursor,
		Threads:         make([]ThreadView, 0, len(mailbox.ThreadsByFolder.Edges)),
	}

	if viewer != nil {
		view.Viewer = ViewerView{
			IGID:     viewer.ID,
			Username: viewer.Data.Username,
			Name:     viewer.Data.FullName,
			FBID:     viewer.GetFBID(),
		}
	}

	for _, edge := range mailbox.ThreadsByFolder.Edges {
		thread := edge.Node.AsIGDirectThread
		if thread == nil {
			continue
		}
		view.Threads = append(view.Threads, newThreadView(thread, ownFBID))
	}

	return view
}

func newThreadView(thread *slidetypes.ThreadInfo, ownFBID int64) ThreadView {
	var lastActivityMS int64
	if !thread.LastActivityTimestampMS.IsZero() {
		lastActivityMS = thread.LastActivityTimestampMS.UnixMilli()
	}

	view := ThreadView{
		ID:             thread.ID,
		ThreadFBID:     thread.ThreadFBID,
		ThreadID:       thread.ThreadID,
		ThreadKey:      thread.ThreadKey,
		Subtype:        string(thread.ThreadSubtype),
		IsGroup:        thread.IsGroup,
		Title:          thread.ThreadTitle,
		LastActivityMS: lastActivityMS,
		Folder:         thread.Folder,
		SystemFolder:   thread.SystemFolder,
		MarkedAsUnread: thread.MarkedAsUnread,
		IsMuted:        thread.IsMuted != nil && *thread.IsMuted,
		IsPinned:       thread.IsPin != nil && *thread.IsPin,
		Participants:   make([]ParticipantView, 0, len(thread.Users)),
	}

	for _, user := range thread.Users {
		if user == nil {
			continue
		}
		view.Participants = append(view.Participants, ParticipantView{
			IGID:     user.ID,
			FBID:     user.InteropMessagingUserFBID,
			Username: user.Username,
			Name:     user.FullName,
		})
	}

	if view.Title == "" {
		view.Title = displayTitle(view.Participants, ownFBID)
	}

	return view
}

func displayTitle(participants []ParticipantView, ownFBID int64) string {
	names := make([]string, 0, len(participants))
	for _, participant := range participants {
		if ownFBID != 0 && participant.FBID == ownFBID {
			continue
		}
		name := participant.Name
		if name == "" {
			name = participant.Username
		}
		if name != "" {
			names = append(names, name)
		}
	}
	if len(names) == 0 {
		return "(unknown)"
	}
	return strings.Join(names, ", ")
}

// ThreadDetailView is the normalized form of IGDThreadDetailQuery. Messages are
// ordered oldest-to-newest for display, even though Instagram returns the newest
// page first.
type ThreadDetailView struct {
	ProtocolVersion string        `json:"protocol_version"`
	Thread          ThreadView    `json:"thread"`
	Messages        []MessageView `json:"messages"`
	HasMoreOlder    bool          `json:"has_more_older"`
	OldestCursor    string        `json:"oldest_cursor,omitempty"`
}

type MessageView struct {
	ID                 string `json:"id"`
	OfflineThreadingID string `json:"offline_threading_id,omitempty"`
	SenderFBID         int64  `json:"sender_fbid"`
	SenderName         string `json:"sender_name,omitempty"`
	IsFromViewer       bool   `json:"is_from_viewer"`
	TimestampMS        int64  `json:"timestamp_ms"`
	Kind               string `json:"kind"`
	ContentType        string `json:"content_type,omitempty"`
	Text               string `json:"text,omitempty"`
	MediaCount         int    `json:"media_count,omitempty"`
	RepliedToMessageID string `json:"replied_to_message_id,omitempty"`
}

func newThreadDetailView(thread *slidetypes.ThreadInfo, ownFBID int64, limit int) *ThreadDetailView {
	if limit <= 0 {
		limit = 20
	}
	if limit > 100 {
		limit = 100
	}

	view := &ThreadDetailView{
		ProtocolVersion: ProtocolVersion,
		Thread:          newThreadView(thread, ownFBID),
		Messages:        make([]MessageView, 0, limit),
	}
	if thread.SlideMessages == nil {
		return view
	}

	type candidate struct {
		message *slidetypes.Message
		cursor  string
	}

	participants := participantNames(thread)
	candidates := make([]candidate, 0, len(thread.SlideMessages.Edges))
	for _, edge := range thread.SlideMessages.Edges {
		if edge.Node != nil {
			candidates = append(candidates, candidate{message: edge.Node, cursor: edge.Cursor})
		}
	}
	truncated := len(candidates) > limit
	if truncated {
		candidates = candidates[:limit]
	}

	view.Messages = make([]MessageView, 0, len(candidates))
	for i := len(candidates) - 1; i >= 0; i-- {
		view.Messages = append(view.Messages, newMessageView(candidates[i].message, ownFBID, participants))
	}

	view.HasMoreOlder = thread.SlideMessages.PageInfo.HasNextPage || truncated
	view.OldestCursor = thread.SlideMessages.PageInfo.EndCursor
	if truncated && len(candidates) > 0 && candidates[len(candidates)-1].cursor != "" {
		// The page cursor points past every returned message. When the caller
		// asked for a smaller page, use the oldest displayed message's cursor
		// so the next request does not skip the messages in between.
		view.OldestCursor = candidates[len(candidates)-1].cursor
	}
	return view
}

func newMessageView(message *slidetypes.Message, ownFBID int64, participants map[int64]string) MessageView {
	kind, text, mediaCount := describeMessageContent(message)

	senderFBID := message.SenderFBID
	senderName := ""
	if message.Sender != nil {
		if senderFBID == 0 {
			senderFBID = message.Sender.UserDict.InteropMessagingUserFBID
		}
		senderName = message.Sender.Name
		if senderName == "" {
			senderName = displayName(&message.Sender.UserDict)
		}
	}
	if senderName == "" {
		senderName = participants[senderFBID]
	}

	var timestampMS int64
	if !message.TimestampMS.IsZero() {
		timestampMS = message.TimestampMS.UnixMilli()
	}

	id := message.MessageID
	if id == "" {
		id = message.ID
	}

	contentType := message.Content.TypeName
	if contentType == "" {
		contentType = message.Typename
	}

	return MessageView{
		ID:                 id,
		OfflineThreadingID: message.OfflineThreadingID,
		SenderFBID:         senderFBID,
		SenderName:         senderName,
		IsFromViewer:       ownFBID != 0 && senderFBID == ownFBID,
		TimestampMS:        timestampMS,
		Kind:               kind,
		ContentType:        contentType,
		Text:               text,
		MediaCount:         mediaCount,
		RepliedToMessageID: message.RepliedToMessageID,
	}
}

func participantNames(thread *slidetypes.ThreadInfo) map[int64]string {
	names := make(map[int64]string, len(thread.Users)+1)
	for _, user := range thread.Users {
		if user == nil {
			continue
		}
		names[user.InteropMessagingUserFBID] = displayName(user)
	}
	if thread.Viewer != nil {
		names[thread.Viewer.InteropMessagingUserFBID] = displayName(thread.Viewer)
	}
	return names
}

func displayName(user *slidetypes.User) string {
	if user == nil {
		return ""
	}
	if user.FullName != "" {
		return user.FullName
	}
	return user.Username
}

func describeMessageContent(message *slidetypes.Message) (kind, text string, mediaCount int) {
	kind = "unknown"
	switch content := message.Content.Content.(type) {
	case *slidetypes.MessageContentText:
		kind = "text"
		text = content.TextBody
	case *slidetypes.MessageContentAdminText:
		kind = "admin"
		text = joinTextFragments(content.TextFragments)
	case *slidetypes.MessageContentImage:
		kind = "image"
		mediaCount = len(content.Attachments)
	case *slidetypes.MessageContentVideo:
		kind = "video"
		mediaCount = len(content.Videos)
	case *slidetypes.MessageContentAudio:
		kind = "audio"
		mediaCount = len(content.AudioAttachments)
	case *slidetypes.MessageContentAnimatedMedia:
		kind = "gif"
		mediaCount = len(content.AnimatedMedia)
		for _, item := range content.AnimatedMedia {
			if item != nil && item.IsSticker {
				kind = "sticker"
				break
			}
		}
	case *slidetypes.MessageContentMultiMedia:
		kind = "media"
		mediaCount = len(content.Attachments)
	case *slidetypes.MessageContentRavenImage:
		kind = "image"
		mediaCount = 1
	case *slidetypes.MessageContentRavenVideo:
		kind = "video"
		mediaCount = 1
	case *slidetypes.MessageContentSticker:
		kind = "sticker"
		text = content.AltText
	case *slidetypes.MessageContentXMA:
		kind = "link"
		text = content.XMATextBody
	case *slidetypes.MessageContentMusicSticker:
		kind = "music"
	case *slidetypes.MessageContentAIRichResponse:
		kind = "ai"
		text = content.UnifiedResponse
	case *slidetypes.MessageContentAISearchResponse:
		kind = "ai-search"
		text = content.MessageTextBody
	case slidetypes.MessageContentUnknown:
		kind = "unknown"
	}

	if text == "" && kind == "admin" {
		text = message.IGDSnippet
	}
	if text == "" && message.TextBody != "" && (kind == "text" || kind == "admin" || kind == "unknown") {
		text = message.TextBody
	}
	return kind, text, mediaCount
}

func joinTextFragments(fragments []slidetypes.TextFragment) string {
	parts := make([]string, 0, len(fragments))
	for _, fragment := range fragments {
		if fragment.Plaintext != "" {
			parts = append(parts, fragment.Plaintext)
		}
	}
	return strings.Join(parts, "")
}
