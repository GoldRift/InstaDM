# InstaDM

An Android Instagram DM client built around a Go protocol core.

- [`go-core/`](go-core/) — Go protocol core and CLI: cookie validation, mailbox,
  thread read, text/media send, reactions, edits, unsend, mark-read, media
  download, mute/pin, new DMs, a durable outbox, and DGW realtime watch. See
  [`go-core/README.md`](go-core/README.md).
- [`app/`](app/) — Android Jetpack Compose app. It embeds the Go core through the
  generated `igcore.aar`; connecting the demo UI to the protocol client is the
  next step.

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
Use the refresh button after replacing the cookie file. If the file is missing, the
app shows the exact path it expects.

## Runtime smoke test

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.goldrift.instadm.GoBindingSmokeTest
```

The test skips if the cookie file is missing. It creates the Go client, loads the
mailbox, and fetches the first thread.

## Status

The Go core implements text and media send/download, reactions, edits, unsend,
mark-read, mute/pin, new DMs, reconnect backoff, and a durable send outbox. The
Android inbox and chat now load real mailbox/thread JSON and send text through the
AAR. Media rendering, realtime updates, and an in-app session screen are next.

## License

InstaDM is licensed under the GNU Affero General Public License, version 3 or
later. See [`LICENSE`](LICENSE).

Third-party components and their licenses are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
