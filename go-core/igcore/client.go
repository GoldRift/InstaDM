package igcore

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/rs/zerolog"
	"go.mau.fi/mautrix-meta/pkg/instameow"
	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/mautrix-meta/pkg/messagix/cookies"
	"go.mau.fi/mautrix-meta/pkg/messagix/methods"
	"go.mau.fi/mautrix-meta/pkg/messagix/types"
)

type Options struct {
	LogLevel  zerolog.Level
	LogOutput io.Writer
	OnEvent   func(Event)
}

type Client struct {
	inner   *instameow.Client
	log     zerolog.Logger
	onEvent func(Event)

	mu           sync.Mutex
	bootstrapped bool
	ownFBID      int64
}

// New creates a read-only protocol client. LoadMailbox performs the inbox page
// bootstrap and the mailbox GraphQL query; it does not open the DGW socket.
func New(cs *cookies.Cookies, options Options) (*Client, error) {
	if cs == nil {
		return nil, errors.New("cookies are required")
	}

	output := options.LogOutput
	if output == nil {
		output = os.Stderr
	}
	logger := zerolog.New(zerolog.ConsoleWriter{
		Out:        output,
		TimeFormat: time.RFC3339,
		NoColor:    true,
	}).Level(options.LogLevel).With().Timestamp().Logger()

	inner := instameow.NewClient(instameow.ClientParams{
		Cookies:       cs,
		Log:           logger,
		DisableTyping: true,
	})

	client := &Client{inner: inner, log: logger, onEvent: options.OnEvent}
	inner.SetEventHandler(func(_ context.Context, event slidetypes.ClientEvent) error {
		client.handleEvent(event)
		return nil
	})

	return client, nil
}

func (c *Client) LoadMailbox(ctx context.Context) (*MailboxView, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}

	viewer, mailbox, err := c.inner.LoadIndex(ctx)
	if err != nil {
		return nil, err
	}
	if !validViewer(viewer) {
		return nil, errors.New("Instagram did not return a valid viewer; the session may be expired")
	}
	if mailbox == nil {
		return nil, errors.New("Instagram returned an empty mailbox")
	}

	ownFBID := c.recordViewer(viewer)
	return newMailboxView(viewer, mailbox, ownFBID), nil
}

// LoadThread fetches the newest page of a thread and normalizes it. threadID is
// the short IG chat ID from MailboxView.Threads[].ID, which instameow sends as
// the thread_fbid GraphQL variable.
func (c *Client) LoadThread(ctx context.Context, threadID string, limit int) (*ThreadDetailView, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}

	threadID = strings.TrimSpace(threadID)
	if threadID == "" {
		return nil, errors.New("thread ID is required")
	}
	if limit <= 0 {
		limit = 20
	}
	if limit > 100 {
		limit = 100
	}

	ownFBID, err := c.bootstrap(ctx)
	if err != nil {
		return nil, err
	}

	request := slidetypes.MakeGetThreadInfoRequest(threadID)
	request.InitialMessagePageCount = limit

	response, err := c.inner.GetThread(ctx, request)
	if err != nil {
		return nil, err
	}
	if response == nil || response.ThreadInfo.AsIGDirectThread == nil {
		return nil, errors.New("Instagram returned an empty thread")
	}

	thread := response.ThreadInfo.AsIGDirectThread
	if ownFBID == 0 && thread.Viewer != nil {
		ownFBID = thread.Viewer.InteropMessagingUserFBID
	}

	return newThreadDetailView(thread, ownFBID, limit), nil
}

