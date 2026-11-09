package igcore

import (
	"context"
	"strings"
	"testing"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
)

func TestNewSearchView(t *testing.T) {
	view := newSearchView("@maya", []*slidetypes.SearchResult{
		{
			InteropMessagingUserFBID: 111,
			PK:                       " 42 ",
			Username:                 " maya.chen ",
			FullName:                 "Maya Chen",
			ProfilePicURL:            "https://example.invalid/maya.jpg",
			IsVerified:               true,
		},
		{InteropMessagingUserFBID: 111, Username: "duplicate"},
		{InteropMessagingUserFBID: 0, Username: "no-messaging-id"},
		nil,
	})
	if view.ProtocolVersion != ProtocolVersion || view.Query != "@maya" {
		t.Errorf("view = %+v", view)
	}
	if len(view.Users) != 1 {
		t.Fatalf("users = %+v, want one", view.Users)
	}
	user := view.Users[0]
	if user.FBID != 111 || user.IGID != "42" || user.Username != "maya.chen" || user.Name != "Maya Chen" {
		t.Errorf("user = %+v", user)
	}
	if user.AvatarURL != "https://example.invalid/maya.jpg" || !user.IsVerified {
		t.Errorf("user metadata = %+v", user)
	}
}

func TestNewSearchViewEmptyResults(t *testing.T) {
	view := newSearchView("nobody", nil)
	if view.Users == nil || len(view.Users) != 0 {
		t.Errorf("users = %v, want an empty slice", view.Users)
	}
}

func TestNormalizeSearchQuery(t *testing.T) {
	if query, err := normalizeSearchQuery("  maya.chen  "); err != nil || query != "maya.chen" {
		t.Errorf("query = %q, err = %v", query, err)
	}
	if _, err := normalizeSearchQuery("  "); err == nil || !strings.Contains(err.Error(), "search query is required") {
		t.Errorf("empty query error = %v", err)
	}
	if _, err := normalizeSearchQuery(strings.Repeat("a", MaxSearchQueryLength+1)); err == nil || !strings.Contains(err.Error(), "maximum") {
		t.Errorf("long query error = %v", err)
	}
}

func TestSearchUsersNilClient(t *testing.T) {
	var client *Client
	if _, err := client.SearchUsers(context.Background(), "maya"); err == nil || !strings.Contains(err.Error(), "client is not initialized") {
		t.Errorf("nil client error = %v", err)
	}
}
