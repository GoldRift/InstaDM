# InstaDM

An Android Instagram DM client built around a Go protocol core.

- [`go-core/`](go-core/) — Go protocol core and CLI: cookie validation, mailbox,
  thread read, text/media send, reactions, edits, unsend, mark-read, media
  download, mute/pin, new DMs, a durable outbox, and DGW realtime watch. See
  [`go-core/README.md`](go-core/README.md).
- [`app/`](app/) — Android Jetpack Compose app. It embeds the Go core through the
  generated `igcore.aar`, loads the real inbox and threads, sends text, media, and
  voice notes from the camera, gallery, or microphone, renders inline image/video/voice
  attachments, and keeps a realtime watch alive in a foreground service.

## Build

The Android AAR is generated locally and is not committed:

```bash
./scripts/build-android-aar.sh
./gradlew :app:assembleDebug
```

The script needs `ANDROID_HOME`, an NDK under `$ANDROID_HOME/ndk`, and `gomobile`
on `PATH`.

The debug APK currently includes all four gomobile ABIs and is about 129 MB.
ABI filters can trim that once the emulator/device targets are fixed.

## First run

On a fresh install the app opens an in-app Instagram login. The page is
Instagram's own login UI inside a WebView, so 2FA, challenges, consent, and
checkpoints all happen there; the app never handles the password. When the
required cookies appear, the Go core validates them by loading the inbox, then
the session is encrypted with a non-exportable Android Keystore key and stored
in the app's no-backup directory.

For development and the runtime smoke test, debuggable builds still accept a
cookie file at the app-specific external path. It is only read when no encrypted
session exists, so replacing it after login requires clearing app data first:

```bash
adb shell mkdir -p /sdcard/Android/data/com.goldrift.instadm/files
adb push ~/.config/instadm/cookies.json \
  /sdcard/Android/data/com.goldrift.instadm/files/cookies.json

./gradlew :app:installDebug
adb shell am start -n com.goldrift.instadm/.MainActivity
```

The debug `cookies.json` is excluded from Android backup. The encrypted session
is kept only in `noBackupFilesDir`.

The inbox loads the mailbox from the Go core and persists it in Room, so a cold start
shows the last conversation list and message previews before the network request
finishes. Previews come from the `last_message` stub Instagram includes in the mailbox;
they are not full messages, so opening a chat still fetches its newest page.
Conversation rows and chat headers show
profile pictures from the mailbox payload: a direct chat uses the other person's picture,
a group uses its custom photo, and a group without one falls back to a collage of its
members. Opening a thread refreshes the title and photo along with the newest messages.
Loaded chats and their paging cursor are stored in Room, so reopening a chat (even
after a restart) paints its messages before the newest page finishes refreshing; a
thread is only re-fetched when its newest page is more than 30 seconds old or the
mailbox shows a newer last message.
Opening a thread fetches its newest
30 messages, and scrolling to the oldest bubble pages in older messages until the
thread begins. Sending text or an attachment writes an optimistic bubble and a durable
outbox row before the network call, so the message survives process death and retries
with the same OTID; a failed send keeps its bubble and can be retried by tapping the
error. Attachments can be captured with the camera button or picked from the system
photo
picker; the composer shows the local copy immediately and clears the pending tick
once the upload finishes. A failed upload keeps the bubble so it can be retried.
The mic sits left of the gallery icon and records a voice note while held. Releasing
sends it, sliding up locks the recorder so the finger can be lifted, and sliding left
cancels. A locked recording can be paused, resumed, deleted, or sent. There is no
duration limit; recording stops cleanly when the file reaches the same 25 MB upload
cap as other attachments. Voice notes are playable inline in both directions, with
tap-to-seek and a waveform; outgoing notes use the local file until Instagram's copy
takes over.
Opening a chat also marks it read, and own messages show a seen tick once another
participant's read receipt reaches them. Realtime read receipts update the open chat
immediately. Image, GIF, sticker, and video attachments render inline, and GIFs
autoplay while their row is visible. Tapping an attachment opens a full-screen
viewer; it downloads the original through the Go core into the app cache (so expired
thumbnail URLs still work) and plays videos with the platform player, including
looping GIFs. Downloaded attachments live in a 250 MB LRU cache; sent attachments move
out of the outbox into that cache once uploaded. The database keeps at most 1,000
messages per thread, and a missing session clears the database, the media cache, and
pending sends.
The refresh button reloads the inbox and reconnects realtime when the stored
session changed. Instagram rotates routing cookies on responses; the Go core
exports the updated jar after successful calls and the app re-encrypts it when
it differs. If no usable session exists, the inbox offers a `Log in` action.