// LoadOlderMessages fetches the page after afterCursor using
// IGDMessageListOffMsysQuery. The cursor is the oldest_cursor returned by a
// previous thread page.
func (c *Client) LoadOlderMessages(ctx context.Context, threadID, afterCursor string, limit int) (*ThreadPageView, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}

	threadID = strings.TrimSpace(threadID)
	afterCursor = strings.TrimSpace(afterCursor)
	if threadID == "" {
		return nil, errors.New("thread ID is required")
	}
	if afterCursor == "" {
		return nil, errors.New("after cursor is required")
	}
	if limit <= 0 {
		limit = 20
	}
	if limit > 100 {
		limit = 100
	}

	ownFBID, err := c.bootstrap(ctx)
	if err != nil {
		return nil, err
	}

	response, err := c.inner.PaginateMessages(ctx, buildPaginateMessagesRequest(threadID, afterCursor, limit))
	if err != nil {
		return nil, err
	}
	if response == nil || response.ThreadInfo.AsIGDirectThread == nil {
		return nil, errors.New("Instagram returned an empty message page")
	}

	page := response.ThreadInfo.AsIGDirectThread
	messages, hasMoreOlder, oldestCursor := normalizeMessagePage(page.Messages, ownFBID, nil, limit)
	return &ThreadPageView{
		ProtocolVersion: ProtocolVersion,
		ThreadID:        threadID,
		Messages:        messages,
		HasMoreOlder:    hasMoreOlder,
		OldestCursor:    oldestCursor,
	}, nil
}

func buildPaginateMessagesRequest(threadID, afterCursor string, limit int) *slidetypes.PaginateMessagesRequest {
	after := afterCursor
	return &slidetypes.PaginateMessagesRequest{
		AfterCursor:             &after,
		FirstN:                  limit,
		ThreadID:                threadID,
		InitialMessagePageCount: limit,
	}
}

// bootstrap validates the cookies and parses the inbox page once per client.
// LoadMailbox does this implicitly; LoadThread calls it so a standalone thread
// command does not rely on empty bootstrap configs.
func (c *Client) bootstrap(ctx context.Context) (int64, error) {
	c.mu.Lock()
	if c.bootstrapped {
		ownFBID := c.ownFBID
		c.mu.Unlock()
		return ownFBID, nil
	}
	c.mu.Unlock()

	viewer, _, err := c.inner.LoadIndex(ctx)
	if err != nil {
		return 0, err
	}
	if !validViewer(viewer) {
		return 0, errors.New("Instagram did not return a valid viewer; the session may be expired")
	}

	ownFBID := c.resolveOwnFBID(viewer)
	c.mu.Lock()
	c.ownFBID = ownFBID
	c.bootstrapped = true
	c.mu.Unlock()
	return ownFBID, nil
}

func (c *Client) recordViewer(viewer *types.PolarisViewer) int64 {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.ownFBID = c.resolveOwnFBID(viewer)
	c.bootstrapped = true
	return c.ownFBID
}

// resolveOwnFBID prefers the internal EIMU ID used by message senders and
// participants. PolarisViewer.Data.Fbid is the profile ID and can be a
// different value.
func (c *Client) resolveOwnFBID(viewer *types.PolarisViewer) int64 {
	if ownFBID := c.inner.GetOwnFBID(); ownFBID != 0 {
		return ownFBID
	}
	if viewer != nil {
		return viewer.GetFBID()
	}
	return 0
}

func validViewer(viewer *types.PolarisViewer) bool {
	return viewer != nil && viewer.ID != "" && viewer.Data.Fbid != ""
}

// Bootstrap validates the cookies and parses the inbox page without opening
// the DGW socket. It is a no-op after the first successful call.
func (c *Client) Bootstrap(ctx context.Context) error {
	if c == nil || c.inner == nil {
		return errors.New("client is not initialized")
	}
	_, err := c.bootstrap(ctx)
	return err
}

// Watch connects to DGW and blocks until ctx is canceled. It reconnects after
// clean socket closes and re-bootstraps if Instagram requests a resnapshot.
// Events are delivered through Options.OnEvent.
func (c *Client) Watch(ctx context.Context) error {
	if c == nil || c.inner == nil {
		return errors.New("client is not initialized")
	}
	if _, err := c.bootstrap(ctx); err != nil {
		return err
	}

	for {
		c.inner.Connect(ctx)
		if ctx.Err() != nil {
			return nil
		}

		// Connect returns on a clean close or after a resnapshot request.
		// Refresh the bootstrap and sequence cursor before reconnecting.
		c.markUnbootstrapped()
		select {
		case <-ctx.Done():
			return nil
		case <-time.After(2 * time.Second):
		}
		if _, err := c.bootstrap(ctx); err != nil {
			return err
		}
	}
}

