package igcore

import (
	"testing"
)

func TestSelectMessage(t *testing.T) {
	messages := []MessageView{
		{ID: "m1", TimestampMS: 1000},
		{ID: "m2", TimestampMS: 2000},
	}

	newest, err := selectMessage(messages, "")
	if err != nil {
		t.Fatalf("select newest: %v", err)
	}
	if newest.ID != "m2" {
		t.Errorf("newest = %q, want m2", newest.ID)
	}

	byID, err := selectMessage(messages, "m1")
	if err != nil {
		t.Fatalf("select by ID: %v", err)
	}
	if byID.ID != "m1" {
		t.Errorf("by ID = %q, want m1", byID.ID)
	}

	if _, err := selectMessage(messages, "missing"); err == nil {
		t.Error("missing message accepted")
	}
	if _, err := selectMessage(nil, ""); err == nil {
		t.Error("empty message list accepted")
	}
}

func TestBuildMarkReadRequests(t *testing.T) {
	first, validation := buildMarkReadRequests("long-thread", "message-1", 1_700_000_000_000)

	if first.Metadata.IGThreadIGID != "long-thread" {
		t.Errorf("first metadata thread = %q", first.Metadata.IGThreadIGID)
	}
	if first.Data.MessageID != "message-1" {
		t.Errorf("first message = %q", first.Data.MessageID)
	}
	if first.Data.ItemID == nil || *first.Data.ItemID != "" {
		t.Errorf("first item_id = %v, want pointer to empty string", first.Data.ItemID)
	}
	if !first.Data.MessageTimestampMS.IsZero() {
		t.Errorf("first timestamp should be zero, got %v", first.Data.MessageTimestampMS)
	}

	if validation.Metadata.IGThreadIGID != "long-thread" {
		t.Errorf("validation metadata thread = %q", validation.Metadata.IGThreadIGID)
	}
	if validation.Data.MessageID != "message-1" {
		t.Errorf("validation message = %q", validation.Data.MessageID)
	}
	if validation.Data.ItemID != nil {
		t.Errorf("validation item_id = %v, want nil", validation.Data.ItemID)
	}
	if got := validation.Data.MessageTimestampMS.UnixMilli(); got != 1_700_000_000_000 {
		t.Errorf("validation timestamp = %d", got)
	}
}
