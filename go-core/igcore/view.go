package igcore

import (
	"encoding/json"
	"math"
	"net/url"
	"regexp"
	"strconv"
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
	// AvatarURL is the viewer's own profile picture, used by the inbox header.
	AvatarURL string `json:"avatar_url,omitempty"`
}

type ParticipantView struct {
	IGID      string `json:"ig_id"`
	FBID      int64  `json:"fbid"`
	Username  string `json:"username"`
	Name      string `json:"name"`
	AvatarURL string `json:"avatar_url,omitempty"`
	// IsViewer marks the logged-in account when Instagram includes it in the
	// participant list. The app excludes it from group avatar collages.
	IsViewer bool `json:"is_viewer,omitempty"`
}

type ThreadView struct {
	ID         string `json:"id"`
	ThreadFBID string `json:"thread_fbid"`
	ThreadID   string `json:"thread_id"`
	ThreadKey  int64  `json:"thread_key"`
	Subtype    string `json:"subtype"`
	IsGroup    bool   `json:"is_group"`
	Title      string `json:"title"`
	// AvatarURL is the conversation photo: the group's custom image, or, for a
	// one-to-one thread, the other person's profile picture.
	AvatarURL      string            `json:"avatar_url,omitempty"`
	Participants   []ParticipantView `json:"participants"`
	LastActivityMS int64             `json:"last_activity_ms"`
	Folder         string            `json:"folder,omitempty"`
	SystemFolder   string            `json:"system_folder,omitempty"`
	MarkedAsUnread bool              `json:"marked_as_unread"`
	IsMuted        bool              `json:"is_muted"`
	IsPinned       bool              `json:"is_pinned"`
	// LastMessage is the inbox preview stub. The inbox only returns partial
	// messages, so it is safe for a one-line preview and must never be treated
	// as history.
	LastMessage *MessagePreviewView `json:"last_message,omitempty"`
}

// MessagePreviewView is the newest message Instagram includes in the inbox.
// It is intentionally smaller than MessageView: no media URLs (they are signed
// and expire) and no reactions, only what an inbox row needs.
type MessagePreviewView struct {
	ID           string `json:"id,omitempty"`
	SenderFBID   int64  `json:"sender_fbid,omitempty"`
	SenderName   string `json:"sender_name,omitempty"`
	IsFromViewer bool   `json:"is_from_viewer"`
	TimestampMS  int64  `json:"timestamp_ms"`
	Kind         string `json:"kind"`
	Text         string `json:"text,omitempty"`
	// Snippet is Instagram's own inbox text, used when the stub's content is
	// not enough to build a preview (for example link/XMA messages).
	Snippet string `json:"snippet,omitempty"`
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
		avatarURL := viewer.Data.ProfilePicURLHd
		if avatarURL == "" {
			avatarURL = viewer.Data.ProfilePicURL
		}
		view.Viewer = ViewerView{
			IGID:      viewer.ID,
			Username:  viewer.Data.Username,
			Name:      viewer.Data.FullName,
			FBID:      viewer.GetFBID(),
			AvatarURL: avatarURL,
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
			IGID:      user.ID,
			FBID:      user.InteropMessagingUserFBID,
			Username:  user.Username,
			Name:      user.FullName,
			AvatarURL: user.ProfilePicURL,
			IsViewer:  ownFBID != 0 && user.InteropMessagingUserFBID == ownFBID,
		})
	}

	view.AvatarURL = threadAvatarURL(thread, view.Participants, ownFBID)

	if view.Title == "" {
		view.Title = displayTitle(view.Participants, ownFBID)
	}

	view.LastMessage = lastMessagePreview(thread.SlideMessages, ownFBID, participantNames(thread))

	return view
}

