package igcore

import (
	"strings"
	"sync"
	"time"
)

const (
	threadCacheTTL     = 5 * time.Minute
	threadCacheMax     = 8
	threadCacheMaxMsgs = 200
)

// cachedThread is the most recent page loaded for one short thread ID.
// longID is stable and kept even after the message page expires.
type cachedThread struct {
	fetchedAt time.Time
	longID    string
	messages  map[string]MessageView
}

// threadCache keeps a few recently loaded thread pages so read receipts and
// media downloads can reuse message metadata instead of issuing a fresh
// IGDThreadDetailQuery for every operation.
type threadCache struct {
	mu      sync.Mutex
	entries map[string]*cachedThread
	now     func() time.Time
}

func newThreadCache() *threadCache {
	return &threadCache{
		entries: make(map[string]*cachedThread),
		now:     time.Now,
	}
}

func (c *Client) cacheLoadedThread(threadID string, view *ThreadDetailView) {
	if c == nil || c.cache == nil || view == nil {
		return
	}
	c.cache.putThread(threadID, view.Thread.ThreadID, view.Messages)
}

func (c *Client) cacheOlderMessages(threadID string, view *ThreadPageView) {
	if c == nil || c.cache == nil || view == nil {
		return
	}
	c.cache.putMessages(threadID, view.Messages)
}

// cachedMarkReadTarget returns the long thread ID and message metadata for a
// mark-read without touching the network.
func (c *Client) cachedMarkReadTarget(threadID, messageID string) (string, MessageView, bool) {
	if c == nil || c.cache == nil {
		return "", MessageView{}, false
	}
	longID := c.cache.longThreadID(threadID)
	if longID == "" {
		return "", MessageView{}, false
	}
	message, ok := c.cache.lookupMessage(threadID, messageID, threadCacheTTL)
	if !ok || message.TimestampMS == 0 {
		return "", MessageView{}, false
	}
	return longID, message, true
}

func (tc *threadCache) putThread(threadID, longID string, messages []MessageView) {
	threadID = strings.TrimSpace(threadID)
	if threadID == "" {
		return
	}
	tc.mu.Lock()
	defer tc.mu.Unlock()

	entry := &cachedThread{
		fetchedAt: tc.now(),
		longID:    strings.TrimSpace(longID),
		messages:  make(map[string]MessageView, len(messages)),
	}
	for _, message := range messages {
		if message.ID != "" {
			entry.messages[message.ID] = message
		}
	}
	entry.trim()
	tc.entries[threadID] = entry
	tc.evictLocked()
}

func (tc *threadCache) putMessages(threadID string, messages []MessageView) {
	threadID = strings.TrimSpace(threadID)
	if threadID == "" {
		return
	}
	tc.mu.Lock()
	defer tc.mu.Unlock()

	entry := tc.entries[threadID]
	if entry == nil {
		entry = &cachedThread{messages: make(map[string]MessageView)}
		tc.entries[threadID] = entry
	}
	entry.fetchedAt = tc.now()
	for _, message := range messages {
		if message.ID != "" {
			entry.messages[message.ID] = message
		}
	}
	entry.trim()
	tc.evictLocked()
}

func (tc *threadCache) lookupMessage(threadID, messageID string, ttl time.Duration) (MessageView, bool) {
	tc.mu.Lock()
	defer tc.mu.Unlock()

	entry := tc.entries[strings.TrimSpace(threadID)]
	if entry == nil || tc.expiredLocked(entry, ttl) {
		return MessageView{}, false
	}
	message, ok := entry.messages[strings.TrimSpace(messageID)]
	return message, ok
}

func (tc *threadCache) longThreadID(threadID string) string {
	tc.mu.Lock()
	defer tc.mu.Unlock()

	entry := tc.entries[strings.TrimSpace(threadID)]
	if entry == nil {
		return ""
	}
	return entry.longID
}

func (tc *threadCache) expiredLocked(entry *cachedThread, ttl time.Duration) bool {
	return ttl > 0 && tc.now().Sub(entry.fetchedAt) > ttl
}

// evictLocked keeps the cache bounded to the most recently used threads. The
// map is tiny, so a linear scan is cheaper than maintaining recency order.
func (tc *threadCache) evictLocked() {
	for len(tc.entries) > threadCacheMax {
		var oldestID string
		var oldest time.Time
		first := true
		for id, entry := range tc.entries {
			if first || entry.fetchedAt.Before(oldest) {
				oldestID = id
				oldest = entry.fetchedAt
				first = false
			}
		}
		if oldestID == "" {
			return
		}
		delete(tc.entries, oldestID)
	}
}

func (entry *cachedThread) trim() {
	for len(entry.messages) > threadCacheMaxMsgs {
		var oldestID string
		var oldestTS int64
		first := true
		for id, message := range entry.messages {
			if first || message.TimestampMS < oldestTS {
				oldestID = id
				oldestTS = message.TimestampMS
				first = false
			}
		}
		if oldestID == "" {
			return
		}
		delete(entry.messages, oldestID)
	}
}
