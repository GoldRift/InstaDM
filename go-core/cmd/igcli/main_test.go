package main

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/goldrift/instadm/go-core/igcore"
)

func TestPrintMailbox(t *testing.T) {
	view := &igcore.MailboxView{
		ProtocolVersion: "test",
		Viewer: igcore.ViewerView{
			IGID:     "ig-viewer",
			Username: "me",
			Name:     "Me",
			FBID:     111,
		},
		SeqID:       42,
		HasNextPage: true,
		Threads: []igcore.ThreadView{
			{
				ID:             "short-id",
				ThreadFBID:     "thread-fbid",
				Title:          "Test Thread",
				LastActivityMS: 1_700_000_000_000,
				Folder:         "PRIMARY",
				MarkedAsUnread: true,
			},
		},
	}

	var output bytes.Buffer
	printMailbox(&output, view)
	rendered := output.String()

	for _, want := range []string{
		"Logged in as Me (@me)",
		"seq_id: 42",
		"threads: 1 (more available)",
		"short-id",
		"Test Thread",
		"PRIMARY",
		"unread",
	} {
		if !strings.Contains(rendered, want) {
			t.Errorf("output does not contain %q:\n%s", want, rendered)
		}
	}
}

func TestPrintThread(t *testing.T) {
	detail := &igcore.ThreadDetailView{
		ProtocolVersion: "test",
		Thread: igcore.ThreadView{
			ID:         "short-id",
			ThreadFBID: "thread-fbid",
			Title:      "Test Thread",
			Folder:     "PRIMARY",
			Subtype:    "IG_ONLY_ONE_TO_ONE",
			Participants: []igcore.ParticipantView{
				{IGID: "ig-other", FBID: 222, Username: "other", Name: "Other Person"},
			},
		},
		Messages: []igcore.MessageView{
			{
				ID:          "m1",
				SenderFBID:  222,
				SenderName:  "Other Person",
				TimestampMS: 1_700_000_000_000,
				Kind:        "text",
				Text:        "hello",
			},
			{
				ID:           "m2",
				SenderFBID:   111,
				IsFromViewer: true,
				TimestampMS:  1_700_000_001_000,
				Kind:         "image",
				MediaCount:   1,
			},
		},
		HasMoreOlder: true,
	}

	var output bytes.Buffer
	printThread(&output, detail)
	rendered := output.String()

	for _, want := range []string{
		"Thread: Test Thread",
		"ID: short-id",
		"Participants: Other Person",
		"Messages: 2 (older messages available)",
		"hello",
		"me",
		"image",
	} {
		if !strings.Contains(rendered, want) {
			t.Errorf("output does not contain %q:\n%s", want, rendered)
		}
	}
}

func TestDisplayMessageText(t *testing.T) {
	tests := []struct {
		name    string
		message igcore.MessageView
		want    string
	}{
		{name: "text", message: igcore.MessageView{Text: "hi"}, want: "hi"},
		{name: "image", message: igcore.MessageView{Kind: "image", MediaCount: 2}, want: "[image x2]"},
		{name: "sticker", message: igcore.MessageView{Kind: "sticker"}, want: "[sticker]"},
		{name: "unknown", message: igcore.MessageView{Kind: "unknown", ContentType: "SlideMessageFutureContent"}, want: "[SlideMessageFutureContent]"},
		{name: "empty", message: igcore.MessageView{}, want: "(empty)"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := displayMessageText(test.message); got != test.want {
				t.Errorf("displayMessageText = %q, want %q", got, test.want)
			}
		})
	}
}

func TestSingleLine(t *testing.T) {
	if got := singleLine("a\nb\rc\td"); got != "a b c d" {
		t.Errorf("singleLine = %q, want %q", got, "a b c d")
	}
}

func TestTruncate(t *testing.T) {
	if got := truncate("short", 10); got != "short" {
		t.Errorf("truncate short = %q", got)
	}
	if got := truncate("abcdefghij", 5); got != "abcd…" {
		t.Errorf("truncate long = %q, want %q", got, "abcd…")
	}
}

func TestPrintSendResult(t *testing.T) {
	result := &igcore.SendResult{
		ProtocolVersion:    "test",
		ThreadID:           "thread-1",
		OfflineThreadingID: "otid-1",
		MessageID:          "mid.$1",
		ID:                 "id-1",
		TimestampMS:        1_700_000_000_000,
		Text:               "hello",
	}

	var output bytes.Buffer
	printSendResult(&output, result)
	rendered := output.String()
	for _, want := range []string{"Sent message", "thread-1", "mid.$1", "otid-1", "hello"} {
		if !strings.Contains(rendered, want) {
			t.Errorf("output does not contain %q:\n%s", want, rendered)
		}
	}
}