The app starts a `dataSync`/`remoteMessaging` foreground service that keeps the DGW
watch connected while the UI is in the background. The status under the app name shows
`Live`, `Connecting…`, `Reconnecting…`, `No realtime session`, or `Realtime error`.
Incoming messages update the open thread and inbox immediately and are written to Room by
a process-scoped recorder, so new messages, edits, deletes, reactions, and read receipts
survive a restart (and are stored even when the activity was never opened). When the app
is in the
background, they also post a notification in the `Messages` channel; Android 13+ asks
for notification permission on first launch. The notification actions include `Stop`
for the foreground service.

## Runtime smoke test

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.goldrift.instadm.GoBindingSmokeTest
```

The test skips if the cookie file is missing. It creates the Go client, loads the
mailbox, fetches the first thread, and starts the watch until a `connected` event
arrives. `RealtimeEventTest` covers the JSON event parser without needing cookies.

Because `connectedDebugAndroidTest` may reinstall the app and remove its external
files, the reliable real-account path is to install both APKs, push cookies, and run
the instrumentation directly:

```bash
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell mkdir -p /sdcard/Android/data/com.goldrift.instadm/files
adb push ~/.config/instadm/cookies.json \
  /sdcard/Android/data/com.goldrift.instadm/files/cookies.json
adb shell am instrument -w com.goldrift.instadm.test/androidx.test.runner.AndroidJUnitRunner
```

## Media send checks

Uploads reach a live account, so verify the composer and the pickers before sending:

- The empty composer shows the camera circle, `Message…`, the mic, and the gallery
  icon. Typing replaces the left and right actions with the primary send button.
- The camera writes into `cacheDir/outgoing` through the app `FileProvider`; the
  gallery opens the system photo picker.
- Holding the mic records; releasing sends, sliding up locks, sliding left cancels.
  The first use asks for `RECORD_AUDIO`. The locked bar can pause, delete, or send.
- Voice upload uses Instagram's `voice_clip` upload field with a waveform. The
  payload is unit-tested, but a live send has not been exercised yet; check that the
  note renders as a voice bubble in an official client.
- Images are capped at 8 MB and other attachments at 25 MB, mirroring
  `go-core/igcore/media.go`. A larger file is rejected before uploading; a voice
  recording that reaches the cap stops and shows the same message.
- HEIC images and other unsupported MIME types are rejected with a readable error.
- In airplane mode, a picked photo keeps a failed bubble; reconnecting and tapping
  the retry label uploads it.

## Status

The Go core implements text and media send/download, reactions, edits, unsend,
mark-read with read-receipt state, mute/pin, new DMs, reconnect backoff with a
post-subscribe flap brake, a durable send outbox, and cookie-jar export for
session persistence. The Android app adds in-app WebView login with encrypted
session storage, loads real mailbox/thread JSON, sends text, media, and voice notes
through the AAR with optimistic bubbles and retry, applies realtime events from a
foreground watch service, marks open threads read with coalesced receipts, and renders
inline media with full-screen image/video viewers, inline voice playback, and seen
ticks driven by real read receipts.

### Planned session lifecycle

Login is the only session UI in v1. The follow-up work is:

- **Logout**: an inbox overflow action clears the encrypted blob and legacy debug
  file, clears WebView cookies and web storage, closes the Go client, stops the
  foreground realtime service, and returns to the login screen.
  `SessionStore.clearCookies()` and `clearWebViewSession()` are already in place.
- **Session expiry**: recognize the Go core's invalid-viewer/auth failures, clear
  the stored session, stop the realtime service, and route to login instead of
  retrying the same expired cookies. Until then the inbox error state offers a
  manual `Log in again` action.
- **Account switching** stays out of scope: WebView cookie storage and the
  realtime socket are single-account.

Instagram can still challenge or block an embedded browser. The login uses a
desktop Chrome user agent because Instagram's mobile Bloks login page renders
with a zero-height root in Android WebView; at phone widths the desktop page
shows a short "Log in" landing before the normal username/password form. The
WebView enables third-party cookies and DOM storage and never injects scripts
into the page. If an account is challenged in the WebView, the native
web/password flow remains the fallback.

## License

InstaDM is licensed under the GNU Affero General Public License, version 3 or
later. See [`LICENSE`](LICENSE).

Third-party components and their licenses are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
