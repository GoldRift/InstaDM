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

func TestNormalizeMessagePageNil(t *testing.T) {
	messages, hasMoreOlder, oldestCursor := normalizeMessagePage(nil, 111, nil, 20)
	if len(messages) != 0 || hasMoreOlder || oldestCursor != "" {
		t.Errorf("normalizeMessagePage(nil) = (%d, %v, %q)", len(messages), hasMoreOlder, oldestCursor)
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