func TestPrintThreadPage(t *testing.T) {
	page := &igcore.ThreadPageView{
		ProtocolVersion: "test",
		ThreadID:        "thread-1",
		Messages: []igcore.MessageView{
			{
				ID:          "message-1",
				SenderFBID:  222,
				SenderName:  "Other Person",
				TimestampMS: 1_700_000_000_000,
				Kind:        "text",
				Text:        "an older message",
			},
		},
		HasMoreOlder: true,
		OldestCursor: "cursor-2",
	}

	var output bytes.Buffer
	printThreadPage(&output, page)
	rendered := output.String()
	for _, want := range []string{"Thread: thread-1 (older page)", "Messages: 1", "an older message", "Next cursor: cursor-2"} {
		if !strings.Contains(rendered, want) {
			t.Errorf("output does not contain %q:\n%s", want, rendered)
		}
	}
}

func TestPrintUnsendResult(t *testing.T) {
	result := &igcore.UnsendResult{
		ProtocolVersion: "test",
		ThreadID:        "thread-1",
		LongThreadID:    "long-thread-1",
		MessageID:       "message-1",
		Unsent:          true,
	}

	var output bytes.Buffer
	printUnsendResult(&output, result)
	rendered := output.String()
	for _, want := range []string{"Unsent message", "thread-1", "long-thread-1", "message-1", "unsent: true"} {
		if !strings.Contains(rendered, want) {
			t.Errorf("output does not contain %q:\n%s", want, rendered)
		}
	}
}

func TestRunUnsendValidation(t *testing.T) {
	const fakeCookies = "sessionid=fake; csrftoken=fake; ds_user_id=1"

	t.Run("missing arguments", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runUnsend(nil, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "usage: igcli unsend") {
			t.Errorf("error = %v, want usage error", err)
		}
	})

	for _, test := range []struct {
		name string
		args []string
		want string
	}{
		{name: "empty thread", args: []string{"", "message-1", "--cookie-header", fakeCookies}, want: "thread ID is empty"},
		{name: "empty message", args: []string{"thread-1", "", "--cookie-header", fakeCookies}, want: "message ID is empty"},
	} {
		t.Run(test.name, func(t *testing.T) {
			var stdout, stderr bytes.Buffer
			err := runUnsend(test.args, &stdout, &stderr)
			if err == nil || !strings.Contains(err.Error(), test.want) {
				t.Errorf("error = %v, want %q", err, test.want)
			}
		})
	}
}

func TestPrintEditResult(t *testing.T) {
	result := &igcore.EditResult{
		ProtocolVersion:    "test",
		ThreadID:           "thread-1",
		MessageID:          "message-1",
		OfflineThreadingID: "otid-1",
		Text:               "new text",
		TimestampMS:        1_700_000_000_000,
	}

	var output bytes.Buffer
	printEditResult(&output, result)
	rendered := output.String()
	for _, want := range []string{"Edited message", "thread-1", "message-1", "otid-1", "new text"} {
		if !strings.Contains(rendered, want) {
			t.Errorf("output does not contain %q:\n%s", want, rendered)
		}
	}
}

func TestRunEditValidation(t *testing.T) {
	const fakeCookies = "sessionid=fake; csrftoken=fake; ds_user_id=1"

	t.Run("missing arguments", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runEdit(nil, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "usage: igcli edit") {
			t.Errorf("error = %v, want usage error", err)
		}
	})

	for _, test := range []struct {
		name string
		args []string
		want string
	}{
		{name: "empty thread", args: []string{"", "message-1", "text", "--cookie-header", fakeCookies}, want: "thread ID is empty"},
		{name: "empty message", args: []string{"thread-1", "", "text", "--cookie-header", fakeCookies}, want: "message ID is empty"},
		{name: "empty text", args: []string{"thread-1", "message-1", "", "--cookie-header", fakeCookies}, want: "text is required"},
		{name: "invalid otid", args: []string{"thread-1", "message-1", "text", "--otid", "abc", "--cookie-header", fakeCookies}, want: "--otid must be"},
	} {
		t.Run(test.name, func(t *testing.T) {
			var stdout, stderr bytes.Buffer
			err := runEdit(test.args, &stdout, &stderr)
			if err == nil || !strings.Contains(err.Error(), test.want) {
				t.Errorf("error = %v, want %q", err, test.want)
			}
		})
	}
}

