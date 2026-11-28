package igcore

import (
	"context"
	"errors"
	"fmt"
	"io"
	"strconv"
	"strings"
	"testing"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow"
	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/mautrix-meta/pkg/messagix/httpclient"
	"go.mau.fi/util/jsontime"
)

func TestBuildSendTextRequest(t *testing.T) {
	request, err := buildSendTextRequest("thread-id", "hello", "", "")
	if err != nil {
		t.Fatalf("buildSendTextRequest: %v", err)
	}
	if request.IGThreadIGID == nil || *request.IGThreadIGID != "thread-id" {
		t.Errorf("ig_thread_igid = %v, want thread-id", request.IGThreadIGID)
	}
	if request.Text.Value != "hello" {
		t.Errorf("text = %q, want hello", request.Text.Value)
	}
	if request.OfflineThreadingID == "" {
		t.Error("offline_threading_id is empty")
	}
	if _, err := strconv.ParseInt(request.OfflineThreadingID, 10, 64); err != nil {
		t.Errorf("offline_threading_id is not numeric: %q", request.OfflineThreadingID)
	}
	if request.SendAttribution == nil || *request.SendAttribution != "igd_web_chat_tab:in_thread" {
		t.Errorf("send_attribution = %v, want igd_web_chat_tab:in_thread", request.SendAttribution)
	}
	if request.ReplyToMessageID != nil {
		t.Errorf("reply_to_message_id = %v, want nil", request.ReplyToMessageID)
	}
	if request.RecipientIGIDs == nil || len(request.RecipientIGIDs) != 0 {
		t.Errorf("recipient_igids = %#v, want empty slice", request.RecipientIGIDs)
	}
	if request.Mentions == nil || request.MentionedUserIDs == nil || request.Commands == nil {
		t.Error("mention/command slices should be empty, not nil")
	}
}

func TestBuildSendTextRequestReplyAndOTID(t *testing.T) {
	request, err := buildSendTextRequest("thread-id", "hello", "message-id", "123456789")
	if err != nil {
		t.Fatalf("buildSendTextRequest: %v", err)
	}
	if request.ReplyToMessageID == nil || *request.ReplyToMessageID != "message-id" {
		t.Errorf("reply_to_message_id = %v, want message-id", request.ReplyToMessageID)
	}
	if request.OfflineThreadingID != "123456789" {
		t.Errorf("offline_threading_id = %q, want 123456789", request.OfflineThreadingID)
	}
}

func TestBuildSendTextRequestValidation(t *testing.T) {
	if _, err := buildSendTextRequest("", "hello", "", ""); err == nil {
		t.Error("empty thread ID accepted")
	}
	if _, err := buildSendTextRequest("thread-id", "   ", "", ""); err == nil {
		t.Error("blank text accepted")
	}
	if _, err := buildSendTextRequest("thread-id", strings.Repeat("a", MaxTextLength+1), "", ""); err == nil {
		t.Error("overlong text accepted")
	}
	if _, err := buildSendTextRequest("thread-id", strings.Repeat("a", MaxTextLength), "", ""); err != nil {
		t.Errorf("text at limit rejected: %v", err)
	}
}

func TestBuildSendTextRequestWithMentions(t *testing.T) {
	// Leading emoji takes 2 UTF-16 code units, matching Instagram's offset space.
	text := "👋 @maya.chen and @everyone!"
	mentions := []MentionInput{
		{Offset: 18, Length: 9, Type: MentionTypeEveryone, FBID: 200000000000000},
		{Offset: 3, Length: 10, Type: MentionTypePerson, FBID: 42},
	}

	request, err := buildSendTextRequestWithMentions("1234567890", text, "", "999", mentions)
	if err != nil {
		t.Fatalf("buildSendTextRequestWithMentions: %v", err)
	}
	if len(request.Mentions) != 1 {
		t.Fatalf("mentions len = %d, want 1", len(request.Mentions))
	}
	if got := request.Mentions[0]; got.Offset != 3 || got.Length != 10 || got.FBID != 42 {
		t.Errorf("mention[0] = %+v, want offset=3 length=10 fbid=42", got)
	}
	if len(request.MentionedUserIDs) != 1 || request.MentionedUserIDs[0] != "42" {
		t.Errorf("mentioned_user_ids = %#v, want [\"42\"]", request.MentionedUserIDs)
	}
	if len(request.Commands) != 1 {
		t.Fatalf("commands len = %d, want 1", len(request.Commands))
	}
	if request.Text.Value != "👋 @maya.chen and @\u200deveryone!" {
		t.Errorf("text = %q, want ZWJ after @ in @everyone", request.Text.Value)
	}
	if got := request.Commands[0]; got.Offset != 18 || got.Length != 10 || got.Type != "EVERYONE" || got.UserOrThreadFBID != 200000000000000 {
		t.Errorf("command[0] = %+v, want offset=18 length=10 type=EVERYONE fbid=200000000000000", got)
	}
}

