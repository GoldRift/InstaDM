package igcore

import (
	"testing"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/mautrix-meta/pkg/messagix/types"
	"go.mau.fi/util/jsontime"
)

func TestNewMailboxView(t *testing.T) {
	muted := true
	timestamp := jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)}

	viewer := &types.PolarisViewer{
		ID: "ig-viewer",
		Data: types.ViewerData{
			Username: "me",
			FullName: "Me",
			// The profile ID can differ from the internal EIMU ID (111) used
			// by participants and message senders.
			Fbid:            "999",
			ProfilePicURL:   "https://cdn.example.invalid/me-small.jpg",
			ProfilePicURLHd: "https://cdn.example.invalid/me.jpg",
		},
	}
	mailbox := &slidetypes.Mailbox{
		UQSeqID: 42,
		ThreadsByFolder: slidetypes.Edged[slidetypes.Node[slidetypes.WrappedThreadInfo]]{
			PageInfo: slidetypes.PageInfo{HasNextPage: true, EndCursor: "cursor"},
			Edges: []slidetypes.Node[slidetypes.WrappedThreadInfo]{
				{
					Node: slidetypes.WrappedThreadInfo{
						AsIGDirectThread: &slidetypes.ThreadInfo{
							ID:                      "short-id",
							ThreadFBID:              "thread-fbid",
							ThreadID:                "long-id",
							ThreadKey:               222,
							ThreadSubtype:           slidetypes.ThreadSubtypeDirect,
							LastActivityTimestampMS: timestamp,
							IsMuted:                 &muted,
							Users: []*slidetypes.User{
								{ID: "ig-other", InteropMessagingUserFBID: 222, Username: "other", FullName: "Other Person", ProfilePicURL: "https://cdn.example.invalid/other.jpg"},
								{ID: "ig-viewer", InteropMessagingUserFBID: 111, Username: "me", FullName: "Me", ProfilePicURL: "https://cdn.example.invalid/me.jpg"},
							},
						},
					},
				},
				{
					// Empty wrapper entries should be skipped, not panic.
					Node: slidetypes.WrappedThreadInfo{},
				},
			},
		},
	}

	view := newMailboxView(viewer, mailbox, 111)

	if view.Viewer.Username != "me" || view.Viewer.FBID != 999 {
		t.Errorf("viewer = %+v, want username me and profile fbid 999", view.Viewer)
	}
	if view.Viewer.AvatarURL != "https://cdn.example.invalid/me.jpg" {
		t.Errorf("viewer avatar = %q, want the HD profile picture", view.Viewer.AvatarURL)
	}
	if view.SeqID != 42 {
		t.Errorf("seq_id = %d, want 42", view.SeqID)
	}
	if !view.HasNextPage || view.EndCursor != "cursor" {
		t.Errorf("page info = (%v, %q), want (true, cursor)", view.HasNextPage, view.EndCursor)
	}
	if len(view.Threads) != 1 {
		t.Fatalf("threads = %d, want 1", len(view.Threads))
	}

	thread := view.Threads[0]
	if thread.ID != "short-id" || thread.ThreadID != "long-id" || thread.ThreadFBID != "thread-fbid" {
		t.Errorf("thread IDs = %+v, unexpected", thread)
	}
	if thread.Title != "Other Person" {
		t.Errorf("title = %q, want %q", thread.Title, "Other Person")
	}
	if thread.LastActivityMS != 1_700_000_000_000 {
		t.Errorf("last_activity_ms = %d, want 1700000000000", thread.LastActivityMS)
	}
	if !thread.IsMuted {
		t.Error("is_muted = false, want true")
	}
	if thread.AvatarURL != "https://cdn.example.invalid/other.jpg" {
		t.Errorf("avatar_url = %q, want the other participant's picture", thread.AvatarURL)
	}
}

func TestNewThreadDetailView(t *testing.T) {
	timestamp := jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)}
	textMessage := func(id string, sender int64, body string) *slidetypes.Message {
		return &slidetypes.Message{
			MessageID:   id,
			SenderFBID:  sender,
			TimestampMS: timestamp,
			Content: slidetypes.MessageContentWrapper{
				TypeName: "SlideMessageText",
				Content:  &slidetypes.MessageContentText{TextBody: body},
			},
		}
	}

	thread := &slidetypes.ThreadInfo{
		ID:          "short-id",
		ThreadTitle: "Test Thread",
		Users: []*slidetypes.User{
			{ID: "ig-other", InteropMessagingUserFBID: 222, FullName: "Other Person"},
		},
		Viewer: &slidetypes.User{ID: "ig-viewer", InteropMessagingUserFBID: 111, FullName: "Me"},
		SlideMessages: &slidetypes.SlideMessages{
			// Instagram returns newest first.
			Edges: []slidetypes.Node[*slidetypes.Message]{
				{Cursor: "c3", Node: textMessage("m3", 111, "third")},
				{Node: nil},
				{Cursor: "c2", Node: textMessage("m2", 222, "second")},
				{Cursor: "c1", Node: textMessage("m1", 222, "first")},
			},
			PageInfo: slidetypes.PageInfo{HasNextPage: true, EndCursor: "older-cursor"},
		},
	}

	view := newThreadDetailView(thread, 111, 2)
	if len(view.Messages) != 2 {
		t.Fatalf("messages = %d, want 2", len(view.Messages))
	}
	if view.Messages[0].ID != "m2" || view.Messages[0].Text != "second" {
		t.Errorf("first displayed message = %+v, want m2/second", view.Messages[0])
	}
	if view.Messages[1].ID != "m3" || view.Messages[1].Text != "third" {
		t.Errorf("second displayed message = %+v, want m3/third", view.Messages[1])
	}
	if !view.Messages[1].IsFromViewer {
		t.Error("m3 should be marked as from the viewer")
	}
	if view.Messages[0].SenderName != "Other Person" {
		t.Errorf("m2 sender = %q, want Other Person", view.Messages[0].SenderName)
	}
	if !view.HasMoreOlder {
		t.Error("has_more_older = false, want true")
	}
	if view.OldestCursor != "c2" {
		t.Errorf("oldest cursor = %q, want c2 (oldest displayed message)", view.OldestCursor)
	}
}

