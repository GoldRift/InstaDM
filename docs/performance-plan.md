# Performance Plan

Prioritized backlog for UI, data, media, protocol, and startup performance.
Mark items done as they land and record any deferred follow-ups inline.

## Measurement

Profile and compare on the `dev` build type (`optimization { enable = true }`),
not `debug`:

- Frame timing: `adb shell dumpsys gfxinfo com.goldrift.instadm.dev framestats`
  or Perfetto system trace while scrolling inbox and chat.
- Cold start: `adb shell am start-activity -W -n com.goldrift.instadm.dev/com.goldrift.instadm.MainActivity`.
- Memory peak: Android Studio Profiler or `dumpsys meminfo` during a 20+ MiB
  upload and multi-GIF transcript scroll.

---

## Phase 1: Chat and Inbox Scroll Smoothness

- [x] **1.1 Remove per-frame `visibleMessageIds` churn in `MessageList`**
  - Files: `app/src/main/java/com/goldrift/instadm/ui/chat/ChatScreen.kt`,
    `MediaViewer.kt`, `VoiceNote.kt`.
  - Problem: `visibleMessageIds` allocates a fresh `HashSet` on every scroll
    offset change and reading it in `itemsIndexed` recomposes every visible
    `MessageRow` whenever an item enters or leaves the viewport.
  - Change: Remove `visibleMessageIds` and `isVisible` plumbing from
    `MessageList`, `MessageRow`, and `MessageBubbleContent`. Track visibility
    locally via `Modifier.onVisibilityChanged` in `InlineGif` and
    `VoiceNoteBubble`.

- [x] **1.2 Remove `SubcomposeLayout` from `LazyColumn` items and `MainPane`**
  - Files: `MediaViewer.kt`, `LinkPreview.kt`, `InstaDmApp.kt`.
  - Problem: `ThumbnailImage`, `StickerImage`, `LocalVisualThumbnail`, and
    `SharePreviewImage` use `SubcomposeAsyncImage`; `LinkPreview` uses
    `BoxWithConstraints` for max-height aspect-ratio clamping; `MainPane` nests
    a second `BoxWithConstraints` inside `InstaDmApp`.
  - Change: Replace `SubcomposeAsyncImage` with `AsyncImage` + `onState`
    overlay states. Replace `BoxWithConstraints` in `LinkPreview` with
    `Modifier.layout`. Pass `wide` from `InstaDmApp` into `MainPane`.

- [ ] **1.3 Cut per-item scroll allocations, linear lookups, and main-thread `File.isFile`**
  - Files: `ChatModels.kt`, `TranscriptLayout.kt`, `ChatScreen.kt`,
    `VoiceNote.kt`, `Avatar.kt`.
  - Changes:
    - Replace `Calendar.getInstance()` in `calendarDaysBetween` with
      allocation-free `TimeZone.getOffset()` day math.
    - Compute divider, cluster position, and `joinedWithPrevious` without
      redundant `sameSenderCluster` / `transcriptDividerBefore` calls per item.
    - Remove synchronous `File.isFile` calls from composition in
      `ChatMessage.replyThumbnail()` and `VoiceNoteBubble` (`localVoiceFile`).
    - Index `participants` by `fbid` (`remember(participants) { ... }`) in
      `MessageList` and `ReactionPills` instead of linear `firstOrNull`.
    - Hoist the 7 `bubbleShape` `RoundedCornerShape` instances to constants.
    - Pass `imageUrl` directly to `AsyncImage` in `Avatar` and `CollageTile`
      instead of building a new `ImageRequest` on every recomposition.
    - Refine `MessageList` `contentType` by message shape (admin, text, media,
      voice, share, sticker, jumbo emoji) and sender for better slot reuse.
    - Remove unconditional `LaunchedEffect(Unit)` entrance animations on
      `ReactionPill` so scrolling past old reacted messages does not spawn
      animations.

- [ ] **1.4 Defer animation and gesture reads to Layout and Draw**
  - Files: `ChatScreen.kt`, `VoiceNote.kt`, `MiniVoicePlayer.kt`,
    `MediaViewerOverlay.kt`.
  - Changes:
    - Swipe-to-reply in `MessageRow`: keep drag offset in `Animatable`, read
      in `Modifier.offset { ... }`, and pass `progress: () -> Float` to
      `SwipeReplyIcon`.
    - Composer height in `ChatScreen`: replace `Modifier.onGloballyPositioned`
      with `Modifier.onSizeChanged` (deduplicating unchanged heights).
    - Voice playback in `VoiceNoteBubble` and `MiniVoicePlayer`: pass
      `progress: () -> Float` into `WaveformBars` (read in draw) and derive
      whole-second countdown text with `derivedStateOf` so text updates at 1 Hz
      instead of 12.5 Hz.
    - Pinch-to-zoom in `ZoomableImagePage`: replace
      `LaunchedEffect(scale.value)` with `snapshotFlow` observing the zoomed
      boolean threshold so zoom stays in `graphicsLayer`.

---

## Phase 2: State Architecture, Background Mapping, and Room Scaling

