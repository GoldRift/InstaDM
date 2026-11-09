package igcore

import (
	"context"
	"errors"
	"strings"
	"time"
)

// TypingResult is the outcome of one typing signal.
type TypingResult struct {
	ProtocolVersion string `json:"protocol_version"`
	ThreadID        string `json:"thread_id"`
	Typing          bool   `json:"typing"`
}

// SetTyping signals typing in a thread over the mqttbypass socket. threadID is
// the short IG chat ID. The signal is transient: Instagram drops it after a few
// seconds, so callers re-send it while the viewer keeps typing.
//
// This needs a running Watch connection on the same client, because the socket
// lives there; the mqttbypass socket is opened on the first call. Clients that
// are not watching should use SetTypingOnce.
func (c *Client) SetTyping(ctx context.Context, threadID string, typing bool) error {
	if c == nil || c.inner == nil {
		return errors.New("client is not initialized")
	}

	threadID = strings.TrimSpace(threadID)
	if threadID == "" {
		return errors.New("thread ID is required")
	}
	return c.inner.SetTyping(ctx, threadID, typing)
}

// SetTypingOnce opens the DGW socket, sends one typing signal, and closes the
// socket again. The client's typing socket is only created by Connect, so this
// is what a one-shot caller such as the CLI needs.
func (c *Client) SetTypingOnce(ctx context.Context, threadID string, typing bool) error {
	if c == nil || c.inner == nil {
		return errors.New("client is not initialized")
	}

	threadID = strings.TrimSpace(threadID)
	if threadID == "" {
		return errors.New("thread ID is required")
	}
	if _, err := c.bootstrap(ctx); err != nil {
		return err
	}

	connectCtx, cancelConnect := context.WithCancel(ctx)
	defer cancelConnect()
	go c.inner.Connect(connectCtx)

	// Connect publishes its context from its own goroutine, so an early attempt
	// can arrive before the client is ready to send.
	var err error
	for {
		if err = c.inner.SetTyping(ctx, threadID, typing); err == nil {
			return nil
		}
		select {
		case <-ctx.Done():
			return err
		case <-time.After(200 * time.Millisecond):
		}
	}
}