func TestPrintMediaList(t *testing.T) {
	message := &igcore.MessageView{
		ID:         "message-1",
		Kind:       "image",
		MediaCount: 2,
		Media: []igcore.MediaView{
			{
				Type:           "image",
				AttachmentFBID: "fbid-1",
				URL:            "https://cdn.example/full.jpg",
				PreviewURL:     "https://cdn.example/preview.jpg",
				Width:          640,
				Height:         480,
			},
			{Type: "video", DurationMS: 12_000},
		},
	}

	var output bytes.Buffer
	printMediaList(&output, "thread-1", message)
	rendered := output.String()
	for _, want := range []string{"Thread: thread-1", "Message: message-1", "Media: 2", "#0", "image", "640x480", "fbid-1", "url:", "#1", "video", "12000 ms", "Tip:"} {
		if !strings.Contains(rendered, want) {
			t.Errorf("output does not contain %q:\n%s", want, rendered)
		}
	}
}

func TestPrintDownloadResult(t *testing.T) {
	result := &igcore.DownloadResult{
		ProtocolVersion: "test",
		ThreadID:        "thread-1",
		MessageID:       "message-1",
		PartIndex:       0,
		Type:            "image",
		AttachmentFBID:  "fbid-1",
		MimeType:        "image/png",
		Path:            "/tmp/photo.png",
		SizeBytes:       1234,
	}

	var output bytes.Buffer
	printDownloadResult(&output, result)
	rendered := output.String()
	for _, want := range []string{"Downloaded media", "thread-1", "message-1", "part: 0 (image)", "fbid-1", "/tmp/photo.png", "image/png", "1234 bytes"} {
		if !strings.Contains(rendered, want) {
			t.Errorf("output does not contain %q:\n%s", want, rendered)
		}
	}
}

func TestRunMediaValidation(t *testing.T) {
	const fakeCookies = "sessionid=fake; csrftoken=fake; ds_user_id=1"

	t.Run("missing arguments", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runMedia(nil, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "usage: igcli media") {
			t.Errorf("error = %v, want usage error", err)
		}
	})

	saveDir := t.TempDir()
	for _, test := range []struct {
		name string
		args []string
		want string
	}{
		{name: "empty thread", args: []string{"", "message-1", "--cookie-header", fakeCookies}, want: "thread ID is empty"},
		{name: "empty message", args: []string{"thread-1", "", "--cookie-header", fakeCookies}, want: "message ID is empty"},
		{name: "negative part", args: []string{"thread-1", "message-1", "--part", "-1", "--cookie-header", fakeCookies}, want: "--part must be zero or greater"},
		{name: "save is a directory", args: []string{"thread-1", "message-1", "--save", saveDir, "--cookie-header", fakeCookies}, want: "--save must be a file path"},
	} {
		t.Run(test.name, func(t *testing.T) {
			var stdout, stderr bytes.Buffer
			err := runMedia(test.args, &stdout, &stderr)
			if err == nil || !strings.Contains(err.Error(), test.want) {
				t.Errorf("error = %v, want %q", err, test.want)
			}
		})
	}
}

func TestPrintMediaSendResult(t *testing.T) {
	result := &igcore.MediaSendResult{
		ProtocolVersion:    "test",
		ThreadID:           "thread-1",
		OfflineThreadingID: "otid-1",
		AttachmentFBID:     "attachment-1",
		Filename:           "photo.png",
		MimeType:           "image/png",
		SizeBytes:          1234,
		MessageID:          "message-1",
		TimestampMS:        1_700_000_000_000,
	}

	var output bytes.Buffer
	printMediaSendResult(&output, result)
	rendered := output.String()
	for _, want := range []string{"Sent media", "thread-1", "message-1", "attachment-1", "photo.png", "image/png", "1234 bytes"} {
		if !strings.Contains(rendered, want) {
			t.Errorf("output does not contain %q:\n%s", want, rendered)
		}
	}
}

