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

## Status

The Go core implements text and media send/download, reactions, edits, unsend,
mark-read, mute/pin, new DMs, reconnect backoff, and a durable send outbox. The
Android app builds with the AAR; wiring the inbox and chat screens to the JSON
client is in progress.

## License

InstaDM is licensed under the GNU Affero General Public License, version 3 or
later. See [`LICENSE`](LICENSE).

Third-party components and their licenses are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