func TestNewThreadDetailViewReadReceipts(t *testing.T) {
	timestamp := func(milliseconds int64) jsontime.UnixMilliString {
		return jsontime.UnixMilliString{Time: time.UnixMilli(milliseconds)}
	}
	message := func(id string, sender, at int64) *slidetypes.Message {
		return &slidetypes.Message{
			MessageID:   id,
			SenderFBID:  sender,
			TimestampMS: timestamp(at),
		}
	}

	thread := &slidetypes.ThreadInfo{
		ID: "short-id",
		Users: []*slidetypes.User{
			{ID: "ig-other", InteropMessagingUserFBID: 222},
		},
		SlideMessages: &slidetypes.SlideMessages{
			Edges: []slidetypes.Node[*slidetypes.Message]{
				{Node: message("m3", 111, 3000)},
				{Node: message("m2", 222, 2000)},
				{Node: message("m1", 111, 1000)},
			},
		},
		SlideReadReceipts: []slidetypes.ReadReceipt{
			// The viewer's own watermark means the viewer read the thread; it
			// must not turn the viewer's sent messages into "seen".
			{ParticipantFBID: 111, WatermarkTimestampMS: timestamp(5000)},
			{ParticipantFBID: 222, WatermarkTimestampMS: timestamp(2000)},
			{ParticipantFBID: 0, WatermarkTimestampMS: timestamp(9000)},
			{ParticipantFBID: 333},
		},
	}

	view := newThreadDetailView(thread, 111, 20)
	if len(view.ReadReceipts) != 2 {
		t.Fatalf("read receipts = %d, want 2", len(view.ReadReceipts))
	}
	if view.ReadReceipts[0].ParticipantFBID != 111 || view.ReadReceipts[0].WatermarkMS != 5000 {
		t.Errorf("viewer receipt = %+v", view.ReadReceipts[0])
	}
	if !view.ReadReceipts[0].FromViewer {
		t.Error("viewer receipt should be marked as from the viewer")
	}
	if view.ReadReceipts[1].ParticipantFBID != 222 || view.ReadReceipts[1].WatermarkMS != 2000 {
		t.Errorf("other receipt = %+v", view.ReadReceipts[1])
	}
	if view.ReadReceipts[1].FromViewer {
		t.Error("other participant's receipt marked as from the viewer")
	}

	seen := make(map[string]bool, len(view.Messages))
	for _, message := range view.Messages {
		seen[message.ID] = message.Seen
	}
	if !seen["m1"] {
		t.Error("m1 should be seen: other participant read past 1000")
	}
	if seen["m2"] {
		t.Error("m2 is from the other participant and should never be seen")
	}
	if seen["m3"] {
		t.Error("m3 should not be seen: other participant only read to 2000")
	}
}

func TestApplyReadReceiptsIgnoresViewerOnly(t *testing.T) {
	messages := []MessageView{
		{ID: "m1", TimestampMS: 1000, IsFromViewer: true},
	}
	applyReadReceipts(messages, []ReadReceiptView{
		{ParticipantFBID: 111, WatermarkMS: 5000, FromViewer: true},
	})
	if messages[0].Seen {
		t.Error("viewer-only receipt produced a seen tick")
	}
}

func TestNormalizeMessagePageNil(t *testing.T) {
	messages, hasMoreOlder, oldestCursor := normalizeMessagePage(nil, 111, nil, 20)
	if len(messages) != 0 || hasMoreOlder || oldestCursor != "" {
		t.Errorf("normalizeMessagePage(nil) = (%d, %v, %q)", len(messages), hasMoreOlder, oldestCursor)
	}
}

func TestNewMessageViewMedia(t *testing.T) {
	message := textMessage("message-1", 222, "")
	message.Content = slidetypes.MessageContentWrapper{
		TypeName: "SlideMessageImageContent",
		Content: &slidetypes.MessageContentImage{
			Attachments: []*slidetypes.Attachment{
				{
					AttachmentFBID:           "fbid-1",
					AttachmentCDNURL:         "https://cdn.example/full.jpg",
					AttachmentCDNFallbackURL: "https://cdn.example/full-fallback.jpg",
					PreviewCDNURL:            "https://cdn.example/preview.jpg",
					PreviewCDNFallbackURL:    "https://cdn.example/preview-fallback.jpg",
					PreviewWidth:             640,
					PreviewHeight:            480,
				},
			},
		},
	}

	view := newMessageView(message, 111, nil)
	if view.Kind != "image" || view.MediaCount != 1 {
		t.Errorf("kind/media count = %q/%d", view.Kind, view.MediaCount)
	}
	if len(view.Media) != 1 {
		t.Fatalf("media = %d, want 1", len(view.Media))
	}
	media := view.Media[0]
	if media.Type != "image" || media.AttachmentFBID != "fbid-1" {
		t.Errorf("media identity = %+v", media)
	}
	if media.URL != "https://cdn.example/full.jpg" || media.FallbackURL != "https://cdn.example/full-fallback.jpg" {
		t.Errorf("media URLs = %+v", media)
	}
	if media.PreviewURL != "https://cdn.example/preview.jpg" || media.PreviewFallbackURL != "https://cdn.example/preview-fallback.jpg" {
		t.Errorf("preview URLs = %+v", media)
	}
	if media.Width != 640 || media.Height != 480 {
		t.Errorf("dimensions = %dx%d", media.Width, media.Height)
	}
}