func TestRunSendMediaValidation(t *testing.T) {
	const fakeCookies = "sessionid=fake; csrftoken=fake; ds_user_id=1"

	t.Run("missing arguments", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runSendMedia(nil, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "usage: igcli send-media") {
			t.Errorf("error = %v, want usage error", err)
		}
	})

	for _, test := range []struct {
		name string
		args []string
		want string
	}{
		{name: "empty thread", args: []string{"", "photo.png", "--cookie-header", fakeCookies}, want: "thread ID is empty"},
		{name: "empty file", args: []string{"thread-1", "", "--cookie-header", fakeCookies}, want: "file path is empty"},
		{name: "missing file", args: []string{"thread-1", "/definitely/missing.png", "--cookie-header", fakeCookies}, want: "media file:"},
	} {
		t.Run(test.name, func(t *testing.T) {
			var stdout, stderr bytes.Buffer
			err := runSendMedia(test.args, &stdout, &stderr)
			if err == nil || !strings.Contains(err.Error(), test.want) {
				t.Errorf("error = %v, want %q", err, test.want)
			}
		})
	}
}

func TestPrintReactionResult(t *testing.T) {
	added := &igcore.ReactionResult{
		ProtocolVersion: "test",
		ThreadID:        "thread-1",
		MessageID:       "message-1",
		Emoji:           "❤",
		TimestampMS:     1_700_000_000_000,
	}
	var addedOutput bytes.Buffer
	printReactionResult(&addedOutput, added)
	for _, want := range []string{"Reaction added", "thread-1", "message-1", "❤"} {
		if !strings.Contains(addedOutput.String(), want) {
			t.Errorf("added output does not contain %q:\n%s", want, addedOutput.String())
		}
	}

	removed := &igcore.ReactionResult{
		ProtocolVersion: "test",
		ThreadID:        "thread-1",
		MessageID:       "message-1",
		Emoji:           "❤",
		Removed:         true,
	}
	var removedOutput bytes.Buffer
	printReactionResult(&removedOutput, removed)
	if !strings.Contains(removedOutput.String(), "Reaction removed") {
		t.Errorf("removed output = %q", removedOutput.String())
	}
}

func TestRunReactValidation(t *testing.T) {
	const fakeCookies = "sessionid=fake; csrftoken=fake; ds_user_id=1"

	t.Run("missing arguments", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runReact(nil, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "usage: igcli react") {
			t.Errorf("error = %v, want usage error", err)
		}
	})

	for _, test := range []struct {
		name string
		args []string
		want string
	}{
		{name: "empty thread", args: []string{"", "message-1", "❤", "--cookie-header", fakeCookies}, want: "thread ID is empty"},
		{name: "empty message", args: []string{"thread-1", "", "❤", "--cookie-header", fakeCookies}, want: "message ID is empty"},
		{name: "empty emoji", args: []string{"thread-1", "message-1", "", "--cookie-header", fakeCookies}, want: "emoji is empty"},
	} {
		t.Run(test.name, func(t *testing.T) {
			var stdout, stderr bytes.Buffer
			err := runReact(test.args, &stdout, &stderr)
			if err == nil || !strings.Contains(err.Error(), test.want) {
				t.Errorf("error = %v, want %q", err, test.want)
			}
		})
	}
}

func TestPrintMarkReadResult(t *testing.T) {
	result := &igcore.MarkReadResult{
		ProtocolVersion: "test",
		ThreadID:        "short-thread",
		LongThreadID:    "long-thread",
		MessageID:       "message-1",
		TimestampMS:     1_700_000_000_000,
		Validated:       true,
	}

	var output bytes.Buffer
	printMarkReadResult(&output, result)
	rendered := output.String()
	for _, want := range []string{"Marked read", "short-thread", "long-thread", "message-1", "validated: true"} {
		if !strings.Contains(rendered, want) {
			t.Errorf("output does not contain %q:\n%s", want, rendered)
		}
	}
}

func TestRunMarkReadValidation(t *testing.T) {
	t.Run("missing thread ID", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runMarkRead(nil, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "usage: igcli mark-read") {
			t.Errorf("error = %v, want usage error", err)
		}
	})

	t.Run("blank thread ID", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runMarkRead([]string{"", "--cookie-header", "sessionid=fake; csrftoken=fake; ds_user_id=1"}, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "thread ID is empty") {
			t.Errorf("error = %v, want empty thread ID error", err)
		}
	})
}

