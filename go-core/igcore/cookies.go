package igcore

import (
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"

	"go.mau.fi/mautrix-meta/pkg/messagix/cookies"
	"go.mau.fi/mautrix-meta/pkg/messagix/types"
)

// ParseCookies accepts the cookie formats produced by the common browser
// extensions and the command line:
//
//   - a flat JSON object: {"sessionid": "...", "csrftoken": "..."}
//   - a JSON object wrapped in {"cookies": ...}
//   - a Cookie-Editor style JSON array: [{"name": "...", "value": "..."}]
//   - a raw Cookie header: "sessionid=...; csrftoken=...; ds_user_id=..."
//
// Only the three required Instagram cookies are enforced. Optional routing
// cookies are kept when present because Instagram uses them for anti-abuse
// and media routing.
func ParseCookies(data []byte) (*cookies.Cookies, error) {
	values, err := parseCookieValues(data)
	if err != nil {
		return nil, err
	}
	if len(values) == 0 {
		return nil, errors.New("no cookies found in input")
	}

	cs := &cookies.Cookies{Platform: types.Instagram}
	cs.UpdateValues(values)

	if missing := cs.GetMissingCookieNames(); len(missing) > 0 {
		return nil, fmt.Errorf("missing required Instagram cookies: %s", joinCookieNames(missing))
	}

	return cs, nil
}

func parseCookieValues(data []byte) (map[cookies.MetaCookieName]string, error) {
	trimmed := strings.TrimSpace(string(data))
	if trimmed == "" {
		return nil, errors.New("cookie input is empty")
	}

	switch trimmed[0] {
	case '{':
		return parseCookieObject(trimmed)
	case '[':
		return parseCookieArray(trimmed)
	default:
		return parseCookieHeader(trimmed)
	}
}

func parseCookieObject(raw string) (map[cookies.MetaCookieName]string, error) {
	var object map[string]json.RawMessage
	if err := json.Unmarshal([]byte(raw), &object); err != nil {
		return nil, fmt.Errorf("parse cookie JSON: %w", err)
	}

	// Some exports wrap the cookie map in an extra object.
	if inner, ok := object["cookies"]; ok && len(object) == 1 {
		return parseCookieValues(inner)
	}

	// Cookie-Editor also has a single-cookie object form.
	if _, hasName := object["name"]; hasName {
		if _, hasValue := object["value"]; hasValue {
			var entry cookieEntry
			if err := json.Unmarshal([]byte(raw), &entry); err != nil {
				return nil, fmt.Errorf("parse cookie entry: %w", err)
			}
			return entryValues(entry), nil
		}
	}

	values := make(map[cookies.MetaCookieName]string, len(object))
	for name, rawValue := range object {
		var value string
		if err := json.Unmarshal(rawValue, &value); err != nil {
			return nil, fmt.Errorf("cookie %q must have a string value", name)
		}
		values[cookies.MetaCookieName(name)] = value
	}
	return values, nil
}

type cookieEntry struct {
	Name  string `json:"name"`
	Value string `json:"value"`
}

func parseCookieArray(raw string) (map[cookies.MetaCookieName]string, error) {
	var entries []cookieEntry
	if err := json.Unmarshal([]byte(raw), &entries); err != nil {
		return nil, fmt.Errorf("parse cookie JSON array: %w", err)
	}
	if len(entries) == 0 {
		return nil, errors.New("cookie JSON array is empty")
	}

	values := make(map[cookies.MetaCookieName]string, len(entries))
	for _, entry := range entries {
		if entry.Name == "" {
			continue
		}
		values[cookies.MetaCookieName(entry.Name)] = entry.Value
	}
	return values, nil
}

func entryValues(entry cookieEntry) map[cookies.MetaCookieName]string {
	return map[cookies.MetaCookieName]string{
		cookies.MetaCookieName(entry.Name): entry.Value,
	}
}

func parseCookieHeader(raw string) (map[cookies.MetaCookieName]string, error) {
	header := strings.TrimSpace(raw)
	if len(header) >= len("cookie:") && strings.EqualFold(header[:len("cookie:")], "cookie:") {
		header = header[len("cookie:"):]
	}

	values := make(map[cookies.MetaCookieName]string)
	for _, part := range strings.Split(header, ";") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		name, value, ok := strings.Cut(part, "=")
		if !ok {
			continue
		}
		name = strings.TrimSpace(name)
		if name == "" {
			continue
		}
		values[cookies.MetaCookieName(name)] = strings.TrimSpace(value)
	}

	if len(values) == 0 {
		return nil, errors.New("cookie header is empty")
	}
	return values, nil
}

func joinCookieNames(names []cookies.MetaCookieName) string {
	sorted := make([]string, len(names))
	for i, name := range names {
		sorted[i] = string(name)
	}
	sort.Strings(sorted)
	return strings.Join(sorted, ", ")
}
