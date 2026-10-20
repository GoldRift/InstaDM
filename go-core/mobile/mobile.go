// Package mobile exposes a JSON-only, gomobile-friendly wrapper around igcore.
// Request methods run with their own timeout; StartWatch streams JSON events on
// a callback until StopWatch. Nothing returns cookies or bootstrap tokens.
package mobile

import (
	"context"
	"encoding/json"
	"errors"
	"os"
	"sync"
	"time"

	"github.com/rs/zerolog"

	"github.com/goldrift/instadm/go-core/igcore"
)

// WatchCallback receives normalized event JSON from StartWatch. Callbacks run
// on a Go-owned thread, so implementations must not block for long.
type WatchCallback interface {
	OnEvent(eventJSON string)
}

type Client struct {
	inner *igcore.Client

	watchMu      sync.Mutex
	watchCancel  context.CancelFunc
	watchDone    chan struct{}
	watchHandler WatchCallback
}

// New creates a client from the same cookie JSON/header formats as the CLI.
func New(cookiesJSON string) (*Client, error) {
	cookies, err := igcore.ParseCookies([]byte(cookiesJSON))
	if err != nil {
		return nil, err
	}

	client := &Client{}
	inner, err := igcore.New(cookies, igcore.Options{
		LogLevel:  zerolog.WarnLevel,
		LogOutput: os.Stderr,
		OnEvent:   client.handleEvent,
	})
	if err != nil {
		return nil, err
	}
	client.inner = inner
	return client, nil
}

// handleEvent forwards protocol events to the active watch callback. The
// callback is nil between StopWatch and the next StartWatch.
func (c *Client) handleEvent(event igcore.Event) {
	c.watchMu.Lock()
	callback := c.watchHandler
	c.watchMu.Unlock()
	if callback == nil {
		return
	}
	emitWatchEvent(callback, event)
}

func Version() string {
	return igcore.ProtocolVersion
}

// StartWatch connects to DGW and delivers normalized event JSON to callback
// until StopWatch or Close. It returns after the watch goroutine starts; the
// caller should keep the callback object alive until StopWatch returns.
func (c *Client) StartWatch(callback WatchCallback) error {
	if c == nil || c.inner == nil {
		return errors.New("client is not initialized")
	}
	if callback == nil {
		return errors.New("watch callback is required")
	}

	c.watchMu.Lock()
	if c.watchCancel != nil {
		c.watchMu.Unlock()
		return errors.New("watch is already running")
	}
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	c.watchCancel = cancel
	c.watchDone = done
	c.watchHandler = callback
	c.watchMu.Unlock()

	go func() {
		defer close(done)

		err := c.inner.Watch(ctx)

		c.watchMu.Lock()
		if c.watchDone == done {
			c.watchCancel = nil
			c.watchDone = nil
			c.watchHandler = nil
		}
		c.watchMu.Unlock()

		if err != nil && ctx.Err() == nil {
			emitWatchEvent(callback, igcore.Event{
				Type:  "watch.error",
				Error: err.Error(),
			})
		}
	}()

	return nil
}

// StopWatch cancels the watch and waits briefly for the socket loop to exit.
// It is safe to call more than once.
func (c *Client) StopWatch() {
	if c == nil {
		return
	}

	c.watchMu.Lock()
	cancel := c.watchCancel
	done := c.watchDone
	c.watchCancel = nil
	c.watchDone = nil
	c.watchHandler = nil
	c.watchMu.Unlock()

	if cancel != nil {
		cancel()
	}
	if done != nil {
		select {
		case <-done:
		case <-time.After(3 * time.Second):
		}
	}
}

func (c *Client) Close() {
	if c == nil {
		return
	}
	c.StopWatch()
	if c.inner != nil {
		c.inner.Close()
	}
}

func emitWatchEvent(callback WatchCallback, event igcore.Event) {
	data, err := json.Marshal(event)
	if err != nil {
		return
	}
	callback.OnEvent(string(data))
}

func (c *Client) MailboxJSON(timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	view, err := client.LoadMailbox(ctx)
	if err != nil {
		return "", err
	}
	return encode(view)
}

func (c *Client) ThreadJSON(threadID string, limit int32, afterCursor string, timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	if afterCursor != "" {
		page, err := client.LoadOlderMessages(ctx, threadID, afterCursor, int(limit))
		if err != nil {
			return "", err
		}
		return encode(page)
	}
	detail, err := client.LoadThread(ctx, threadID, int(limit))
	if err != nil {
		return "", err
	}
	return encode(detail)
}

func (c *Client) SendTextJSON(threadID, text, replyTo, otid string, timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	result, err := client.SendText(ctx, threadID, text, replyTo, otid)
	if err != nil {
		return "", err
	}
	return encode(result)
}

func (c *Client) SendNewDMJSON(recipientFBID, text, otid string, timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	result, err := client.SendNewDM(ctx, recipientFBID, text, otid)
	if err != nil {
		return "", err
	}
	return encode(result)
}

func (c *Client) EditJSON(threadID, messageID, text, otid string, timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	result, err := client.EditText(ctx, threadID, messageID, text, otid)
	if err != nil {
		return "", err
	}
	return encode(result)
}

func (c *Client) UnsendJSON(threadID, messageID string, timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	result, err := client.UnsendMessage(ctx, threadID, messageID)
	if err != nil {
		return "", err
	}
	return encode(result)
}

func (c *Client) MuteJSON(threadID string, muteSeconds int32, timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	result, err := client.SetMuted(ctx, threadID, int(muteSeconds))
	if err != nil {
		return "", err
	}
	return encode(result)
}

func (c *Client) PinJSON(threadID string, pin bool, timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	result, err := client.SetPinned(ctx, threadID, pin)
	if err != nil {
		return "", err
	}
	return encode(result)
}

func (c *Client) SendMediaJSON(threadID, filename, mimeType string, data []byte, replyTo, otid string, timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	result, err := client.SendMediaBytes(ctx, threadID, filename, mimeType, data, replyTo, otid)
	if err != nil {
		return "", err
	}
	return encode(result)
}

func (c *Client) ReactJSON(threadID, messageID, emoji string, remove bool, timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	result, err := client.SendReaction(ctx, threadID, messageID, emoji, remove)
	if err != nil {
		return "", err
	}
	return encode(result)
}

func (c *Client) MarkReadJSON(threadID, messageID string, timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	result, err := client.MarkThreadRead(ctx, threadID, messageID)
	if err != nil {
		return "", err
	}
	return encode(result)
}

func (c *Client) DownloadMediaJSON(threadID, messageID string, part int32, destPath string, timeoutSeconds int32) (string, error) {
	client, err := c.client()
	if err != nil {
		return "", err
	}
	ctx, cancel := timeoutContext(timeoutSeconds)
	defer cancel()

	result, err := client.DownloadMediaPart(ctx, threadID, messageID, int(part), destPath)
	if err != nil {
		return "", err
	}
	return encode(result)
}

func (c *Client) client() (*igcore.Client, error) {
	if c == nil || c.inner == nil {
		return nil, errors.New("client is not initialized")
	}
	return c.inner, nil
}

func encode(value any) (string, error) {
	data, err := json.Marshal(value)
	if err != nil {
		return "", err
	}
	return string(data), nil
}

func timeoutContext(seconds int32) (context.Context, context.CancelFunc) {
	if seconds <= 0 {
		seconds = 90
	}
	return context.WithTimeout(context.Background(), time.Duration(seconds)*time.Second)
}