// lastMessagePreview picks the newest inbox stub. The inbox returns messages
// newest-first, but older pages and some thread types are not guaranteed to be
// ordered, so compare timestamps instead of trusting the edge order.
func lastMessagePreview(page *slidetypes.SlideMessages, ownFBID int64, participants map[int64]string) *MessagePreviewView {
	if page == nil {
		return nil
	}

	var newest *slidetypes.Message
	for _, edge := range page.Edges {
		message := edge.Node
		if message == nil {
			continue
		}
		if newest == nil || message.TimestampMS.UnixMilli() > newest.TimestampMS.UnixMilli() {
			newest = message
		}
	}
	if newest == nil {
		return nil
	}

	kind, text, _ := describeMessageContent(newest)

	senderFBID := newest.SenderFBID
	senderName := ""
	if newest.Sender != nil {
		if senderFBID == 0 {
			senderFBID = newest.Sender.UserDict.InteropMessagingUserFBID
		}
		senderName = newest.Sender.Name
		if senderName == "" {
			senderName = displayName(&newest.Sender.UserDict)
		}
	}
	if senderName == "" {
		senderName = participants[senderFBID]
	}

	id := newest.MessageID
	if id == "" {
		id = newest.ID
	}

	return &MessagePreviewView{
		ID:           id,
		SenderFBID:   senderFBID,
		SenderName:   senderName,
		IsFromViewer: ownFBID != 0 && senderFBID == ownFBID,
		TimestampMS:  newest.TimestampMS.UnixMilli(),
		Kind:         kind,
		Text:         text,
		Snippet:      newest.IGDSnippet,
	}
}

// threadAvatarURL picks the conversation photo. Groups use the custom thread
// image, which Instagram leaves empty until someone uploads one; the app then
// composes a collage from the participant avatars. Direct threads have no
// thread image, so use the other person's profile picture.
func threadAvatarURL(thread *slidetypes.ThreadInfo, participants []ParticipantView, ownFBID int64) string {
	if thread.IsGroup {
		return thread.ThreadImageURL
	}
	for _, participant := range participants {
		if ownFBID != 0 && participant.FBID == ownFBID {
			continue
		}
		if participant.AvatarURL != "" {
			return participant.AvatarURL
		}
	}
	// Self threads and responses without a viewer ID still deserve an avatar.
	for _, participant := range participants {
		if participant.AvatarURL != "" {
			return participant.AvatarURL
		}
	}
	return ""
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
	ProtocolVersion string            `json:"protocol_version"`
	Thread          ThreadView        `json:"thread"`
	Messages        []MessageView     `json:"messages"`
	ReadReceipts    []ReadReceiptView `json:"read_receipts,omitempty"`
	HasMoreOlder    bool              `json:"has_more_older"`
	OldestCursor    string            `json:"oldest_cursor,omitempty"`
}

// ReadReceiptView is a participant's read watermark for a thread. FromViewer
// marks the viewer's own receipt, which only means the viewer has read the
// thread; it must not be rendered as a seen tick.
type ReadReceiptView struct {
	ParticipantFBID int64 `json:"participant_fbid"`
	WatermarkMS     int64 `json:"watermark_ms"`
	FromViewer      bool  `json:"from_viewer,omitempty"`
}

// ThreadPageView is a page fetched with IGDMessageListOffMsysQuery. It has no
// thread metadata beyond the short thread ID.
type ThreadPageView struct {
	ProtocolVersion string        `json:"protocol_version"`
	ThreadID        string        `json:"thread_id"`
	Messages        []MessageView `json:"messages"`
	HasMoreOlder    bool          `json:"has_more_older"`
	OldestCursor    string        `json:"oldest_cursor,omitempty"`
}

type ReactionView struct {
	Emoji        string `json:"emoji"`
	SenderFBID   int64  `json:"sender_fbid"`
	IsFromViewer bool   `json:"is_from_viewer"`
	TimestampMS  int64  `json:"timestamp_ms,omitempty"`
}