func (c *Client) markUnbootstrapped() {
	c.mu.Lock()
	c.bootstrapped = false
	c.mu.Unlock()
}

const MaxTextLength = 1000

// NewOTID returns a new epoch-based offline threading ID.
func NewOTID() string {
	return strconv.FormatInt(methods.GenerateEpochID(), 10)
}

type SendResult struct {
	ProtocolVersion    string `json:"protocol_version"`
	ThreadID           string `json:"thread_id"`
	OfflineThreadingID string `json:"offline_threading_id"`
	MessageID          string `json:"message_id,omitempty"`
	ID                 string `json:"id,omitempty"`
	TimestampMS        int64  `json:"timestamp_ms,omitempty"`
	Text               string `json:"text"`
}

// SendText sends a plain text message to an existing thread. threadID is the
// short IG chat ID (ThreadInfo.ID). otid may be empty, in which case a new
// epoch/OTID is generated; pass the same value when retrying a send.
func (c *Client) SendText(ctx context.Context, threadID, text, replyTo, otid string) (*SendResult, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}

	request, err := buildSendTextRequest(threadID, text, replyTo, otid)
	if err != nil {
		return nil, err
	}
	if _, err := c.bootstrap(ctx); err != nil {
		return nil, err
	}

	response, err := c.inner.SendMessage(ctx, request)
	if err != nil {
		return nil, err
	}
	if response == nil {
		return nil, errors.New("Instagram returned an empty send response")
	}

	return newSendResult(strings.TrimSpace(threadID), request.OfflineThreadingID, text, response.Message), nil
}

func buildSendTextRequest(threadID, text, replyTo, otid string) (*slidetypes.SendTextRequest, error) {
	threadID = strings.TrimSpace(threadID)
	if threadID == "" {
		return nil, errors.New("thread ID is required")
	}
	if strings.TrimSpace(text) == "" {
		return nil, errors.New("text is required")
	}
	if length := len([]rune(text)); length > MaxTextLength {
		return nil, fmt.Errorf("text is %d characters; the maximum is %d", length, MaxTextLength)
	}
	if otid == "" {
		otid = strconv.FormatInt(methods.GenerateEpochID(), 10)
	}
	request := newSendTextRequest(threadID, text, otid)
	if replyTo != "" {
		request.ReplyToMessageID = &replyTo
	}
	return request, nil
}

func newSendTextRequest(threadID, text, otid string) *slidetypes.SendTextRequest {
	threadIDCopy := threadID
	attribution := "igd_web_chat_tab:in_thread"
	return &slidetypes.SendTextRequest{
		IGThreadIGID:       &threadIDCopy,
		OfflineThreadingID: otid,
		RecipientIGIDs:     []string{},
		Text:               slidetypes.SensitiveString{Value: text},
		Mentions:           []slidetypes.InputMention{},
		MentionedUserIDs:   []string{},
		Commands:           []slidetypes.InputCommand{},
		SendAttribution:    &attribution,
	}
}

func newSendResult(threadID, otid, text string, message slidetypes.SentMessage) *SendResult {
	var timestampMS int64
	if !message.TimestampMS.IsZero() {
		timestampMS = message.TimestampMS.UnixMilli()
	}
	return &SendResult{
		ProtocolVersion:    ProtocolVersion,
		ThreadID:           threadID,
		OfflineThreadingID: otid,
		MessageID:          message.MessageID,
		ID:                 message.ID,
		TimestampMS:        timestampMS,
		Text:               text,
	}
}

// Close is a no-op today. The read-only paths do not hold a socket or
// background goroutine; Watch does, and exits when its context is canceled.
func (c *Client) Close() {}
