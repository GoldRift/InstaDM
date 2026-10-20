package mobile

import (
	"strings"
	"testing"

	"github.com/goldrift/instadm/go-core/igcore"
)

const fakeCookies = "sessionid=fake; csrftoken=fake; ds_user_id=1"

func TestNewRejectsEmptyCookies(t *testing.T) {
	if _, err := New(""); err == nil || !strings.Contains(err.Error(), "cookie input is empty") {
		t.Errorf("error = %v, want empty cookie error", err)
	}
	if _, err := New("sessionid=fake"); err == nil || !strings.Contains(err.Error(), "missing required") {
		t.Errorf("error = %v, want missing cookie error", err)
	}
}

func TestVersion(t *testing.T) {
	if Version() != igcore.ProtocolVersion {
		t.Errorf("Version() = %q, want %q", Version(), igcore.ProtocolVersion)
	}
}

func TestValidationBeforeNetwork(t *testing.T) {
	client, err := New(fakeCookies)
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	defer client.Close()

	if _, err := client.ThreadJSON("", 20, "", 1); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("thread error = %v", err)
	}
	if _, err := client.SendTextJSON("thread-id", " ", "", "", 1); err == nil || !strings.Contains(err.Error(), "text is required") {
		t.Errorf("send text error = %v", err)
	}
	if _, err := client.SendMediaJSON("123", "photo.png", "image/png", nil, "", "", 1); err == nil || !strings.Contains(err.Error(), "media file is empty") {
		t.Errorf("send media error = %v", err)
	}
	if _, err := client.EditJSON("thread-id", "message-id", " ", "", 1); err == nil || !strings.Contains(err.Error(), "text is required") {
		t.Errorf("edit error = %v", err)
	}
	if _, err := client.UnsendJSON("thread-id", "", 1); err == nil || !strings.Contains(err.Error(), "message ID is required") {
		t.Errorf("unsend error = %v", err)
	}
	if _, err := client.MuteJSON("thread-id", -2, 1); err == nil || !strings.Contains(err.Error(), "mute seconds") {
		t.Errorf("mute error = %v", err)
	}
	if _, err := client.PinJSON("", true, 1); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("pin error = %v", err)
	}
	if _, err := client.ReactJSON("thread-id", "message-id", "", false, 1); err == nil || !strings.Contains(err.Error(), "emoji is required") {
		t.Errorf("react error = %v", err)
	}
	if _, err := client.MarkReadJSON("", "", 1); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("mark read error = %v", err)
	}
	if _, err := client.DownloadMediaJSON("thread-id", "", 0, "/tmp/out.png", 1); err == nil || !strings.Contains(err.Error(), "message ID is required") {
		t.Errorf("download error = %v", err)
	}
}

func TestNilClient(t *testing.T) {
	var client *Client
	if _, err := client.MailboxJSON(1); err == nil || !strings.Contains(err.Error(), "client is not initialized") {
		t.Errorf("error = %v", err)
	}
}