type MediaView struct {
	Type               string    `json:"type"`
	AttachmentFBID     string    `json:"attachment_fbid,omitempty"`
	URL                string    `json:"url,omitempty"`
	FallbackURL        string    `json:"fallback_url,omitempty"`
	PreviewURL         string    `json:"preview_url,omitempty"`
	PreviewFallbackURL string    `json:"preview_fallback_url,omitempty"`
	Width              int       `json:"width,omitempty"`
	Height             int       `json:"height,omitempty"`
	DurationMS         int       `json:"duration_ms,omitempty"`
	Waveform           []float64 `json:"waveform,omitempty"`
	// AltText is Instagram's accessibility description for a sticker. It is
	// not message body text and must not be rendered in place of the sticker.
	AltText string `json:"alt_text,omitempty"`
	// TargetID, TargetURL, and Shortcode identify a shared Instagram item
	// (an XMA attachment). A reel's DM payload only carries a preview image;
	// clients resolve the playable video from TargetID when the user opens it.
	// Link shares (posts, stories, external pages) use URL as the target.
	TargetID  string `json:"target_id,omitempty"`
	TargetURL string `json:"target_url,omitempty"`
	Shortcode string `json:"shortcode,omitempty"`
	// ViewOnce marks Instagram's view-once ("raven") media. The one allowed
	// view belongs to the official app, so no CDN URL is exposed and clients
	// must show a notice instead of rendering or downloading the attachment.
	ViewOnce bool `json:"view_once,omitempty"`
	// ViewMode is the raven replay policy: "viewed" for a single view or
	// "replayed" when Instagram allows one replay. Empty for permanent media.
	ViewMode string `json:"view_mode,omitempty"`
}

// ReplyView is the quoted snapshot Instagram embeds in a reply. Keeping the
// snapshot lets the app render the quote even when the original message is
// outside the loaded page.
type ReplyView struct {
	ID           string `json:"id"`
	SenderFBID   int64  `json:"sender_fbid"`
	SenderName   string `json:"sender_name,omitempty"`
	IsFromViewer bool   `json:"is_from_viewer"`
	Kind         string `json:"kind"`
	Text         string `json:"text,omitempty"`
	MediaCount   int    `json:"media_count,omitempty"`
}

type MessageView struct {
	ID                 string         `json:"id"`
	OfflineThreadingID string         `json:"offline_threading_id,omitempty"`
	SenderFBID         int64          `json:"sender_fbid"`
	SenderName         string         `json:"sender_name,omitempty"`
	IsFromViewer       bool           `json:"is_from_viewer"`
	TimestampMS        int64          `json:"timestamp_ms"`
	Kind               string         `json:"kind"`
	ContentType        string         `json:"content_type,omitempty"`
	Text               string         `json:"text,omitempty"`
	MediaCount         int            `json:"media_count,omitempty"`
	Media              []MediaView    `json:"media,omitempty"`
	RepliedToMessageID string         `json:"replied_to_message_id,omitempty"`
	RepliedToMessage   *ReplyView     `json:"replied_to_message,omitempty"`
	Reactions          []ReactionView `json:"reactions,omitempty"`
	// Seen is true when another participant's read watermark has reached this
	// message. It is only meaningful for messages sent by the viewer.
	Seen bool `json:"seen"`
}

func newThreadDetailView(thread *slidetypes.ThreadInfo, ownFBID int64, limit int) *ThreadDetailView {
	view := &ThreadDetailView{
		ProtocolVersion: ProtocolVersion,
		Thread:          newThreadView(thread, ownFBID),
		ReadReceipts:    newReadReceiptViews(thread.SlideReadReceipts, ownFBID),
	}
	view.Messages, view.HasMoreOlder, view.OldestCursor = normalizeMessagePage(
		thread.SlideMessages, ownFBID, participantNames(thread), limit,
	)
	applyReadReceipts(view.Messages, view.ReadReceipts)
	return view
}

