package igcore

import (
	"io"
	"testing"

	"github.com/rs/zerolog"
	"go.mau.fi/mautrix-meta/pkg/messagix/cookies"
	"go.mau.fi/mautrix-meta/pkg/messagix/types"
)

func newExportTestClient(t *testing.T, cs *cookies.Cookies) *Client {
	t.Helper()
	client, err := New(cs, Options{LogLevel: zerolog.WarnLevel, LogOutput: io.Discard})
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	t.Cleanup(client.Close)
	return client
}

func TestExportCookiesJSONRoundTrip(t *testing.T) {
	cs, err := ParseCookies([]byte(
		`{"sessionid":"session","csrftoken":"csrf","ds_user_id":"42","rur":"rur-value","mid":"machine"}`,
	))
	if err != nil {
		t.Fatalf("ParseCookies: %v", err)
	}
	client := newExportTestClient(t, cs)

	exported, err := client.ExportCookiesJSON()
	if err != nil {
		t.Fatalf("ExportCookiesJSON: %v", err)
	}

	parsed, err := ParseCookies([]byte(exported))
	if err != nil {
		t.Fatalf("ParseCookies(exported): %v", err)
	}
	for _, name := range []cookies.MetaCookieName{
		cookies.IGCookieSessionID,
		cookies.IGCookieCSRFToken,
		cookies.IGCookieDSUserID,
		cookies.IGCookieRUR,
		cookies.IGCookieMachineID,
	} {
		if got, want := parsed.Get(name), cs.Get(name); got != want {
			t.Errorf("cookie %s = %q, want %q", name, got, want)
		}
	}
}

func TestExportCookiesJSONIncludesRotatedValues(t *testing.T) {
	cs, err := ParseCookies([]byte(`{"sessionid":"session","csrftoken":"csrf","ds_user_id":"42"}`))
	if err != nil {
		t.Fatalf("ParseCookies: %v", err)
	}
	client := newExportTestClient(t, cs)
	client.inner.GetCookies().Set(cookies.IGCookieRUR, "rotated-rur")
	client.inner.GetCookies().Set(cookies.IGCookieSHBID, "rotated-shbid")

	exported, err := client.ExportCookiesJSON()
	if err != nil {
		t.Fatalf("ExportCookiesJSON: %v", err)
	}
	parsed, err := ParseCookies([]byte(exported))
	if err != nil {
		t.Fatalf("ParseCookies(exported): %v", err)
	}
	if got := parsed.Get(cookies.IGCookieRUR); got != "rotated-rur" {
		t.Errorf("rur = %q, want rotated-rur", got)
	}
	if got := parsed.Get(cookies.IGCookieSHBID); got != "rotated-shbid" {
		t.Errorf("shbid = %q, want rotated-shbid", got)
	}
}

func TestExportCookiesJSONRejectsIncompleteSession(t *testing.T) {
	cs := &cookies.Cookies{Platform: types.Instagram}
	cs.UpdateValues(map[cookies.MetaCookieName]string{
		cookies.IGCookieSessionID: "session",
	})
	client := newExportTestClient(t, cs)

	if _, err := client.ExportCookiesJSON(); err == nil {
		t.Fatal("ExportCookiesJSON succeeded with missing required cookies")
	}
}

func TestExportCookiesJSONNilClient(t *testing.T) {
	var client *Client
	if _, err := client.ExportCookiesJSON(); err == nil {
		t.Fatal("ExportCookiesJSON succeeded on a nil client")
	}
}
