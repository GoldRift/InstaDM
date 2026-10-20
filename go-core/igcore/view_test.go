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
			Fbid: "999",
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
								{ID: "ig-other", InteropMessagingUserFBID: 222, Username: "other", FullName: "Other Person"},
								{ID: "ig-viewer", InteropMessagingUserFBID: 111, Username: "me", FullName: "Me"},
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