func newReadReceiptViews(receipts []slidetypes.ReadReceipt, ownFBID int64) []ReadReceiptView {
	views := make([]ReadReceiptView, 0, len(receipts))
	for _, receipt := range receipts {
		if receipt.ParticipantFBID == 0 || receipt.WatermarkTimestampMS.IsZero() {
			continue
		}
		views = append(views, ReadReceiptView{
			ParticipantFBID: receipt.ParticipantFBID,
			WatermarkMS:     receipt.WatermarkTimestampMS.UnixMilli(),
			FromViewer:      ownFBID != 0 && receipt.ParticipantFBID == ownFBID,
		})
	}
	return views
}

// applyReadReceipts marks the viewer's messages as seen when another
// participant has read up to their timestamp. The viewer's own watermark only
// means the viewer has read the thread, so it must not produce seen ticks.
func applyReadReceipts(messages []MessageView, receipts []ReadReceiptView) {
	var watermark int64
	for _, receipt := range receipts {
		if receipt.FromViewer {
			continue
		}
		if receipt.WatermarkMS > watermark {
			watermark = receipt.WatermarkMS
		}
	}
	if watermark == 0 {
		return
	}

	for i := range messages {
		if !messages[i].IsFromViewer || messages[i].TimestampMS == 0 {
			continue
		}
		if messages[i].TimestampMS <= watermark {
			messages[i].Seen = true
		}
	}
}

func normalizeMessagePage(page *slidetypes.SlideMessages, ownFBID int64, participants map[int64]string, limit int) ([]MessageView, bool, string) {
	if limit <= 0 {
		limit = 20
	}
	if limit > 100 {
		limit = 100
	}
	if page == nil {
		return []MessageView{}, false, ""
	}

	type candidate struct {
		message *slidetypes.Message
		cursor  string
	}

	candidates := make([]candidate, 0, len(page.Edges))
	for _, edge := range page.Edges {
		if edge.Node != nil {
			candidates = append(candidates, candidate{message: edge.Node, cursor: edge.Cursor})
		}
	}
	truncated := len(candidates) > limit
	if truncated {
		candidates = candidates[:limit]
	}

	messages := make([]MessageView, 0, len(candidates))
	for i := len(candidates) - 1; i >= 0; i-- {
		messages = append(messages, newMessageView(candidates[i].message, ownFBID, participants))
	}

	hasMoreOlder := page.PageInfo.HasNextPage || truncated
	oldestCursor := page.PageInfo.EndCursor
	if truncated && len(candidates) > 0 && candidates[len(candidates)-1].cursor != "" {
		// The page cursor points past every returned message. When the caller
		// asked for a smaller page, use the oldest displayed message's cursor
		// so the next request does not skip the messages in between.
		oldestCursor = candidates[len(candidates)-1].cursor
	}
	return messages, hasMoreOlder, oldestCursor
}

// messageSender resolves the sender FBID and display name Instagram attached
// to a message. Replies embed an abbreviated copy of the original message, so
// both paths share this lookup.
func messageSender(message *slidetypes.Message, participants map[int64]string) (int64, string) {
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
	return senderFBID, senderName
}

// newReplyView normalizes the quoted message embedded in a reply.
func newReplyView(message *slidetypes.Message, ownFBID int64, participants map[int64]string) *ReplyView {
	if message == nil {
		return nil
	}
	id := message.MessageID
	if id == "" {
		id = message.ID
	}
	if id == "" {
		return nil
	}
	kind, text, mediaCount := describeMessageContent(message)
	senderFBID, senderName := messageSender(message, participants)
	return &ReplyView{
		ID:           id,
		SenderFBID:   senderFBID,
		SenderName:   senderName,
		IsFromViewer: ownFBID != 0 && senderFBID == ownFBID,
		Kind:         kind,
		Text:         text,
		MediaCount:   mediaCount,
	}
}