func TestBuildSendTextRequestEveryoneShiftsSubsequentMentionsAndAvoidsDoubleZWJ(t *testing.T) {
	text := "@everyone ping @maya"
	mentions := []MentionInput{
		{Offset: 0, Length: 9, Type: MentionTypeEveryone, FBID: 200000000000000},
		{Offset: 15, Length: 5, Type: MentionTypePerson, FBID: 42},
	}

	request, err := buildSendTextRequestWithMentions("1234567890", text, "", "999", mentions)
	if err != nil {
		t.Fatalf("buildSendTextRequestWithMentions: %v", err)
	}
	if request.Text.Value != "@\u200deveryone ping @maya" {
		t.Errorf("text = %q, want @\\u200deveryone ping @maya", request.Text.Value)
	}
	if got := request.Commands[0]; got.Offset != 0 || got.Length != 10 {
		t.Errorf("command[0] = %+v, want offset=0 length=10", got)
	}
	if got := request.Mentions[0]; got.Offset != 16 || got.Length != 5 {
		t.Errorf("mention[0] = %+v, want shifted offset=16 length=5", got)
	}

	// Already containing U+200D should not insert a second ZWJ.
	already, err := buildSendTextRequestWithMentions(
		"1234567890",
		"@\u200deveryone hi",
		"",
		"999",
		[]MentionInput{{Offset: 0, Length: 10, Type: MentionTypeEveryone, FBID: 200000000000000}},
	)
	if err != nil {
		t.Fatalf("already-zwj buildSendTextRequestWithMentions: %v", err)
	}
	if already.Text.Value != "@\u200deveryone hi" {
		t.Errorf("already text = %q, want unchanged", already.Text.Value)
	}
	if got := already.Commands[0]; got.Offset != 0 || got.Length != 10 {
		t.Errorf("already command[0] = %+v, want offset=0 length=10", got)
	}
}

func TestBuildSendTextRequestWithMentionsDeduplicatesUserIDsAndFallbackThreadID(t *testing.T) {
	text := "@maya @maya @everyone"
	mentions := []MentionInput{
		{Offset: 0, Length: 5, Type: MentionTypePerson, FBID: 42},
		{Offset: 6, Length: 5, Type: MentionTypePerson, FBID: 42},
		{Offset: 12, Length: 9, Type: MentionTypeEveryone, FBID: 0},
	}

	request, err := buildSendTextRequestWithMentions("1234567890", text, "", "999", mentions)
	if err != nil {
		t.Fatalf("buildSendTextRequestWithMentions: %v", err)
	}
	if len(request.Mentions) != 2 {
		t.Fatalf("mentions len = %d, want 2", len(request.Mentions))
	}
	if len(request.MentionedUserIDs) != 1 || request.MentionedUserIDs[0] != "42" {
		t.Errorf("mentioned_user_ids = %#v, want deduplicated [\"42\"]", request.MentionedUserIDs)
	}
	if len(request.Commands) != 1 || request.Commands[0].UserOrThreadFBID != 1234567890 {
		t.Errorf("commands = %+v, want fallback thread ID 1234567890", request.Commands)
	}
}

func TestBuildSendTextRequestWithMentionsValidation(t *testing.T) {
	cases := []struct {
		name     string
		threadID string
		text     string
		mentions []MentionInput
	}{
		{
			name:     "negative offset",
			threadID: "123",
			text:     "@maya",
			mentions: []MentionInput{{Offset: -1, Length: 5, Type: MentionTypePerson, FBID: 1}},
		},
		{
			name:     "non-positive length",
			threadID: "123",
			text:     "@maya",
			mentions: []MentionInput{{Offset: 0, Length: 0, Type: MentionTypePerson, FBID: 1}},
		},
		{
			name:     "out of bounds",
			threadID: "123",
			text:     "@maya",
			mentions: []MentionInput{{Offset: 2, Length: 5, Type: MentionTypePerson, FBID: 1}},
		},
		{
			name:     "missing person fbid",
			threadID: "123",
			text:     "@maya",
			mentions: []MentionInput{{Offset: 0, Length: 5, Type: MentionTypePerson, FBID: 0}},
		},
		{
			name:     "non-numeric fallback thread id for everyone",
			threadID: "non-numeric",
			text:     "@everyone",
			mentions: []MentionInput{{Offset: 0, Length: 9, Type: MentionTypeEveryone, FBID: 0}},
		},
		{
			name:     "unsupported type",
			threadID: "123",
			text:     "@ai",
			mentions: []MentionInput{{Offset: 0, Length: 3, Type: "ai", FBID: 1}},
		},
	}
	for _, tc := range cases {
		if _, err := buildSendTextRequestWithMentions(tc.threadID, tc.text, "", "999", tc.mentions); err == nil {
			t.Errorf("%s: expected error, got nil", tc.name)
		}
	}
}

func TestBuildPaginateMessagesRequest(t *testing.T) {
	request := buildPaginateMessagesRequest("thread-id", "cursor-1", 5)

	if request.AfterCursor == nil || *request.AfterCursor != "cursor-1" {
		t.Errorf("after cursor = %v", request.AfterCursor)
	}
	if request.ThreadID != "thread-id" {
		t.Errorf("thread ID = %q", request.ThreadID)
	}
	if request.FirstN != 5 || request.InitialMessagePageCount != 5 {
		t.Errorf("limits = %d / %d, want 5 / 5", request.FirstN, request.InitialMessagePageCount)
	}
	if request.BeforeCursor != nil || request.LastN != nil || request.NewerThanMessageID != nil || request.OlderThanMessageID != nil {
		t.Errorf("unexpected cursor fields: %+v", request)
	}
}

