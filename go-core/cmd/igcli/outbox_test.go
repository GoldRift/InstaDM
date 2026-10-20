package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func newTestOutbox(t *testing.T) string {
	t.Helper()
	return filepath.Join(t.TempDir(), "outbox.json")
}

func testTextRecord(otid, text string) outboxRecord {
	return outboxRecord{
		OTID:      otid,
		Kind:      "text",
		ThreadID:  "thread-id",
		Text:      text,
		CreatedAt: time.Now().UTC(),
	}
}

func TestOutboxAddListRemove(t *testing.T) {
	path := newTestOutbox(t)
	if err := addOutboxRecord(path, testTextRecord("1", "hello")); err != nil {
		t.Fatalf("add text: %v", err)
	}
	if err := addOutboxRecord(path, outboxRecord{
		OTID:      "2",
		Kind:      "media",
		ThreadID:  "thread-id",
		FilePath:  "/tmp/photo.png",
		Filename:  "photo.png",
		CreatedAt: time.Now().UTC(),
	}); err != nil {
		t.Fatalf("add media: %v", err)
	}

	records, err := listOutboxRecords(path)
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if len(records) != 2 {
		t.Fatalf("records = %d, want 2", len(records))
	}
	if records[0].OTID != "1" || records[1].OTID != "2" {
		t.Errorf("record order = %s, %s", records[0].OTID, records[1].OTID)
	}

	if err := addOutboxRecord(path, testTextRecord("1", "duplicate")); err == nil || !strings.Contains(err.Error(), "already pending") {
		t.Errorf("duplicate add error = %v", err)
	}

	removed, err := removeOutboxRecord(path, "1")
	if err != nil || !removed {
		t.Fatalf("remove 1 = %v, %v", removed, err)
	}
	records, err = listOutboxRecords(path)
	if err != nil || len(records) != 1 || records[0].OTID != "2" {
		t.Fatalf("after remove = %+v, %v", records, err)
	}

	removed, err = removeOutboxRecord(path, "2")
	if err != nil || !removed {
		t.Fatalf("remove 2 = %v, %v", removed, err)
	}
	if _, err := os.Stat(path); !errors.Is(err, os.ErrNotExist) {
		t.Errorf("outbox file still exists: %v", err)
	}
}

func TestOutboxUpdateAndFailure(t *testing.T) {
	path := newTestOutbox(t)
	if err := addOutboxRecord(path, testTextRecord("1", "hello")); err != nil {
		t.Fatalf("add: %v", err)
	}

	found, err := updateOutboxRecord(path, "1", func(record *outboxRecord) {
		record.Attempts = 2
		record.LastError = "network down"
	})
	if err != nil || !found {
		t.Fatalf("update = %v, %v", found, err)
	}
	records, err := listOutboxRecords(path)
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if records[0].Attempts != 2 || records[0].LastError != "network down" {
		t.Errorf("record = %+v", records[0])
	}

	markOutboxFailed(path, "1", errors.New("timeout"))
	records, _ = listOutboxRecords(path)
	if records[0].Attempts != 3 || records[0].LastError != "timeout" {
		t.Errorf("after mark failed = %+v", records[0])
	}

	markOutboxFailed("", "1", errors.New("ignored"))
	markOutboxFailed(path, "missing", errors.New("ignored"))
}

func TestOutboxRecordValidation(t *testing.T) {
	for _, test := range []struct {
		name    string
		payload string
		want    string
	}{
		{name: "invalid JSON", payload: "{", want: "parse outbox"},
		{name: "missing OTID", payload: `[{"kind":"text","thread_id":"t","text":"hi"}]`, want: "OTID is empty"},
		{name: "missing thread", payload: `[{"otid":"1","kind":"text","thread_id":"","text":"hi"}]`, want: "thread ID is empty"},
		{name: "empty text", payload: `[{"otid":"1","kind":"text","thread_id":"t","text":" "}]`, want: "text is empty"},
		{name: "missing media path", payload: `[{"otid":"1","kind":"media","thread_id":"t"}]`, want: "media file path is empty"},
		{name: "unknown kind", payload: `[{"otid":"1","kind":"other","thread_id":"t"}]`, want: "unknown kind"},
		{name: "duplicate OTID", payload: `[{"otid":"1","kind":"text","thread_id":"t","text":"a"},{"otid":"1","kind":"text","thread_id":"t","text":"b"}]`, want: "duplicate"},
	} {
		t.Run(test.name, func(t *testing.T) {
			path := newTestOutbox(t)
			if err := os.WriteFile(path, []byte(test.payload), 0o600); err != nil {
				t.Fatalf("write outbox: %v", err)
			}
			if _, err := listOutboxRecords(path); err == nil || !strings.Contains(err.Error(), test.want) {
				t.Errorf("error = %v, want %q", err, test.want)
			}
		})
	}
}