func newMessageView(message *slidetypes.Message, ownFBID int64, participants map[int64]string) MessageView {
	kind, text, mediaCount := describeMessageContent(message)
	senderFBID, senderName := messageSender(message, participants)

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

	reply := newReplyView(message.RepliedToMessage, ownFBID, participants)
	repliedToID := message.RepliedToMessageID
	if repliedToID == "" && reply != nil {
		repliedToID = reply.ID
	}

	reactions := make([]ReactionView, 0, len(message.Reactions))
	for _, reaction := range message.Reactions {
		if reaction == nil || reaction.Reaction == "" {
			continue
		}
		var reactionTS int64
		if !reaction.ReactionTimestampMS.IsZero() {
			reactionTS = reaction.ReactionTimestampMS.UnixMilli()
		}
		reactions = append(reactions, ReactionView{
			Emoji:        reaction.Reaction,
			SenderFBID:   reaction.SenderFBID,
			IsFromViewer: ownFBID != 0 && reaction.SenderFBID == ownFBID,
			TimestampMS:  reactionTS,
		})
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
		Media:              messageMedia(message),
		RepliedToMessageID: repliedToID,
		RepliedToMessage:   reply,
		Reactions:          reactions,
	}
}

func messageMedia(message *slidetypes.Message) []MediaView {
	var media []MediaView
	switch content := message.Content.Content.(type) {
	case *slidetypes.MessageContentImage:
		for _, attachment := range content.Attachments {
			if view, ok := attachmentView(attachment, "image"); ok {
				media = append(media, view)
			}
		}
	case *slidetypes.MessageContentVideo:
		for _, attachment := range content.Videos {
			if view, ok := attachmentView(attachment, "video"); ok {
				media = append(media, view)
			}
		}
	case *slidetypes.MessageContentMultiMedia:
		for _, attachment := range content.Attachments {
			if view, ok := attachmentView(attachment, "media"); ok {
				media = append(media, view)
			}
		}
	case *slidetypes.MessageContentRavenImage:
		media = appendRavenMedia(media, content.Attachment, content.ViewMode, "image")
	case *slidetypes.MessageContentRavenVideo:
		media = appendRavenMedia(media, content.Attachment, content.ViewMode, "video")
	case *slidetypes.MessageContentAudio:
		for _, attachment := range content.AudioAttachments {
			if attachment == nil {
				continue
			}
			media = append(media, MediaView{
				Type:           "audio",
				AttachmentFBID: attachment.AttachmentFBID,
				URL:            attachment.AttachmentCDNURL,
				DurationMS:     attachment.PlayableDurationMS,
				Waveform:       normalizeWaveform(attachment.WaveformData),
			})
		}
	case *slidetypes.MessageContentAnimatedMedia:
		for _, attachment := range content.AnimatedMedia {
			if attachment == nil {
				continue
			}
			kind := "gif"
			mediaURL := attachment.PreviewCDNURL
			if attachment.IsSticker {
				kind = "sticker"
				if attachment.AttachmentWebpURL != "" {
					mediaURL = attachment.AttachmentWebpURL
				}
			} else if attachment.AttachmentMP4URL != "" {
				mediaURL = attachment.AttachmentMP4URL
			}
			media = append(media, MediaView{
				Type:       kind,
				URL:        mediaURL,
				PreviewURL: attachment.PreviewCDNURL,
				Width:      attachment.PreviewWidth,
				Height:     attachment.PreviewHeight,
			})
		}
	case *slidetypes.MessageContentXMA:
		if view, ok := sharedReelMedia(content); ok {
			media = append(media, view)
		} else if view, ok := xmaLinkMedia(content); ok {
			media = append(media, view)
		}
	case *slidetypes.MessageContentSticker:
		// Store, cutout, avatar, and AI stickers all arrive as this content
		// type with only a preview image and an accessibility description.
		if url := strings.TrimSpace(content.PreviewURL); url != "" {
			media = append(media, MediaView{
				Type:       "sticker",
				URL:        url,
				PreviewURL: url,
				Width:      content.PreviewWidth,
				Height:     content.PreviewHeight,
				AltText:    content.AltText,
			})
		}
	}
	return media
}