func TestLoadOlderMessagesValidation(t *testing.T) {
	client := &Client{inner: &instameow.Client{}}

	if _, err := client.LoadOlderMessages(context.Background(), "", "cursor", 20); err == nil || !strings.Contains(err.Error(), "thread ID is required") {
		t.Errorf("empty thread ID error = %v", err)
	}
	if _, err := client.LoadOlderMessages(context.Background(), "thread-id", "", 20); err == nil || !strings.Contains(err.Error(), "after cursor is required") {
		t.Errorf("empty cursor error = %v", err)
	}
}

func TestNextWatchBackoff(t *testing.T) {
	for _, test := range []struct {
		current time.Duration
		want    time.Duration
	}{
		{current: 0, want: initialWatchBackoff},
		{current: time.Second, want: 2 * time.Second},
		{current: 8 * time.Second, want: 16 * time.Second},
		{current: 16 * time.Second, want: maxWatchBackoff},
		{current: maxWatchBackoff, want: maxWatchBackoff},
		{current: 45 * time.Second, want: maxWatchBackoff},
	} {
		if got := nextWatchBackoff(test.current); got != test.want {
			t.Errorf("nextWatchBackoff(%s) = %s, want %s", test.current, got, test.want)
		}
	}
}

func TestShouldRetryWatch(t *testing.T) {
	for _, test := range []struct {
		name string
		err  error
		want bool
	}{
		{name: "nil", err: nil, want: false},
		{name: "eof", err: io.EOF, want: true},
		{name: "network", err: errors.New("network down"), want: true},
		{name: "canceled", err: context.Canceled, want: false},
		{name: "deadline", err: context.DeadlineExceeded, want: false},
		{name: "token invalid", err: httpclient.ErrTokenInvalidated, want: false},
		{name: "challenge", err: httpclient.ErrChallengeRequired, want: false},
		{name: "rate limited", err: httpclient.ErrRateLimited, want: false},
		{name: "wrapped challenge", err: fmt.Errorf("wrapped: %w", httpclient.ErrChallengeRequired), want: false},
	} {
		t.Run(test.name, func(t *testing.T) {
			if got := shouldRetryWatch(test.err); got != test.want {
				t.Errorf("shouldRetryWatch(%v) = %v, want %v", test.err, got, test.want)
			}
		})
	}
}

func TestSleepContext(t *testing.T) {
	if err := sleepContext(context.Background(), time.Millisecond); err != nil {
		t.Errorf("sleepContext = %v", err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if err := sleepContext(ctx, time.Hour); !errors.Is(err, context.Canceled) {
		t.Errorf("canceled sleep = %v, want context.Canceled", err)
	}
}

func TestWatchValidation(t *testing.T) {
	var client *Client
	if err := client.Watch(context.Background()); err == nil || !strings.Contains(err.Error(), "client is not initialized") {
		t.Errorf("error = %v", err)
	}
}

func TestNewOTID(t *testing.T) {
	if _, err := strconv.ParseInt(NewOTID(), 10, 64); err != nil {
		t.Errorf("NewOTID is not numeric: %v", err)
	}
}

func TestNewSendResult(t *testing.T) {
	message := slidetypes.SentMessage{
		MessageID:   "mid.$1",
		ID:          "message-id",
		TimestampMS: jsontime.UnixMilliString{Time: time.UnixMilli(1_700_000_000_000)},
	}
	request, err := buildSendTextRequestWithMentions(
		"1234567890",
		"@everyone hello",
		"",
		"otid",
		[]MentionInput{{Offset: 0, Length: 9, Type: MentionTypeEveryone, FBID: 200000000000000}},
	)
	if err != nil {
		t.Fatalf("buildSendTextRequestWithMentions: %v", err)
	}
	result := newSendResult("1234567890", request, message)

	if result.ProtocolVersion != ProtocolVersion {
		t.Errorf("protocol version = %q, want %q", result.ProtocolVersion, ProtocolVersion)
	}
	if result.ThreadID != "1234567890" || result.OfflineThreadingID != "otid" || result.Text != "@\u200deveryone hello" {
		t.Errorf("result = %+v", result)
	}
	if len(result.Mentions) != 1 || result.Mentions[0] != (MentionView{Offset: 0, Length: 10, Type: MentionTypeEveryone, FBID: 200000000000000}) {
		t.Errorf("mentions = %+v, want adjusted everyone span", result.Mentions)
	}
	if result.MessageID != "mid.$1" || result.ID != "message-id" {
		t.Errorf("message IDs = %q / %q", result.MessageID, result.ID)
	}
	if result.TimestampMS != 1_700_000_000_000 {
		t.Errorf("timestamp = %d, want 1700000000000", result.TimestampMS)
	}
}