func TestNewMessageViewViewOnceMedia(t *testing.T) {
	attachment := &slidetypes.Attachment{
		AttachmentFBID:   "fbid-1",
		AttachmentCDNURL: "https://cdn.example/full.jpg",
		PreviewCDNURL:    "https://cdn.example/preview.jpg",
	}
	ravenImage := func(mode slidetypes.RavenViewMode, attachment *slidetypes.Attachment) *slidetypes.Message {
		message := textMessage("message-1", 222, "")
		message.Content = slidetypes.MessageContentWrapper{
			TypeName: "SlideMessageRavenImageContent",
			Content: &slidetypes.MessageContentRavenImage{
				ViewMode:   mode,
				Attachment: attachment,
			},
		}
		return message
	}

	t.Run("hides the download URL", func(t *testing.T) {
		view := newMessageView(ravenImage(slidetypes.RavenViewModeViewOnce, attachment), 111, nil)
		if view.Kind != "image" || view.MediaCount != 1 || len(view.Media) != 1 {
			t.Fatalf("kind/media = %q/%d/%d, want image/1/1", view.Kind, view.MediaCount, len(view.Media))
		}
		media := view.Media[0]
		if media.Type != "image" || !media.ViewOnce || media.ViewMode != "viewed" {
			t.Errorf("media = %+v, want a viewed image notice", media)
		}
		if media.URL != "" || media.FallbackURL != "" ||
			media.PreviewURL != "" || media.PreviewFallbackURL != "" {
			t.Errorf("view-once media exposed a CDN URL: %+v", media)
		}
	})

	t.Run("keeps the notice when the attachment is missing", func(t *testing.T) {
		// Instagram omits the attachment until the media is opened in the
		// official app; the placeholder still has to reach the client.
		view := newMessageView(ravenImage(slidetypes.RavenViewModeViewOnce, nil), 111, nil)
		if len(view.Media) != 1 {
			t.Fatalf("media = %d, want 1 placeholder", len(view.Media))
		}
		if media := view.Media[0]; !media.ViewOnce || media.Type != "image" || media.ViewMode != "viewed" {
			t.Errorf("placeholder = %+v, want a viewed image notice", media)
		}
	})

	t.Run("marks replayable media", func(t *testing.T) {
		view := newMessageView(ravenImage(slidetypes.RavenViewModeViewTwice, attachment), 111, nil)
		if len(view.Media) != 1 {
			t.Fatalf("media = %d, want 1", len(view.Media))
		}
		if media := view.Media[0]; !media.ViewOnce || media.ViewMode != "replayed" || media.URL != "" {
			t.Errorf("media = %+v, want a replayable notice without a URL", media)
		}
	})

	t.Run("keeps keep-in-chat media", func(t *testing.T) {
		view := newMessageView(ravenImage(slidetypes.RavenViewModeKeepInChat, attachment), 111, nil)
		if len(view.Media) != 1 {
			t.Fatalf("media = %d, want 1", len(view.Media))
		}
		media := view.Media[0]
		if media.ViewOnce || media.ViewMode != "" {
			t.Errorf("media = %+v, want a permanent attachment", media)
		}
		if media.URL != "https://cdn.example/full.jpg" {
			t.Errorf("url = %q, want the CDN URL", media.URL)
		}
	})

	t.Run("marks view-once video", func(t *testing.T) {
		message := textMessage("message-1", 222, "")
		message.Content = slidetypes.MessageContentWrapper{
			TypeName: "SlideMessageRavenVideoContent",
			Content: &slidetypes.MessageContentRavenVideo{
				ViewMode:   slidetypes.RavenViewModeViewOnce,
				Attachment: attachment,
			},
		}
		view := newMessageView(message, 111, nil)
		if view.Kind != "video" || len(view.Media) != 1 {
			t.Fatalf("kind/media = %q/%d, want video/1", view.Kind, len(view.Media))
		}
		if media := view.Media[0]; !media.ViewOnce || media.Type != "video" || media.URL != "" {
			t.Errorf("media = %+v, want a view-once video notice", media)
		}
	})
}

func TestNewMessageViewAnimatedMedia(t *testing.T) {
	message := textMessage("message-1", 222, "")
	message.Content = slidetypes.MessageContentWrapper{
		TypeName: "SlideMessageAnimatedMediaContent",
		Content: &slidetypes.MessageContentAnimatedMedia{
			AnimatedMedia: []*slidetypes.AnimatedAttachment{
				{
					IsSticker:         true,
					AttachmentWebpURL: "https://cdn.example/sticker.webp",
					PreviewCDNURL:     "https://cdn.example/sticker-preview.jpg",
					PreviewWidth:      128,
					PreviewHeight:     128,
				},
			},
		},
	}

	view := newMessageView(message, 111, nil)
	if view.Kind != "sticker" || len(view.Media) != 1 {
		t.Fatalf("kind/media = %q/%d", view.Kind, len(view.Media))
	}
	if view.Media[0].Type != "sticker" || view.Media[0].URL != "https://cdn.example/sticker.webp" {
		t.Errorf("sticker media = %+v", view.Media[0])
	}
}