// sharedReelPathPattern matches Instagram's reel share URLs. Post and story
// URLs deliberately do not match: they stay link shares.
var sharedReelPathPattern = regexp.MustCompile(`/(?:reel|reels)/([A-Za-z0-9_-]+)`)

// sharedReelTarget recognizes a native reel share. The DM payload carries the
// media ID and a public reel URL, but no playable video URL.
func sharedReelTarget(xma *slidetypes.XMAContent) (targetID, shortcode string, ok bool) {
	if xma == nil {
		return "", "", false
	}
	targetID = strings.TrimSpace(xma.TargetID)
	if targetID == "" {
		return "", "", false
	}
	if _, err := strconv.ParseInt(targetID, 10, 64); err != nil {
		return "", "", false
	}

	targetURL := unwrapInstagramRedirect(strings.TrimSpace(xma.TargetURL))
	if parsed, err := url.Parse(targetURL); err == nil {
		if match := sharedReelPathPattern.FindStringSubmatch(parsed.Path); len(match) > 1 {
			shortcode = match[1]
		}
	}
	if shortcode == "" && !strings.Contains(strings.ToLower(xma.Typename), "reel") {
		return "", "", false
	}
	return targetID, shortcode, true
}

// unwrapInstagramRedirect resolves the link wrapper Instagram sometimes uses
// for share URLs. Other URLs are returned unchanged.
func unwrapInstagramRedirect(raw string) string {
	parsed, err := url.Parse(raw)
	if err != nil || !strings.EqualFold(parsed.Host, "l.instagram.com") {
		return raw
	}
	if target := parsed.Query().Get("u"); target != "" {
		return target
	}
	return raw
}

// xmaTargetURL returns the public URL carried by an XMA attachment. It is used
// for link shares (posts, stories, external pages) that have no playable media.
func xmaTargetURL(content *slidetypes.MessageContentXMA) string {
	if content == nil || content.XMA == nil {
		return ""
	}
	return unwrapInstagramRedirect(strings.TrimSpace(content.XMA.TargetURL))
}

// sharedReelMedia normalizes a native reel share into a media item. The item
// keeps the preview image and the identifiers needed to resolve the video.
func sharedReelMedia(content *slidetypes.MessageContentXMA) (MediaView, bool) {
	if content == nil {
		return MediaView{}, false
	}
	targetID, shortcode, ok := sharedReelTarget(content.XMA)
	if !ok {
		return MediaView{}, false
	}
	view := MediaView{
		Type:      "reel",
		TargetID:  targetID,
		TargetURL: xmaTargetURL(content),
		Shortcode: shortcode,
	}
	applyXMAPreview(&view, content.XMA)
	return view, true
}

// xmaLinkMedia normalizes a non-reel XMA share into a link card. The target URL
// is the link the user sees and opens; the preview image is optional.
func xmaLinkMedia(content *slidetypes.MessageContentXMA) (MediaView, bool) {
	targetURL := xmaTargetURL(content)
	if targetURL == "" {
		return MediaView{}, false
	}
	view := MediaView{
		Type:      "link",
		URL:       targetURL,
		TargetURL: targetURL,
	}
	applyXMAPreview(&view, content.XMA)
	return view, true
}

// xmaShareKind labels a link share so inbox previews and reply quotes can say
// "post" or "story" instead of the generic "link".
func xmaShareKind(targetURL string) string {
	parsed, err := url.Parse(targetURL)
	if err != nil {
		return "link"
	}
	switch {
	case strings.HasPrefix(parsed.Path, "/p/"):
		return "post"
	case strings.HasPrefix(parsed.Path, "/stories/"):
		return "story"
	case sharedReelPathPattern.MatchString(parsed.Path):
		return "reel"
	default:
		return "link"
	}
}

