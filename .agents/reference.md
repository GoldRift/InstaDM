# instadm Reference

On-demand architecture and operational details. Read only when working on the relevant area.

## Android architecture details

- **Login & session**: `ui/login/LoginScreen` runs Instagram's login page in a WebView and captures cookies once `sessionid`, `csrftoken`, and `ds_user_id` exist. `ProtocolRepository.signIn` validates them by loading the mailbox before persisting. `data/SessionStore` encrypts the cookie jar with a non-exportable Keystore key in `noBackupFilesDir/session.enc`, and re-encrypts Instagram-rotated cookies after successful calls.
- **Data flow**: Room is the UI source of truth (`data/ChatStore`, `data/db/InstaDmDatabase`), fed by `data/MailboxSync`. `data/ProtocolRepository` serializes `GoClient` calls on `Dispatchers.IO` and owns the client lifetime. `ui/chat/InboxState` holds Compose state and `ui/chat/ProtocolMapper` maps Go JSON to UI models.
- **Realtime**: Process-scoped `RealtimeRecorder` plus foreground `RealtimeService` (`dataSync|remoteMessaging`) persist deltas even when the UI was never opened. `RealtimeService.restart` reconnects only when the stored session changed.
- **Outbox**: Sends go through the Room `Outbox` (optimistic bubble plus durable row, same OTID on retry, up to 3 attempts, attachments copied into `filesDir/outbox`). Separate from the CLI file outbox.
- **Media cache**: Downloaded media uses a 250 MB LRU cache under `cacheDir/media`.
- **Paging**: Chat loads 30 messages per page (`THREAD_PAGE_SIZE`). Reaching the oldest bubble calls `InboxState.loadOlder`, which passes `Conversation.oldestCursor` to `GoClient.thread` and prepends through `ProtocolMapper.mapOlderPage`. `hasMoreOlder`, `oldestCursor`, `isLoadingOlder`, and `olderError` live on `Conversation`; a failed page pauses paging and shows a footer retry.
- **Smoke test**: `GoBindingSmokeTest` skips when the cookie file is missing. It loads the mailbox, the first thread, and an older page, then waits for a `connected` watch event.

## Debug cookies & device testing

Debuggable builds accept an adb-pushed cookie file at the app-specific external path only while no encrypted session exists. Clear app data to switch back:

```bash
adb shell mkdir -p /sdcard/Android/data/com.goldrift.instadm/files
adb push ~/.config/instadm/cookies.json \
  /sdcard/Android/data/com.goldrift.instadm/files/cookies.json
```

`connectedDebugAndroidTest` may reinstall the app and delete external files. For a real-account run, install both APKs, push cookies, then invoke the test runner directly.

## AAR & release build options

- `./scripts/build-android-aar.sh` supports `ABIS` (subset of ABIs) and `STRIP=0` (keep symbols).
- Side-by-side release-parity phone builds use the `dev` build type (`com.goldrift.instadm.dev`, debug-key signed, R8 enabled) via Android Studio or `./scripts/build-dev.sh [--serve|--install|--rebuild-aar]`, passing `-Pinstadm.abis=arm64-v8a` for a single-ABI APK.
- Per-ABI release splits use `-Pinstadm.abiSplits=true` (all four ABIs) or `-Pinstadm.abis=<abi,...>` (specific subset).
- Release `versionName` and `versionCode` come from `-PversionName` and `-PversionCode` (`major*10000 + minor*100 + patch`).
- Demo screenshot tests require `-Pinstadm.testBuildType=demo` (added automatically by `./scripts/demo-screenshots.sh`). AVD is `Medium_Phone_API_37.0`.