func TestNewMessageViewStickerContent(t *testing.T) {
	message := textMessage("message-1", 222, "")
	message.Content = slidetypes.MessageContentWrapper{
		TypeName: "SlideMessageStoreStickerContent",
		Content: &slidetypes.MessageContentSticker{
			AltText:       "A bunny sits in a dark corner",
			PreviewURL:    "https://cdn.example/sticker.webp",
			PreviewWidth:  300,
			PreviewHeight: 300,
		},
	}

	view := newMessageView(message, 111, nil)
	if view.Kind != "sticker" || len(view.Media) != 1 {
		t.Fatalf("kind/media = %q/%d, want sticker/1", view.Kind, len(view.Media))
	}
	if view.Text != "" {
		t.Errorf("text = %q, want empty: sticker alt text is not message text", view.Text)
	}
	media := view.Media[0]
	if media.Type != "sticker" || media.URL != "https://cdn.example/sticker.webp" {
		t.Errorf("sticker media = %+v", media)
	}
	if media.PreviewURL != media.URL || media.Width != 300 || media.Height != 300 {
		t.Errorf("sticker preview = %+v", media)
	}
	if media.AltText != "A bunny sits in a dark corner" {
		t.Errorf("sticker alt text = %q", media.AltText)
	}
}

func TestNewMessageViewStickerWithoutPreviewKeepsAltText(t *testing.T) {
	message := textMessage("message-1", 222, "")
	message.Content = slidetypes.MessageContentWrapper{
		TypeName: "SlideMessageStoreStickerContent",
		Content: &slidetypes.MessageContentSticker{
			AltText: "A bunny sits in a dark corner",
		},
	}

	view := newMessageView(message, 111, nil)
	if view.Kind != "sticker" || len(view.Media) != 0 {
		t.Fatalf("kind/media = %q/%d, want sticker/0", view.Kind, len(view.Media))
	}
	if view.Text != "A bunny sits in a dark corner" {
		t.Errorf("text = %q, want the alt text fallback", view.Text)
	}
}

func TestNewMessageViewReactions(t *testing.T) {
	message := textMessage("message-1", 222, "hello")
	message.Reactions = []*slidetypes.Reaction{
		{
			Reaction:            "❤",
			SenderFBID:          111,
			ReactionTimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)},
		},
		{
			Reaction:   "👍",
			SenderFBID: 222,
		},
	}

	view := newMessageView(message, 111, nil)
	if len(view.Reactions) != 2 {
		t.Fatalf("reactions = %d, want 2", len(view.Reactions))
	}
	if view.Reactions[0].Emoji != "❤" || view.Reactions[0].SenderFBID != 111 || view.Reactions[0].TimestampMS != 1_700_000_000_000 {
		t.Errorf("first reaction = %+v", view.Reactions[0])
	}
	if !view.Reactions[0].IsFromViewer {
		t.Errorf("viewer reaction not attributed: %+v", view.Reactions[0])
	}
	if view.Reactions[1].Emoji != "👍" || view.Reactions[1].SenderFBID != 222 {
		t.Errorf("second reaction = %+v", view.Reactions[1])
	}
	if view.Reactions[1].IsFromViewer {
		t.Errorf("other reaction marked as viewer: %+v", view.Reactions[1])
	}
}

func TestNewMessageViewReplySnapshot(t *testing.T) {
	quoted := textMessage("message-1", 222, "the original")
	quoted.Sender = &slidetypes.MessageSender{Name: "Jacob"}

	message := textMessage("message-2", 111, "my reply")
	message.RepliedToMessageID = "message-1"
	message.RepliedToMessage = quoted

	view := newMessageView(message, 111, nil)
	if view.RepliedToMessageID != "message-1" {
		t.Errorf("replied_to_message_id = %q, want message-1", view.RepliedToMessageID)
	}
	reply := view.RepliedToMessage
	if reply == nil {
		t.Fatal("replied_to_message = nil, want the quoted snapshot")
	}
	if reply.ID != "message-1" || reply.Kind != "text" || reply.Text != "the original" {
		t.Errorf("reply identity/content = %+v", reply)
	}
	if reply.SenderName != "Jacob" || reply.SenderFBID != 222 || reply.IsFromViewer {
		t.Errorf("reply sender = %+v", reply)
	}
}

func TestNewReplyViewViewerAndMedia(t *testing.T) {
	quoted := textMessage("message-1", 111, "")
	quoted.Content = slidetypes.MessageContentWrapper{
		TypeName: "SlideMessageImageContent",
		Content: &slidetypes.MessageContentImage{
			Attachments: []*slidetypes.Attachment{{AttachmentFBID: "fbid-1"}},
		},
	}

	reply := newReplyView(quoted, 111, nil)
	if reply == nil {
		t.Fatal("replied_to_message = nil, want the quoted snapshot")
	}
	if !reply.IsFromViewer || reply.Kind != "image" || reply.MediaCount != 1 || reply.Text != "" {
		t.Errorf("reply = %+v", reply)
	}
	if got := newReplyView(nil, 111, nil); got != nil {
		t.Errorf("nil message produced %+v", got)
	}
}