// isExpiredXMA recognizes Instagram's placeholder for a share whose media is
// gone. The payload has no URL, so clients can only show the notice text.
func isExpiredXMA(xma *slidetypes.XMAContent) bool {
	if xma == nil {
		return false
	}
	if strings.Contains(strings.ToLower(xma.Typename), "expiredplaceholder") {
		return true
	}
	eyebrow := strings.ToLower(xma.EyebrowText)
	caption := strings.ToLower(xma.CaptionBodyText)
	return strings.Contains(eyebrow, "shared") && strings.Contains(caption, "unavailable")
}

// xmaPlaceholderKind labels an XMA share that has no URL, such as Instagram's
// "Story unavailable" placeholder. The caption/eyebrow text is the only clue.
func xmaPlaceholderKind(xma *slidetypes.XMAContent) string {
	if xma == nil {
		return "link"
	}
	text := strings.ToLower(xma.EyebrowText + " " + xma.CaptionBodyText + " " + xma.TitleText)
	switch {
	case strings.Contains(text, "story"):
		return "story"
	case strings.Contains(text, "reel"):
		return "reel"
	case strings.Contains(text, "post"):
		return "post"
	default:
		return "link"
	}
}

// xmaFallbackText gives an XMA share without a URL a readable body instead of
// an empty bubble. Expired story shares carry "Shared X's story" in the eyebrow
// and "Story unavailable" in the caption.
func xmaFallbackText(xma *slidetypes.XMAContent) string {
	if xma == nil {
		return ""
	}
	eyebrow := strings.TrimSpace(xma.EyebrowText)
	caption := strings.TrimSpace(xma.CaptionBodyText)
	title := strings.TrimSpace(xma.TitleText)
	switch {
	case eyebrow != "" && caption != "":
		return joinSentence(eyebrow, caption)
	case eyebrow != "":
		return eyebrow
	case caption != "":
		return caption
	default:
		return title
	}
}

// joinSentence joins two notice fragments into one sentence pair. The first
// fragment gets a period unless it already ends with terminal punctuation, and
// the second gets a final period for a finished sentence.
func joinSentence(first, second string) string {
	first = strings.TrimSpace(first)
	second = strings.TrimSpace(second)
	switch {
	case first == "":
		return ensureSentenceEnd(second)
	case second == "":
		return ensureSentenceEnd(first)
	case hasTerminalPunctuation(first):
		return first + " " + ensureSentenceEnd(second)
	default:
		return first + ". " + ensureSentenceEnd(second)
	}
}

func ensureSentenceEnd(text string) string {
	text = strings.TrimSpace(text)
	if text == "" || hasTerminalPunctuation(text) {
		return text
	}
	return text + "."
}

func hasTerminalPunctuation(text string) bool {
	return strings.HasSuffix(text, ".") ||
		strings.HasSuffix(text, "!") ||
		strings.HasSuffix(text, "?")
}

func applyXMAPreview(view *MediaView, xma *slidetypes.XMAContent) {
	if view == nil || xma == nil {
		return
	}
	preview := xma.PreviewImage
	if preview == nil {
		preview = xma.XMAPreviewImage
	}
	if preview == nil {
		return
	}
	view.PreviewURL = preview.URL
	view.PreviewFallbackURL = preview.FallbackURL
	view.Width = preview.Width
	view.Height = preview.Height
}

// appendRavenMedia normalizes Instagram's ephemeral ("raven") attachments.
// View-once and replayable media never expose their CDN URLs: the allowed views
// belong to the official app, and Instagram often omits the attachment from the
// payload until it is opened there. "Keep in chat" media has no view limit, so
// it is treated like a normal attachment.
func appendRavenMedia(media []MediaView, attachment *slidetypes.Attachment, mode slidetypes.RavenViewMode, kind string) []MediaView {
	viewType := mode.ViewType()
	if viewType == "" {
		if view, ok := attachmentView(attachment, kind); ok {
			media = append(media, view)
		}
		return media
	}
	// Keep the item even when the attachment is missing so clients can show
	// the notice instead of an empty bubble.
	return append(media, MediaView{Type: kind, ViewOnce: true, ViewMode: viewType})
}