func TestOutboxFileMode(t *testing.T) {
	path := newTestOutbox(t)
	if err := addOutboxRecord(path, testTextRecord("1", "hello")); err != nil {
		t.Fatalf("add: %v", err)
	}
	info, err := os.Stat(path)
	if err != nil {
		t.Fatalf("stat: %v", err)
	}
	if mode := info.Mode().Perm(); mode != 0o600 {
		t.Errorf("mode = %o, want 600", mode)
	}
}

func TestSendWithOutboxLeavesPendingOnClientError(t *testing.T) {
	path := newTestOutbox(t)
	var stdout, stderr bytes.Buffer
	err := runSend([]string{"thread-id", "hello", "--outbox", path, "--cookie-header", "bogus"}, &stdout, &stderr)
	if err == nil {
		t.Fatal("send succeeded with bogus cookies")
	}

	records, listErr := listOutboxRecords(path)
	if listErr != nil {
		t.Fatalf("list: %v", listErr)
	}
	if len(records) != 1 {
		t.Fatalf("records = %d, want 1", len(records))
	}
	if records[0].Kind != "text" || records[0].ThreadID != "thread-id" || records[0].Text != "hello" {
		t.Errorf("record = %+v", records[0])
	}
	if records[0].Attempts != 1 || records[0].LastError == "" {
		t.Errorf("failure accounting = %+v", records[0])
	}
}

func TestSendMediaWithOutboxLeavesPendingOnClientError(t *testing.T) {
	path := newTestOutbox(t)
	mediaPath := filepath.Join(t.TempDir(), "photo.bin")
	if err := os.WriteFile(mediaPath, []byte("not really media"), 0o600); err != nil {
		t.Fatalf("write media: %v", err)
	}

	var stdout, stderr bytes.Buffer
	err := runSendMedia([]string{"thread-id", mediaPath, "--outbox", path, "--cookie-header", "bogus"}, &stdout, &stderr)
	if err == nil {
		t.Fatal("send-media succeeded with bogus cookies")
	}

	records, listErr := listOutboxRecords(path)
	if listErr != nil {
		t.Fatalf("list: %v", listErr)
	}
	if len(records) != 1 {
		t.Fatalf("records = %d, want 1", len(records))
	}
	absolutePath, _ := filepath.Abs(mediaPath)
	if records[0].Kind != "media" || records[0].FilePath != absolutePath || records[0].Filename != "photo.bin" {
		t.Errorf("record = %+v", records[0])
	}
}

func TestRunOutboxValidation(t *testing.T) {
	path := newTestOutbox(t)
	if err := addOutboxRecord(path, testTextRecord("1", "hello")); err != nil {
		t.Fatalf("add: %v", err)
	}

	t.Run("missing subcommand", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runOutbox(nil, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "usage: igcli outbox") {
			t.Errorf("error = %v", err)
		}
	})
	t.Run("unknown subcommand", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runOutbox([]string{"bogus", "--path", path}, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "unknown outbox command") {
			t.Errorf("error = %v", err)
		}
	})
	t.Run("missing path", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runOutbox([]string{"list"}, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "outbox path is required") {
			t.Errorf("error = %v", err)
		}
	})
	t.Run("list", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		if err := runOutbox([]string{"list", "--path", path}, &stdout, &stderr); err != nil {
			t.Fatalf("list: %v", err)
		}
		if !strings.Contains(stdout.String(), "Pending sends: 1") || !strings.Contains(stdout.String(), "hello") {
			t.Errorf("output = %q", stdout.String())
		}
	})
	t.Run("list JSON", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		if err := runOutbox([]string{"list", "--path", path, "--json"}, &stdout, &stderr); err != nil {
			t.Fatalf("list JSON: %v", err)
		}
		var records []outboxRecord
		if err := json.Unmarshal(stdout.Bytes(), &records); err != nil {
			t.Fatalf("parse JSON: %v\n%s", err, stdout.String())
		}
		if len(records) != 1 || records[0].OTID != "1" {
			t.Errorf("records = %+v", records)
		}
	})
	t.Run("remove missing id", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runOutbox([]string{"remove", "--path", path}, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "--id is required") {
			t.Errorf("error = %v", err)
		}
	})
	t.Run("remove unknown id", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runOutbox([]string{"remove", "--path", path, "--id", "missing"}, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "no pending send") {
			t.Errorf("error = %v", err)
		}
	})
	t.Run("remove", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		if err := runOutbox([]string{"remove", "--path", path, "--id", "1"}, &stdout, &stderr); err != nil {
			t.Fatalf("remove: %v", err)
		}
		if !strings.Contains(stdout.String(), "Removed pending send 1") {
			t.Errorf("output = %q", stdout.String())
		}
	})
	t.Run("retry empty", func(t *testing.T) {
		var stdout, stderr bytes.Buffer
		err := runOutbox([]string{"retry", "--path", path}, &stdout, &stderr)
		if err == nil || !strings.Contains(err.Error(), "no pending sends") {
			t.Errorf("error = %v", err)
		}
	})
}