func TestDescribeMessageContent(t *testing.T) {
	tests := []struct {
		name      string
		message   *slidetypes.Message
		wantKind  string
		wantText  string
		wantMedia int
	}{
		{
			name: "text",
			message: &slidetypes.Message{
				Content: slidetypes.MessageContentWrapper{
					TypeName: "SlideMessageText",
					Content:  &slidetypes.MessageContentText{TextBody: "hello"},
				},
			},
			wantKind: "text",
			wantText: "hello",
		},
		{
			name: "admin text",
			message: &slidetypes.Message{
				Content: slidetypes.MessageContentWrapper{
					TypeName: "SlideMessageAdminText",
					Content: &slidetypes.MessageContentAdminText{
						TextFragments: []slidetypes.TextFragment{
							{Plaintext: "joined "},
							{Plaintext: "the chat"},
						},
					},
				},
			},
			wantKind: "admin",
			wantText: "joined the chat",
		},
		{
			name: "reaction action log",
			message: &slidetypes.Message{
				Content: slidetypes.MessageContentWrapper{
					TypeName: "SlideMessageAdminText",
					Content: &slidetypes.MessageContentAdminText{
						IsReactionActionLog: true,
						TextFragments: []slidetypes.TextFragment{
							{Plaintext: "Reacted ❤ to your message"},
						},
					},
				},
			},
			wantKind: "reaction-log",
			wantText: "Reacted ❤ to your message",
		},
		{
			name: "admin snippet fallback",
			message: &slidetypes.Message{
				IGDSnippet: "snippet",
				Content: slidetypes.MessageContentWrapper{
					TypeName: "SlideMessageAdminText",
					Content:  &slidetypes.MessageContentAdminText{},
				},
			},
			wantKind: "admin",
			wantText: "snippet",
		},
		{
			name: "image",
			message: &slidetypes.Message{
				Content: slidetypes.MessageContentWrapper{
					TypeName: "SlideMessageImageContent",
					Content: &slidetypes.MessageContentImage{
						Attachments: []*slidetypes.Attachment{{}, {}},
					},
				},
			},
			wantKind:  "image",
			wantMedia: 2,
		},
		{
			name: "unknown falls back to text body",
			message: &slidetypes.Message{
				TextBody: "fallback",
				Content: slidetypes.MessageContentWrapper{
					TypeName: "SlideMessageFutureContent",
					Content:  slidetypes.MessageContentUnknown{"future": true},
				},
			},
			wantKind: "unknown",
			wantText: "fallback",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			kind, text, media := describeMessageContent(test.message)
			if kind != test.wantKind || text != test.wantText || media != test.wantMedia {
				t.Errorf("describeMessageContent = (%q, %q, %d), want (%q, %q, %d)",
					kind, text, media, test.wantKind, test.wantText, test.wantMedia)
			}
		})
	}
}

func TestNewThreadViewKeepsGroupTitle(t *testing.T) {
	thread := &slidetypes.ThreadInfo{
		ID:            "group-id",
		ThreadTitle:   "Weekend plans",
		ThreadSubtype: slidetypes.ThreadSubtypeGroup,
		IsGroup:       true,
		Users: []*slidetypes.User{
			{ID: "ig-a", InteropMessagingUserFBID: 1, FullName: "Alice"},
			{ID: "ig-b", InteropMessagingUserFBID: 2, FullName: "Bob"},
		},
	}

	view := newThreadView(thread, 999)

	if view.Title != "Weekend plans" {
		t.Errorf("title = %q, want %q", view.Title, "Weekend plans")
	}
	if !view.IsGroup {
		t.Error("is_group = false, want true")
	}
	if len(view.Participants) != 2 {
		t.Errorf("participants = %d, want 2", len(view.Participants))
	}
}

func TestNewThreadViewExposesParticipantAvatars(t *testing.T) {
	thread := &slidetypes.ThreadInfo{
		ID:            "group-id",
		ThreadTitle:   "Weekend plans",
		ThreadSubtype: slidetypes.ThreadSubtypeGroup,
		IsGroup:       true,
		Users: []*slidetypes.User{
			{ID: "ig-me", InteropMessagingUserFBID: 999, FullName: "Me", ProfilePicURL: "https://cdn/me.jpg"},
			{ID: "ig-a", InteropMessagingUserFBID: 1, FullName: "Alice", ProfilePicURL: "https://cdn/a.jpg"},
			{ID: "ig-b", InteropMessagingUserFBID: 2, FullName: "Bob"},
		},
	}

	view := newThreadView(thread, 999)

	if view.AvatarURL != "" {
		t.Errorf("group avatar = %q, want empty without a custom image", view.AvatarURL)
	}
	if view.Participants[0].AvatarURL != "https://cdn/me.jpg" || !view.Participants[0].IsViewer {
		t.Errorf("viewer participant = %+v, want own avatar and is_viewer", view.Participants[0])
	}
	if view.Participants[1].AvatarURL != "https://cdn/a.jpg" || view.Participants[1].IsViewer {
		t.Errorf("other participant = %+v, want avatar and not is_viewer", view.Participants[1])
	}
}

func TestNewThreadViewGroupUsesCustomImage(t *testing.T) {
	thread := &slidetypes.ThreadInfo{
		ID:             "group-id",
		ThreadSubtype:  slidetypes.ThreadSubtypeGroup,
		IsGroup:        true,
		ThreadImageURL: "https://cdn/group.jpg",
	}

	view := newThreadView(thread, 999)

	if view.AvatarURL != "https://cdn/group.jpg" {
		t.Errorf("group avatar = %q, want the custom thread image", view.AvatarURL)
	}
}

