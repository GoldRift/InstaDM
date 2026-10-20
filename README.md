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

## Runtime smoke test

With the API 37 emulator running, install the debug app and push the cookie file
to the app's external files directory:

```bash
adb shell mkdir -p /sdcard/Android/data/com.goldrift.instadm/files
adb push ~/.config/instadm/cookies.json \
  /sdcard/Android/data/com.goldrift.instadm/files/cookies.json

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.goldrift.instadm.GoBindingSmokeTest
```

The test skips if the cookie file is missing. It creates the Go client, loads the
mailbox, and fetches the first thread.

## Status

The Go core implements text and media send/download, reactions, edits, unsend,
mark-read, mute/pin, new DMs, reconnect backoff, and a durable send outbox. The
AAR builds, the Kotlin wrapper compiles, and the instrumented mailbox/thread smoke
test passes on the `Medium_Phone_API_37.0` emulator. Wiring the demo inbox and chat
screens to the JSON client is the next step.

## License

InstaDM is licensed under the GNU Affero General Public License, version 3 or
later. See [`LICENSE`](LICENSE).

Third-party components and their licenses are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