- [ ] **2.1 Decouple open-thread messages from `InboxState.items`**
  - Files: `InboxState.kt`, `InstaDmApp.kt`, `ThreadDao.kt`.
  - Problem: `items` (`SnapshotStateList<Conversation>`) embeds open-thread
    messages, so message updates invalidate the inbox and inbox/unread
    emissions run `items.clear(); items.addAll(mapped)`.
  - Change: Keep active-thread messages in separate state from inbox summaries,
    swap immutable lists instead of `clear()`/`addAll()`, and apply
    `.distinctUntilChanged()` to `observeUnreadCounts()`.

- [ ] **2.2 Move Room-to-UI mapping off `Dispatchers.Main.immediate` and cache formatters**
  - Files: `InboxState.kt`, `ProtocolMapper.kt`, `ChatModels.kt`,
    `TranscriptLayout.kt`.
  - Change: Map Room flows on `Dispatchers.Default` (including reaction-log
    filtering, `messagesById`, and transcript layout metadata). Cache/reuse
    date formatters in `formatMessageTime`, `formatInboxTime`, and
    `formatRevealedTimestamp`, and hoist `Regex("\\s+")` in `initialsOf`.

- [ ] **2.3 Window `MessageDao.observeThread`**
  - Files: `MessageDao.kt`, `ChatStore.kt`, `InboxState.kt`.
  - Change: Query the newest `LIMIT :limit` non-deleted, non-reaction-log rows
    and expand the local window on scroll/quote-jump before fetching older
    network pages, instead of reloading up to 10,000 rows on every table write.

- [ ] **2.4 Batch N+1 queries in `ChatStore` and parse JSON outside transactions**
  - Files: `ChatStore.kt`, `MessageDao.kt`, `ThreadDao.kt`.
  - Change: Parse JSON payloads on `Dispatchers.Default` before
    `db.withTransaction`. Batch `IN (:ids)` / `IN (:otids)` lookups and batch
    attachment/reaction deletes and upserts; skip deletes when nothing exists
    or arrives.

---

## Phase 3: Concurrency, Memory, Media, and Startup

- [ ] **3.1 Unblock interactive RPCs from media downloads/uploads in `ProtocolRepository`**
  - Files: `ProtocolRepository.kt` (and verify `igcore` / `mautrix-meta` concurrency).
  - Change: Check `cachedMedia` before acquiring locks. Allow concurrent HTTP
    RPCs and media transfers without stalling text sends, thread loads,
    reactions, or read receipts.

- [ ] **3.2 Stream media uploads by file path instead of 50+ MiB RAM copies**
  - Files: `PickedMedia.kt`, `Outbox.kt`, `GoClient.kt`, `ProtocolRepository.kt`,
    `go-core/mobile/mobile.go`.
  - Change: Stream `ContentResolver` directly into `outboxDir` while enforcing
    size limits, and pass file paths across gomobile (`SendMediaPathJSON` /
    `SendVoicePathJSON`) instead of `ByteArray`.

- [ ] **3.3 Move remaining main-thread disk I/O and media lifecycle off UI thread**
  - Files: `Outbox.kt`, `VoiceNote.kt`, `VoiceRecorder.kt`.
  - Changes:
    - Wrap `pruneMediaCache` and `File.isFile` checks in `Outbox` with
      `withContext(Dispatchers.IO)`.
    - Move `VoicePlayer`'s `MediaPlayer.prepare()` (use `prepareAsync()`) and
      `release()` off the main thread.
    - Move `VoiceRecorder`'s `prune`, `prepare`, `stop`, `release`, and
      `file.length()` checks off the main thread; avoid O(N^2) list copying on
      every amplitude sample; throttle displayed elapsed seconds to 1 Hz.

- [ ] **3.4 Cap inline GIF `ExoPlayer` instances**
  - Files: `MediaViewer.kt`.
  - Change: Initialize `InlineGif` from `media.localPath` when cached, avoid
    creating players during fast scroll, and cap active concurrent inline GIF
    players to 2–3 visible items.

- [ ] **3.5 Trim cold-start work**
  - Files: `InstaDmApp.kt`, `RealtimeService.kt`, `ProtocolRepository.kt`.
  - Changes:
    - Initialize `startRoute` synchronously from
      `application.sessionStore.hasStoredSession()` to avoid the initial
      spinner frame.
    - Reuse `application.sessionStore` inside `RealtimeService` instead of
      creating a new `SessionStore(this)` that re-decrypts Keystore cookies.
    - Evaluate sharing one bootstrapped `GoClient` between `ProtocolRepository`
      and `RealtimeService`.

---

## Phase 4: Build and Runtime Optimizations

- [ ] **4.1 Add Baseline Profiles (`androidx.profileinstaller` + `:baselineprofile`)**
  - Files: `gradle/libs.versions.toml`, `app/build.gradle.kts`, new
    `baselineprofile/` module.
  - Cover cold start, inbox scroll, opening a thread, and transcript scroll.

- [ ] **4.2 Tune Coil caching**
  - Files: `InstaDmApplication.kt`.
  - Configure explicit memory/disk cache bounds and verify CDN cache hit rates
    across signed URL refreshes.