func TestNewThreadViewDirectAvatarSkipsViewer(t *testing.T) {
	thread := &slidetypes.ThreadInfo{
		ID:            "dm-id",
		ThreadSubtype: slidetypes.ThreadSubtypeDirect,
		Users: []*slidetypes.User{
			{ID: "ig-other", InteropMessagingUserFBID: 222, FullName: "Other", ProfilePicURL: "https://cdn/other.jpg"},
			{ID: "ig-me", InteropMessagingUserFBID: 111, FullName: "Me", ProfilePicURL: "https://cdn/me.jpg"},
		},
	}

	view := newThreadView(thread, 111)

	if view.AvatarURL != "https://cdn/other.jpg" {
		t.Errorf("direct avatar = %q, want the other participant's picture", view.AvatarURL)
	}
	if view.Participants[1].IsViewer != true || view.Participants[0].IsViewer != false {
		t.Errorf("participants = %+v, want only fbid 111 marked as viewer", view.Participants)
	}
}

func TestNewThreadViewDirectAvatarWithoutViewerID(t *testing.T) {
	thread := &slidetypes.ThreadInfo{
		ID: "dm-id",
		Users: []*slidetypes.User{
			{ID: "ig-other", InteropMessagingUserFBID: 222, ProfilePicURL: "https://cdn/other.jpg"},
		},
	}

	view := newThreadView(thread, 0)

	if view.AvatarURL != "https://cdn/other.jpg" {
		t.Errorf("direct avatar = %q, want the only participant's picture", view.AvatarURL)
	}
	if view.Participants[0].IsViewer {
		t.Error("is_viewer = true without a known viewer FBID")
	}
}

func TestNewThreadViewAddsLastMessagePreview(t *testing.T) {
	timestamp := jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_123_000)}
	thread := &slidetypes.ThreadInfo{
		ID:                      "dm-id",
		LastActivityTimestampMS: timestamp,
		Users: []*slidetypes.User{
			{ID: "ig-other", InteropMessagingUserFBID: 222, Username: "other", FullName: "Other Person"},
			{ID: "ig-me", InteropMessagingUserFBID: 111, FullName: "Me"},
		},
		SlideMessages: &slidetypes.SlideMessages{
			Edges: []slidetypes.Node[*slidetypes.Message]{
				{
					Node: &slidetypes.Message{
						MessageID:          "message-1",
						SenderFBID:         222,
						TimestampMS:        timestamp,
						Content:            slidetypes.MessageContentWrapper{Content: &slidetypes.MessageContentText{TextBody: "see you soon"}},
						OfflineThreadingID: "otid-1",
					},
				},
			},
		},
	}

	view := newThreadView(thread, 111)

	preview := view.LastMessage
	if preview == nil {
		t.Fatal("last_message = nil, want the inbox stub")
	}
	if preview.ID != "message-1" || preview.Text != "see you soon" || preview.Kind != "text" {
		t.Errorf("last_message = %+v, want message-1/text/see you soon", preview)
	}
	if preview.IsFromViewer {
		t.Error("is_from_viewer = true for the other participant's message")
	}
	if preview.SenderName != "Other Person" {
		t.Errorf("sender_name = %q, want the participant's full name", preview.SenderName)
	}
	if preview.TimestampMS != timestamp.UnixMilli() {
		t.Errorf("timestamp_ms = %d, want %d", preview.TimestampMS, timestamp.UnixMilli())
	}
}

func TestNewThreadViewPrefersNewestLastMessage(t *testing.T) {
	older := jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)}
	newer := jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_500_000)}
	thread := &slidetypes.ThreadInfo{
		ID: "dm-id",
		SlideMessages: &slidetypes.SlideMessages{
			Edges: []slidetypes.Node[*slidetypes.Message]{
				{Node: &slidetypes.Message{MessageID: "old", SenderFBID: 111, TimestampMS: older}},
				{Node: &slidetypes.Message{MessageID: "new", SenderFBID: 111, TimestampMS: newer, IGDSnippet: "latest"}},
			},
		},
	}

	view := newThreadView(thread, 111)

	if view.LastMessage == nil || view.LastMessage.ID != "new" {
		t.Fatalf("last_message = %+v, want the newest edge", view.LastMessage)
	}
	if !view.LastMessage.IsFromViewer {
		t.Error("is_from_viewer = false for the viewer's own message")
	}
	if view.LastMessage.Snippet != "latest" {
		t.Errorf("snippet = %q, want the inbox snippet", view.LastMessage.Snippet)
	}
}

func TestNewThreadViewWithoutStubsHasNoLastMessage(t *testing.T) {
	view := newThreadView(&slidetypes.ThreadInfo{ID: "dm-id"}, 111)

	if view.LastMessage != nil {
		t.Errorf("last_message = %+v, want nil without slide_messages", view.LastMessage)
	}
}

func TestNormalizeWaveform(t *testing.T) {
	if got := normalizeWaveform(nil); got != nil {
		t.Errorf("normalizeWaveform(nil) = %v, want nil", got)
	}

	normalized := normalizeWaveform([]any{0.0, 0.5, 1.0})
	if len(normalized) != 3 || normalized[1] != 0.5 || normalized[2] != 1 {
		t.Errorf("normalized = %v", normalized)
	}

	scaled := normalizeWaveform([]any{0.0, 127.5, 255.0})
	if len(scaled) != 3 || scaled[1] != 0.5 || scaled[2] != 1 {
		t.Errorf("scaled = %v", scaled)
	}

	wrapped := normalizeWaveform([]any{map[string]any{"amplitude": 0.25}, "skip", -1.0})
	if len(wrapped) != 2 || wrapped[0] != 0.25 || wrapped[1] != 0 {
		t.Errorf("wrapped = %v", wrapped)
	}
}

