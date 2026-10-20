# InstaDM

An Android Instagram DM client built around a Go protocol core.

- [`go-core/`](go-core/) — Go protocol core and CLI: cookie validation, mailbox,
  thread read, text/media send, reactions, edits, unsend, mark-read, media
  download, mute/pin, new DMs, a durable outbox, and DGW realtime watch. See
  [`go-core/README.md`](go-core/README.md).
- [`app/`](app/) — Android Jetpack Compose app. It embeds the Go core through the
  generated `igcore.aar`, loads the real inbox and threads, sends text, renders
  inline image/video attachments, and keeps a realtime watch alive in a foreground
  service.

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

The debug app reads the session from the app-specific external files directory.
With an emulator or device connected:

```bash
adb shell mkdir -p /sdcard/Android/data/com.goldrift.instadm/files
adb push ~/.config/instadm/cookies.json \
  /sdcard/Android/data/com.goldrift.instadm/files/cookies.json

./gradlew :app:installDebug
adb shell am start -n com.goldrift.instadm/.MainActivity
```

The inbox loads the mailbox from the Go core. Opening a thread fetches its newest
20 messages; sending text appends the server message after the mutation succeeds.
Image, GIF, sticker, and video attachments render inline. Tapping an attachment
opens a full-screen viewer; it downloads the original through the Go core into the
app cache (so expired thumbnail URLs still work) and plays videos with the platform
player, including looping GIFs.
Use the refresh button after replacing the cookie file. If the file is missing, the
app shows the exact path it expects.

The app starts a `dataSync`/`remoteMessaging` foreground service that keeps the DGW
watch connected while the UI is in the background. The status under the app name shows
`Live`, `Connecting…`, `Reconnecting…`, `No realtime session`, or `Realtime error`.
Incoming messages update the open thread and inbox immediately. When the app is in the
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

## Status

The Go core implements text and media send/download, reactions, edits, unsend,
mark-read, mute/pin, new DMs, reconnect backoff, and a durable send outbox. The
Android inbox and chat load real mailbox/thread JSON, send text through the AAR,
apply realtime events from a foreground watch service, and render inline media with
full-screen image and video viewers. Older-message paging in the UI and an in-app
session screen are next.

## License

InstaDM is licensed under the GNU Affero General Public License, version 3 or
later. See [`LICENSE`](LICENSE).

Third-party components and their licenses are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
