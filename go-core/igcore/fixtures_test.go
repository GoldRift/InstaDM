package igcore

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func TestRedactedFixturesUnmarshal(t *testing.T) {
	mailboxData, err := os.ReadFile(filepath.Join("..", "fixtures", "mailbox.redacted.json"))
	if err != nil {
		t.Fatalf("read mailbox fixture: %v", err)
	}
	var mailbox MailboxView
	if err := json.Unmarshal(mailboxData, &mailbox); err != nil {
		t.Fatalf("unmarshal mailbox fixture: %v", err)
	}
	if mailbox.ProtocolVersion == "" {
		t.Error("mailbox fixture has no protocol_version")
	}
	if len(mailbox.Threads) != 1 {
		t.Fatalf("mailbox fixture threads = %d, want 1", len(mailbox.Threads))
	}
	if mailbox.Threads[0].ID == "" || mailbox.Threads[0].Title == "" {
		t.Error("mailbox fixture thread is missing ID or title")
	}
	if mailbox.Threads[0].AvatarURL == "" ||
		len(mailbox.Threads[0].Participants) == 0 ||
		mailbox.Threads[0].Participants[0].AvatarURL == "" {
		t.Error("mailbox fixture thread is missing avatar metadata")
	}
	preview := mailbox.Threads[0].LastMessage
	if preview == nil {
		t.Fatal("mailbox fixture thread is missing last_message")
	}
	if preview.ID == "" || preview.Kind != "text" || preview.Text == "" {
		t.Errorf("mailbox fixture last_message = %+v, want a text preview", preview)
	}
	if preview.TimestampMS == 0 || preview.IsFromViewer {
		t.Errorf("mailbox fixture last_message = %+v, want an incoming message with a timestamp", preview)
	}

	threadData, err := os.ReadFile(filepath.Join("..", "fixtures", "thread.redacted.json"))
	if err != nil {
		t.Fatalf("read thread fixture: %v", err)
	}
	var thread ThreadDetailView
	if err := json.Unmarshal(threadData, &thread); err != nil {
		t.Fatalf("unmarshal thread fixture: %v", err)
	}
	if thread.ProtocolVersion == "" {
		t.Error("thread fixture has no protocol_version")
	}
	if len(thread.Messages) != 3 {
		t.Fatalf("thread fixture messages = %d, want 3", len(thread.Messages))
	}
	if thread.Messages[0].Kind != "text" || thread.Messages[2].Kind != "image" {
		t.Errorf("thread fixture kinds = %q, %q, want text, image", thread.Messages[0].Kind, thread.Messages[2].Kind)
	}
	if len(thread.ReadReceipts) != 2 {
		t.Errorf("thread fixture read receipts = %d, want 2", len(thread.ReadReceipts))
	}
	if !thread.Messages[1].Seen {
		t.Error("thread fixture message-2 should be seen")
	}
	if thread.Messages[0].Seen || thread.Messages[2].Seen {
		t.Error("thread fixture only the viewer's read message should be seen")
	}
	if !thread.HasMoreOlder || thread.OldestCursor == "" {
		t.Error("thread fixture is missing pagination metadata")
	}
	if thread.Thread.AvatarURL == "" || thread.Thread.Participants[0].AvatarURL == "" {
		t.Error("thread fixture is missing avatar metadata")
	}
}