func TestNewMessageViewNormalizesSharedReel(t *testing.T) {
	timestamp := jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)}
	message := &slidetypes.Message{
		MessageID:   "mid.reel",
		SenderFBID:  222,
		TimestampMS: timestamp,
		Content: slidetypes.MessageContentWrapper{
			TypeName: "SlideMessageXMAContent",
			Content: &slidetypes.MessageContentXMA{
				XMATextBody: "watch this",
				XMA: &slidetypes.XMAContent{
					Typename:  "XDTReelShare",
					TargetID:  "123456789",
					TargetURL: "https://www.instagram.com/reel/ABC_123/?igsh=xyz",
					PreviewImage: &slidetypes.XMAPreviewImage{
						URL:         "https://cdn.example.invalid/reel.jpg",
						FallbackURL: "https://cdn.example.invalid/reel-fallback.jpg",
						Width:       1080,
						Height:      1920,
					},
				},
			},
		},
	}

	view := newMessageView(message, 111, nil)
	if view.Kind != "reel" || view.MediaCount != 1 {
		t.Errorf("kind/media_count = %q/%d, want reel/1", view.Kind, view.MediaCount)
	}
	if view.Text != "watch this" {
		t.Errorf("text = %q, want the XMA body", view.Text)
	}
	if len(view.Media) != 1 {
		t.Fatalf("media = %+v, want one reel", view.Media)
	}
	media := view.Media[0]
	if media.Type != "reel" || media.TargetID != "123456789" || media.Shortcode != "ABC_123" {
		t.Errorf("reel media = %+v, want type reel with target ID and shortcode", media)
	}
	if media.PreviewURL != "https://cdn.example.invalid/reel.jpg" ||
		media.PreviewFallbackURL != "https://cdn.example.invalid/reel-fallback.jpg" ||
		media.Width != 1080 || media.Height != 1920 {
		t.Errorf("reel preview = %+v", media)
	}
}

func TestNewMessageViewNormalizesSharedPostAsLink(t *testing.T) {
	timestamp := jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)}
	message := &slidetypes.Message{
		MessageID:   "mid.post",
		SenderFBID:  222,
		TimestampMS: timestamp,
		Content: slidetypes.MessageContentWrapper{
			TypeName: "SlideMessageXMAContent",
			Content: &slidetypes.MessageContentXMA{
				XMA: &slidetypes.XMAContent{
					Typename:  "XDTMediaShare",
					TargetID:  "987654321",
					TargetURL: "https://www.instagram.com/p/POST123/?igsh=xyz",
					PreviewImage: &slidetypes.XMAPreviewImage{
						URL:    "https://cdn.example.invalid/post.jpg",
						Width:  1080,
						Height: 1350,
					},
				},
			},
		},
	}

	view := newMessageView(message, 111, nil)
	if view.Kind != "post" || view.MediaCount != 1 {
		t.Errorf("kind/media_count = %q/%d, want post/1", view.Kind, view.MediaCount)
	}
	if len(view.Media) != 1 {
		t.Fatalf("media = %+v, want one link", view.Media)
	}
	media := view.Media[0]
	if media.Type != "link" || media.URL != "https://www.instagram.com/p/POST123/?igsh=xyz" {
		t.Errorf("link media = %+v", media)
	}
	if media.PreviewURL != "https://cdn.example.invalid/post.jpg" {
		t.Errorf("preview = %q, want the XMA preview", media.PreviewURL)
	}
}

func TestNewMessageViewNormalizesStoryShareAsLink(t *testing.T) {
	message := &slidetypes.Message{
		MessageID:  "mid.story",
		SenderFBID: 222,
		Content: slidetypes.MessageContentWrapper{
			TypeName: "SlideMessageXMAContent",
			Content: &slidetypes.MessageContentXMA{
				XMA: &slidetypes.XMAContent{
					Typename:  "XDTStoryShare",
					TargetURL: "https://www.instagram.com/stories/someone/1234567890/?reel_id=1234567890",
				},
			},
		},
	}

	view := newMessageView(message, 111, nil)
	if view.Kind != "story" || view.MediaCount != 1 {
		t.Errorf("kind/media_count = %q/%d, want story/1", view.Kind, view.MediaCount)
	}
	if len(view.Media) != 1 || view.Media[0].Type != "link" {
		t.Fatalf("media = %+v, want one link", view.Media)
	}
}

func TestXMAReelWithoutTargetIDFallsBackToLink(t *testing.T) {
	message := &slidetypes.Message{
		MessageID:  "mid.reel-no-id",
		SenderFBID: 222,
		Content: slidetypes.MessageContentWrapper{
			TypeName: "SlideMessageXMAContent",
			Content: &slidetypes.MessageContentXMA{
				XMA: &slidetypes.XMAContent{
					Typename:  "XDTReelShare",
					TargetURL: "https://www.instagram.com/reel/ABC_123/",
				},
			},
		},
	}

	view := newMessageView(message, 111, nil)
	if view.Kind != "reel" || view.MediaCount != 1 {
		t.Errorf("kind/media_count = %q/%d, want reel/1", view.Kind, view.MediaCount)
	}
	if len(view.Media) != 1 || view.Media[0].Type != "link" || view.Media[0].URL == "" {
		t.Fatalf("media = %+v, want a link fallback", view.Media)
	}
}

