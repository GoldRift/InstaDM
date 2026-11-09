package igcore

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
)

// MaxSearchQueryLength caps the omni-picker query; Instagram rejects longer
// ones without a useful error.
const MaxSearchQueryLength = 100

type SearchUserView struct {
	FBID       int64  `json:"fbid"`
	IGID       string `json:"ig_id,omitempty"`
	Username   string `json:"username"`
	Name       string `json:"name"`
	AvatarURL  string `json:"avatar_url,omitempty"`
	IsVerified bool   `json:"is_verified,omitempty"`
}

type SearchView struct {
	ProtocolVersion string           `json:"protocol_version"`
	Query           string           `json:"query"`
	Users           []SearchUserView `json:"users"`
}

// SearchUsers finds accounts by username or display name through
// IGDOmniPickerSearchResultsListQuery. The interop messaging FBID in each
// result is what SendNewDM takes as the recipient.
func (c *Client) SearchUsers(ctx context.Context, query string) (*SearchView, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}

	query, err := normalizeSearchQuery(query)
	if err != nil {
		return nil, err
	}

	if _, err := c.bootstrap(ctx); err != nil {
		return nil, err
	}
	response, err := c.inner.SearchUsers(ctx, query)
	if err != nil {
		return nil, err
	}
	if response == nil {
		return nil, errors.New("Instagram returned an empty search response")
	}

	return newSearchView(query, response.Data.Results), nil
}

func normalizeSearchQuery(query string) (string, error) {
	query = strings.TrimSpace(query)
	if query == "" {
		return "", errors.New("search query is required")
	}
	if length := len([]rune(query)); length > MaxSearchQueryLength {
		return "", fmt.Errorf("search query is %d characters; the maximum is %d", length, MaxSearchQueryLength)
	}
	return query, nil
}

// newSearchView keeps only accounts that can receive a DM: a result without an
// interop messaging FBID cannot be the recipient of a new conversation.
func newSearchView(query string, results []*slidetypes.SearchResult) *SearchView {
	view := &SearchView{
		ProtocolVersion: ProtocolVersion,
		Query:           query,
		Users:           []SearchUserView{},
	}
	seen := make(map[int64]bool, len(results))
	for _, result := range results {
		if result == nil || result.InteropMessagingUserFBID == 0 || seen[result.InteropMessagingUserFBID] {
			continue
		}
		seen[result.InteropMessagingUserFBID] = true
		view.Users = append(view.Users, SearchUserView{
			FBID:       result.InteropMessagingUserFBID,
			IGID:       strings.TrimSpace(result.PK),
			Username:   strings.TrimSpace(result.Username),
			Name:       strings.TrimSpace(result.FullName),
			AvatarURL:  strings.TrimSpace(result.ProfilePicURL),
			IsVerified: result.IsVerified,
		})
	}
	return view
}