// normalizeWaveform converts Instagram's audio waveform data into amplitudes in
// the 0..1 range. Entries are normally numbers, but some payloads wrap them in
// an object; non-numeric entries are skipped. Values above 1 are assumed to be
// the 0..255 scale used by some clients.
func normalizeWaveform(raw []any) []float64 {
	if len(raw) == 0 {
		return nil
	}
	waveform := make([]float64, 0, len(raw))
	maxValue := 0.0
	for _, item := range raw {
		value, ok := waveformValue(item)
		if !ok {
			continue
		}
		if value < 0 || math.IsNaN(value) {
			value = 0
		}
		waveform = append(waveform, value)
		if value > maxValue {
			maxValue = value
		}
		if len(waveform) >= maxVoiceWaveformSamples {
			break
		}
	}
	if len(waveform) == 0 {
		return nil
	}
	if maxValue > 1.5 {
		maxValue = 255
	} else {
		maxValue = 1
	}
	for index, value := range waveform {
		waveform[index] = math.Min(value/maxValue, 1)
	}
	return waveform
}

func waveformValue(item any) (float64, bool) {
	switch value := item.(type) {
	case float64:
		return value, true
	case float32:
		return float64(value), true
	case int:
		return float64(value), true
	case int64:
		return float64(value), true
	case json.Number:
		parsed, err := value.Float64()
		return parsed, err == nil
	case map[string]any:
		for _, key := range []string{"amplitude", "value", "height"} {
			if nested, ok := value[key]; ok {
				return waveformValue(nested)
			}
		}
	}
	return 0, false
}

func attachmentView(attachment *slidetypes.Attachment, kind string) (MediaView, bool) {
	if attachment == nil {
		return MediaView{}, false
	}
	return MediaView{
		Type:               kind,
		AttachmentFBID:     attachment.AttachmentFBID,
		URL:                attachment.AttachmentCDNURL,
		FallbackURL:        attachment.AttachmentCDNFallbackURL,
		PreviewURL:         attachment.PreviewCDNURL,
		PreviewFallbackURL: attachment.PreviewCDNFallbackURL,
		Width:              attachment.PreviewWidth,
		Height:             attachment.PreviewHeight,
	}, true
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
		if content.IsReactionActionLog {
			// Likes and reactions arrive twice: as a reaction on the target
			// message and as this activity entry, which the app keeps out of
			// the transcript.
			kind = "reaction-log"
		}
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
		if strings.TrimSpace(content.PreviewURL) != "" {
			mediaCount = 1
		} else {
			// Without an image the accessibility description is the only
			// content left, so keep it as a readable fallback.
			text = content.AltText
		}
	case *slidetypes.MessageContentXMA:
		text = content.XMATextBody
		if _, _, ok := sharedReelTarget(content.XMA); ok {
			kind = "reel"
			mediaCount = 1
		} else if targetURL := xmaTargetURL(content); targetURL != "" {
			kind = xmaShareKind(targetURL)
			mediaCount = 1
		} else {
			kind = xmaPlaceholderKind(content.XMA)
			if isExpiredXMA(content.XMA) {
				// The app renders the "-unavailable" kinds as a notice card
				// instead of a normal text bubble.
				kind += "-unavailable"
			}
			if fallback := xmaFallbackText(content.XMA); fallback != "" {
				if text == "" {
					text = fallback
				} else {
					text += "\n" + fallback
				}
			}
		}
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

	if text == "" && (kind == "admin" || kind == "reaction-log") {
		text = message.IGDSnippet
	}
	if text == "" && mediaCount == 0 && (kind == "link" || kind == "unknown") &&
		message.TextBody == "" {
		// A share with no URL and no body would otherwise render an empty
		// bubble. Instagram's own inbox text is a readable fallback.
		text = message.IGDSnippet
	}
	if text == "" && message.TextBody != "" &&
		(kind == "text" || kind == "admin" || kind == "reaction-log" || kind == "unknown") {
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
