// Package mobile exposes a JSON-only, gomobile-friendly wrapper around igcore.
// All methods run with their own timeout and never return cookies or tokens.
package mobile

import (
	"context"
	"encoding/json"
	"errors"
	"os"
	"time"

	"github.com/rs/zerolog"

	"github.com/goldrift/instadm/go-core/igcore"
)

type Client struct {
	inner *igcore.Client
}

// New creates a client from the same cookie JSON/header formats as the CLI.
func New(cookiesJSON string) (*Client, error) {
	cookies, err := igcore.ParseCookies([]byte(cookiesJSON))
	if err != nil {
		return nil, err
	}
	inner, err := igcore.New(cookies, igcore.Options{
		LogLevel:  zerolog.WarnLevel,
		LogOutput: os.Stderr,
	})
	if err != nil {
		return nil, err
	}
	return &Client{inner: inner}, nil
}

func Version() string {
	return igcore.ProtocolVersion
}

func (c *Client) Close() {
	if c != nil && c.inner != nil {
		c.inner.Close()
	}
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
