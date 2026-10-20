package igcore

import (
	"testing"
	"time"
)

func TestThreadCacheStoresMessagesAndLongID(t *testing.T) {
	cache := newThreadCache()
	cache.putThread("thread", "long-thread", []MessageView{
		{ID: "m1", TimestampMS: 1000},
		{ID: "m2", TimestampMS: 2000},
	})

	message, ok := cache.lookupMessage("thread", "m2", threadCacheTTL)
	if !ok {
		t.Fatal("cached message not found")
	}
	if message.TimestampMS != 2000 {
		t.Errorf("timestamp = %d, want 2000", message.TimestampMS)
	}
	if got := cache.longThreadID("thread"); got != "long-thread" {
		t.Errorf("long thread ID = %q, want long-thread", got)
	}
	if _, ok := cache.lookupMessage("thread", "missing", threadCacheTTL); ok {
		t.Error("missing message found")
	}
}

func TestThreadCacheExpiresMessagesButKeepsLongID(t *testing.T) {
	current := time.Unix(1_700_000_000, 0)
	cache := newThreadCache()
	cache.now = func() time.Time { return current }
	cache.putThread("thread", "long-thread", []MessageView{{ID: "m1", TimestampMS: 1000}})

	current = current.Add(threadCacheTTL + time.Second)
	if _, ok := cache.lookupMessage("thread", "m1", threadCacheTTL); ok {
		t.Error("expired message still found")
	}
	if got := cache.longThreadID("thread"); got != "long-thread" {
		t.Errorf("long thread ID after expiry = %q", got)
	}
}

func TestThreadCacheMergesOlderPages(t *testing.T) {
	cache := newThreadCache()
	cache.putThread("thread", "long-thread", []MessageView{{ID: "m2", TimestampMS: 2000}})
	cache.putMessages("thread", []MessageView{{ID: "m1", TimestampMS: 1000}})

	if _, ok := cache.lookupMessage("thread", "m2", threadCacheTTL); !ok {
		t.Error("newest message lost after merging an older page")
	}
	if _, ok := cache.lookupMessage("thread", "m1", threadCacheTTL); !ok {
		t.Error("older page message not cached")
	}
	if got := cache.longThreadID("thread"); got != "long-thread" {
		t.Errorf("long thread ID = %q after merge", got)
	}
}

func TestThreadCacheEvictsOldestThread(t *testing.T) {
	current := time.Unix(1_700_000_000, 0)
	cache := newThreadCache()
	cache.now = func() time.Time { return current }
	for i := 0; i < threadCacheMax+1; i++ {
		current = current.Add(time.Second)
		cache.putThread(string(rune('a'+i)), "", []MessageView{{ID: "m", TimestampMS: 1}})
	}

	if _, ok := cache.lookupMessage("a", "m", threadCacheTTL); ok {
		t.Error("oldest thread was not evicted")
	}
	if _, ok := cache.lookupMessage("b", "m", threadCacheTTL); !ok {
		t.Error("newer thread missing")
	}
	if len(cache.entries) != threadCacheMax {
		t.Errorf("cache holds %d entries, want %d", len(cache.entries), threadCacheMax)
	}
}

func TestThreadCacheTrimsMessagePages(t *testing.T) {
	cache := newThreadCache()
	messages := make([]MessageView, 0, threadCacheMaxMsgs+5)
	for i := 0; i < threadCacheMaxMsgs+5; i++ {
		messages = append(messages, MessageView{ID: string(rune(i + 1)), TimestampMS: int64(i + 1)})
	}
	cache.putThread("thread", "", messages)

	entry := cache.entries["thread"]
	if entry == nil {
		t.Fatal("thread not cached")
	}
	if len(entry.messages) != threadCacheMaxMsgs {
		t.Errorf("cached %d messages, want %d", len(entry.messages), threadCacheMaxMsgs)
	}
	if _, ok := entry.messages[string(rune(1))]; ok {
		t.Error("oldest message was not trimmed")
	}
}

func TestCachedMarkReadTarget(t *testing.T) {
	client := &Client{cache: newThreadCache()}
	client.cache.putThread("thread", "long-thread", []MessageView{{ID: "m1", TimestampMS: 1000}})

	longID, target, ok := client.cachedMarkReadTarget("thread", "m1")
	if !ok {
		t.Fatal("cache miss for mark-read target")
	}
	if longID != "long-thread" || target.ID != "m1" || target.TimestampMS != 1000 {
		t.Errorf("target = %q/%q/%d", longID, target.ID, target.TimestampMS)
	}

	if _, _, ok := client.cachedMarkReadTarget("thread", "missing"); ok {
		t.Error("missing message produced a cached target")
	}

	client.cache.putThread("no-timestamp", "long-thread", []MessageView{{ID: "m1"}})
	if _, _, ok := client.cachedMarkReadTarget("no-timestamp", "m1"); ok {
		t.Error("message without a timestamp produced a cached target")
	}
}