func TestExpiredStoryPlaceholderShowsText(t *testing.T) {
	message := &slidetypes.Message{
		MessageID:  "mid.story-expired",
		SenderFBID: 222,
		Content: slidetypes.MessageContentWrapper{
			TypeName: "SlideMessageXMAContent",
			Content: &slidetypes.MessageContentXMA{
				XMA: &slidetypes.XMAContent{
					Typename:        "SlideMessageExpiredPlaceholderXMA",
					EyebrowText:     "Shared matt bernstein's story",
					CaptionBodyText: "Story unavailable",
				},
			},
		},
	}

	view := newMessageView(message, 111, nil)
	if view.Kind != "story-unavailable" || view.MediaCount != 0 {
		t.Errorf("kind/media_count = %q/%d, want story-unavailable/0", view.Kind, view.MediaCount)
	}
	if len(view.Media) != 0 {
		t.Errorf("media = %+v, want no media for an expired story", view.Media)
	}
	want := "Shared matt bernstein's story. Story unavailable."
	if view.Text != want {
		t.Errorf("text = %q, want %q", view.Text, want)
	}
}

func TestXMAWithoutTargetURLUsesSnippet(t *testing.T) {
	message := &slidetypes.Message{
		MessageID:  "mid.empty",
		SenderFBID: 222,
		IGDSnippet: "Sent a post",
		Content: slidetypes.MessageContentWrapper{
			TypeName: "SlideMessageXMAContent",
			Content: &slidetypes.MessageContentXMA{
				XMA: &slidetypes.XMAContent{Typename: "XDTMediaShare"},
			},
		},
	}

	view := newMessageView(message, 111, nil)
	if view.Kind != "link" || view.MediaCount != 0 {
		t.Errorf("kind/media_count = %q/%d, want link/0", view.Kind, view.MediaCount)
	}
	if view.Text != "Sent a post" {
		t.Errorf("text = %q, want the inbox snippet fallback", view.Text)
	}
}

func TestSharedReelTarget(t *testing.T) {
	cases := []struct {
		name          string
		xma           *slidetypes.XMAContent
		wantOK        bool
		wantTargetID  string
		wantShortcode string
	}{
		{
			name:          "reel URL",
			xma:           &slidetypes.XMAContent{TargetID: "1", TargetURL: "https://www.instagram.com/reel/ABC_123/?igsh=xyz"},
			wantOK:        true,
			wantTargetID:  "1",
			wantShortcode: "ABC_123",
		},
		{
			name:          "reels URL",
			xma:           &slidetypes.XMAContent{TargetID: "2", TargetURL: "https://www.instagram.com/reels/XYZ-9/"},
			wantOK:        true,
			wantTargetID:  "2",
			wantShortcode: "XYZ-9",
		},
		{
			name:          "redirect URL",
			xma:           &slidetypes.XMAContent{TargetID: "3", TargetURL: "https://l.instagram.com/?u=https%3A%2F%2Fwww.instagram.com%2Freel%2FREDIRECT%2F"},
			wantOK:        true,
			wantTargetID:  "3",
			wantShortcode: "REDIRECT",
		},
		{
			name:          "typename fallback without URL",
			xma:           &slidetypes.XMAContent{TargetID: "4", Typename: "XDTReelShare"},
			wantOK:        true,
			wantTargetID:  "4",
			wantShortcode: "",
		},
		{
			name:   "post URL",
			xma:    &slidetypes.XMAContent{TargetID: "5", TargetURL: "https://www.instagram.com/p/POST123/"},
			wantOK: false,
		},
		{
			name:   "story URL",
			xma:    &slidetypes.XMAContent{TargetURL: "https://www.instagram.com/stories/someone/123/"},
			wantOK: false,
		},
		{
			name:   "missing target ID",
			xma:    &slidetypes.XMAContent{TargetURL: "https://www.instagram.com/reel/ABC/"},
			wantOK: false,
		},
		{
			name:   "non-numeric target ID",
			xma:    &slidetypes.XMAContent{TargetID: "abc", TargetURL: "https://www.instagram.com/reel/ABC/"},
			wantOK: false,
		},
		{
			name:   "nil XMA",
			xma:    nil,
			wantOK: false,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			targetID, shortcode, ok := sharedReelTarget(tc.xma)
			if ok != tc.wantOK {
				t.Fatalf("ok = %v, want %v", ok, tc.wantOK)
			}
			if targetID != tc.wantTargetID || shortcode != tc.wantShortcode {
				t.Errorf("target/shortcode = %q/%q, want %q/%q", targetID, shortcode, tc.wantTargetID, tc.wantShortcode)
			}
		})
	}
}

func TestUnknownMessageUsesSnippet(t *testing.T) {
	message := &slidetypes.Message{
		MessageID:  "mid.unknown",
		SenderFBID: 222,
		IGDSnippet: "Sent a post",
		Content: slidetypes.MessageContentWrapper{
			TypeName: "SlideMessageUnknownContent",
			Content:  slidetypes.MessageContentUnknown{},
		},
	}

	view := newMessageView(message, 111, nil)
	if view.Kind != "unknown" {
		t.Errorf("kind = %q, want unknown", view.Kind)
	}
	if view.Text != "Sent a post" {
		t.Errorf("text = %q, want the inbox snippet fallback", view.Text)
	}
}
