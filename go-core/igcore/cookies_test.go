package igcore

import (
	"strings"
	"testing"

	"go.mau.fi/mautrix-meta/pkg/messagix/cookies"
)

func TestParseCookiesFlatJSON(t *testing.T) {
	cs, err := ParseCookies([]byte(`{
		"sessionid": "session-value",
		"csrftoken": "csrf-value",
		"ds_user_id": "12345",
		"mid": "machine-id"
	}`))
	if err != nil {
		t.Fatalf("ParseCookies returned error: %v", err)
	}

	if got := cs.Get(cookies.IGCookieSessionID); got != "session-value" {
		t.Errorf("sessionid = %q, want %q", got, "session-value")
	}
	if got := cs.Get(cookies.IGCookieCSRFToken); got != "csrf-value" {
		t.Errorf("csrftoken = %q, want %q", got, "csrf-value")
	}
	if got := cs.Get(cookies.IGCookieDSUserID); got != "12345" {
		t.Errorf("ds_user_id = %q, want %q", got, "12345")
	}
	if got := cs.Get(cookies.IGCookieMachineID); got != "machine-id" {
		t.Errorf("mid = %q, want %q", got, "machine-id")
	}
}

func TestParseCookiesCookieHeader(t *testing.T) {
	cs, err := ParseCookies([]byte("Cookie: sessionid=session-value; csrftoken=csrf-value; ds_user_id=12345"))
	if err != nil {
		t.Fatalf("ParseCookies returned error: %v", err)
	}
	if got := cs.Get(cookies.IGCookieSessionID); got != "session-value" {
		t.Errorf("sessionid = %q, want %q", got, "session-value")
	}
}

func TestParseCookiesArray(t *testing.T) {
	cs, err := ParseCookies([]byte(`[
		{"name": "sessionid", "value": "session-value", "domain": ".instagram.com"},
		{"name": "csrftoken", "value": "csrf-value", "domain": ".instagram.com"},
		{"name": "ds_user_id", "value": "12345", "domain": ".instagram.com"}
	]`))
	if err != nil {
		t.Fatalf("ParseCookies returned error: %v", err)
	}
	if got := cs.Get(cookies.IGCookieDSUserID); got != "12345" {
		t.Errorf("ds_user_id = %q, want %q", got, "12345")
	}
}

func TestParseCookiesWrappedObject(t *testing.T) {
	cs, err := ParseCookies([]byte(`{"cookies": {
		"sessionid": "session-value",
		"csrftoken": "csrf-value",
		"ds_user_id": "12345"
	}}`))
	if err != nil {
		t.Fatalf("ParseCookies returned error: %v", err)
	}
	if got := cs.Get(cookies.IGCookieSessionID); got != "session-value" {
		t.Errorf("sessionid = %q, want %q", got, "session-value")
	}
}

func TestParseCookiesMissingRequired(t *testing.T) {
	_, err := ParseCookies([]byte(`{"sessionid": "session-value"}`))
	if err == nil {
		t.Fatal("ParseCookies succeeded with missing required cookies")
	}
	for _, name := range []string{"csrftoken", "ds_user_id"} {
		if !strings.Contains(err.Error(), name) {
			t.Errorf("error %q does not mention missing cookie %q", err, name)
		}
	}
}

func TestParseCookiesEmpty(t *testing.T) {
	if _, err := ParseCookies(nil); err == nil {
		t.Fatal("ParseCookies succeeded with empty input")
	}
}