func TestFormatEvent(t *testing.T) {
	message := &igcore.MessageView{
		ID:          "message-1",
		SenderFBID:  222,
		SenderName:  "Other Person",
		TimestampMS: 1_700_000_000_000,
		Kind:        "text",
		Text:        "hello",
	}

	tests := []struct {
		name  string
		event igcore.Event
		want  []string
	}{
		{
			name:  "connected",
			event: igcore.Event{Type: "connected", SeqID: 42},
			want:  []string{"connected (seq_id=42)"},
		},
		{
			name:  "new message",
			event: igcore.Event{Type: "message.new", ThreadID: "thread-1", Message: message},
			want:  []string{"[thread-1]", "Other Person", "hello", "[text]"},
		},
		{
			name:  "delete",
			event: igcore.Event{Type: "message.delete", ThreadID: "thread-1", Message: &igcore.MessageView{ID: "message-1"}},
			want:  []string{"deleted message-1"},
		},
		{
			name:  "reaction",
			event: igcore.Event{Type: "reaction.add", ThreadID: "thread-1", Reaction: "❤", Message: &igcore.MessageView{ID: "message-1"}},
			want:  []string{"reaction added ❤", "message-1"},
		},
		{
			name:  "unknown",
			event: igcore.Event{Type: "unknown", ThreadID: "thread-1", RawType: "SlideUQPPFutureMutation"},
			want:  []string{"SlideUQPPFutureMutation"},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			rendered := formatEvent(test.event)
			for _, want := range test.want {
				if !strings.Contains(rendered, want) {
					t.Errorf("event %q = %q, missing %q", test.name, rendered, want)
				}
			}
		})
	}

	if rendered := formatEvent(igcore.Event{Type: "seq_id", SeqID: 42}); rendered != "" {
		t.Errorf("seq_id event rendered %q, want empty", rendered)
	}
}

func TestEventPrinterJSONFilter(t *testing.T) {
	var output bytes.Buffer
	printer := &eventPrinter{
		out:          &output,
		threadFilter: "thread-1",
		jsonOutput:   true,
	}

	printer.print(igcore.Event{Type: "connected", SeqID: 1})
	printer.print(igcore.Event{Type: "message.new", ThreadID: "thread-2"})
	printer.print(igcore.Event{Type: "message.new", ThreadID: "thread-1", Message: &igcore.MessageView{ID: "message-1"}})

	lines := strings.Split(strings.TrimSpace(output.String()), "\n")
	if len(lines) != 2 {
		t.Fatalf("lines = %d, want 2:\n%s", len(lines), output.String())
	}
	if !strings.Contains(lines[0], `"type":"connected"`) {
		t.Errorf("first line = %q", lines[0])
	}
	if !strings.Contains(lines[1], `"thread_id":"thread-1"`) {
		t.Errorf("second line = %q", lines[1])
	}
}

func TestRunSendValidation(t *testing.T) {
	const fakeCookies = "sessionid=fake; csrftoken=fake; ds_user_id=1"

	t.Run("blank text", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runSend([]string{"thread-id", "", "--cookie-header", fakeCookies}, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "text is required") {
			t.Errorf("error = %v, want text is required", err)
		}
	})

	t.Run("flags first", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runSend([]string{"--cookie-header", fakeCookies, "thread-id", ""}, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "text is required") {
			t.Errorf("error = %v, want text is required", err)
		}
	})

	t.Run("bad otid", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runSend([]string{"thread-id", "hello", "--otid", "abc", "--cookie-header", fakeCookies}, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "--otid must be a numeric") {
			t.Errorf("error = %v, want --otid validation", err)
		}
	})

	t.Run("negative retries", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runSend([]string{"thread-id", "hello", "--retry", "-1", "--cookie-header", fakeCookies}, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "--retry must be") {
			t.Errorf("error = %v, want retry validation", err)
		}
	})

	t.Run("overlong text", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		text := strings.Repeat("a", igcore.MaxTextLength+1)
		err := runSend([]string{"thread-id", text, "--cookie-header", fakeCookies}, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "maximum is 1000") {
			t.Errorf("error = %v, want maximum length error", err)
		}
	})
}

func TestWarnIfCookieFileIsReadable(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "cookies.json")
	if err := os.WriteFile(path, []byte("{}"), 0o644); err != nil {
		t.Fatal(err)
	}

	var warning bytes.Buffer
	warnIfCookieFileIsReadable(path, &warning)
	if !strings.Contains(warning.String(), "chmod 600") {
		t.Errorf("no permission warning for mode 0644: %q", warning.String())
	}

	if err := os.Chmod(path, 0o600); err != nil {
		t.Fatal(err)
	}
	warning.Reset()
	warnIfCookieFileIsReadable(path, &warning)
	if warning.Len() != 0 {
		t.Errorf("unexpected permission warning for mode 0600: %q", warning.String())
	}
}
