# instadm (InstaDM)

Two build units, and no root Go module:

- `go-core/`: separate Go module holding the protocol core (`igcore/`), the CLI (`cmd/igcli/`), and the gomobile wrapper (`mobile/`).
- `app/`: one Android module, `:app`, Jetpack Compose on top of the generated `app/libs/igcore.aar`.

## Workflow

The user creates and deletes the working branch; work on whatever branch is checked out. If you are asked to make one, use `feat/<slug>`, `fix/<slug>`, `chore/<slug>`, `docs/<slug>`, `test/<slug>`. Commit as you go with Conventional Commits, one logical change per commit, and fix mistakes with a follow-up commit rather than holding the work back.

Before the branch returns to `main`:

- [ ] New or changed behavior has a test that fails without the change.
- [ ] The checks for what you touched pass (see Checks).
- [ ] Demo screenshots are regenerated if a captured screen changed.
- [ ] Rebase onto `main` and `git merge --ff-only`. The user deletes the branch.

Never push, open a pull request, or create a tag unless asked. Releases are cut from annotated `vMAJOR.MINOR.PATCH` tags by the maintainer, and the tag triggers `.github/workflows/release.yml`.

## Checks

Go, from `go-core/`:

```bash
go build ./... && go vet ./... && go test ./...
go test ./igcore -run TestName -count=1
go test ./mobile -run TestName -count=1
```

Android, from the repository root:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests 'com.goldrift.instadm.MediaPolicyTest'
./gradlew :app:assembleDebug
```

Which ones:

- `go-core/` changed: the Go trio above. `go test` is offline.
- Kotlin changed: `:app:testDebugUnitTest`.
- UI or the Go binding changed: `:app:assembleDebug`, which needs `app/libs/igcore.aar`.
- `igcore` or `mobile` changed: rebuild the AAR first with `./scripts/build-android-aar.sh`; Gradle does not compile Go.

Never run `connectedDebugAndroidTest`, `:app:lintDebug`, or a real-account check unless asked. When you could not verify something, say so in the reply and name what the user should tap.

`app/src/test` is JVM-only. `app/src/androidTest` needs a device and covers Room, mapping, realtime parsing, and the outbox. Keep a test at the JVM seam when it can live there; when only the device can catch it, add the `androidTest` case and say it is unrun.

## Testing and writing

- Load the `tdd` skill for any behavior change and name the seam in a line before writing the test. Seams that already exist: the Go normalize layer, `ChatStore`, `Outbox`, `ProtocolMapper`, `MediaPolicy`.
- Load the `unslop` skill for prose you write: UI strings, README, release notes, commit messages, comments.
- Write code that explains itself and comment only what it cannot: protocol quirks, workarounds, invariants, ordering or lifetime constraints, magic values. No narration, no banners, no doc blocks restating a signature. Do not add comments to code you did not change.

## Go core

- Module path `github.com/goldrift/instadm/go-core`.
- `igcore` is a thin normalized layer over the pinned `mautrix-meta`. Do not invent endpoints, field names, or doc IDs; check the pinned source.
- Keep the existing `replace github.com/imroc/req/v3 => ...` in `go.mod`; Go ignores replacement directives from dependency modules.
- Go is `/usr/bin/go`. Do not install it through mise.
- Keep `go-core/README.md` current for commands, flags, and validation status.
- Real-account checks read cookies from `~/.config/instadm/cookies.json` (mode `0600`). Never commit real cookies or unredacted captures; `fixtures/` holds redacted samples and `testdata/example-cookies.json` holds placeholders.
- `send` and `send-media` accept `--outbox`: CLI-only, Unix-only, `flock`, mode `0600`, absolute media paths.
- `watch` is the only socket-holding path. It reconnects with 1 to 30 s backoff and stops on permanent auth, challenge, or rate-limit errors.
- Keep `README.md` current for user-visible behavior.

## Android

- Login runs Instagram's page in a WebView (`ui/login/LoginScreen`) and captures cookies once `sessionid`, `csrftoken`, and `ds_user_id` exist. `ProtocolRepository.signIn` validates them by loading the mailbox before persisting.
- `data/SessionStore` encrypts the cookie jar with a non-exportable Keystore key in `noBackupFilesDir/session.enc`, and re-encrypts Instagram-rotated cookies after successful calls.
- Debuggable builds also accept an adb-pushed cookie file at the app-specific external path, but only while no encrypted session exists. Clear app data to switch back:

  ```bash
  adb shell mkdir -p /sdcard/Android/data/com.goldrift.instadm/files
  adb push ~/.config/instadm/cookies.json \
    /sdcard/Android/data/com.goldrift.instadm/files/cookies.json
  ```

- Room is the UI source of truth: `data/ChatStore` and `data/db/InstaDmDatabase`, fed by `data/MailboxSync`. `app/schemas/` is committed and `exportSchema = true`, so any entity change needs a version bump and a migration; there is no destructive fallback.
- `data/ProtocolRepository` serializes `GoClient` calls on `Dispatchers.IO` and owns the client lifetime. `ui/chat/InboxState` is Compose state and `ui/chat/ProtocolMapper` maps Go JSON to UI models.
- `Conversation.id` is the short IG thread ID used for navigation and Go calls. `ChatMessage.id` stays a `String`; real IDs look like `mid.$...`.
- Realtime is a process-scoped `RealtimeRecorder` plus the foreground `RealtimeService` (`dataSync|remoteMessaging`), so deltas persist even when the UI was never opened. `RealtimeService.restart` reconnects only when the stored session changed.
- Sends go through the Room `Outbox`: optimistic bubble plus durable row, the same OTID on retry, up to 3 attempts, attachments copied into `filesDir/outbox`. This is separate from the CLI file outbox.
- Attachments cap at 8 MiB for images and 25 MiB otherwise (`MediaPolicy`). Downloaded media uses a 250 MB LRU cache under `cacheDir/media`.
- Chat loads 30 messages per page (`THREAD_PAGE_SIZE`). Reaching the oldest bubble calls `InboxState.loadOlder`, which passes `Conversation.oldestCursor` to `GoClient.thread` and prepends through `ProtocolMapper.mapOlderPage`. `hasMoreOlder`, `oldestCursor`, `isLoadingOlder`, and `olderError` live on `Conversation`, and a failed page pauses paging and shows a footer retry.
- The chat list is a reversed `LazyColumn`, so older pages append past the visible range and stable keys hold the viewport. Never key the scroll-to-newest effect on `messages.size`, or a prepend jumps the reader to the bottom.
- Call the Kotlin wrapper `data/GoClient.kt`; UI code never touches the generated `Client`.
- The manifest requires `INTERNET`. gomobile and R8 rules live in `app/src/main/keepRules/rules.keep`.
- `GoBindingSmokeTest` skips when the cookie file is missing. It loads the mailbox, the first thread, and an older page, then waits for a `connected` watch event.

## Demo build

- The `demo` build type (`com.goldrift.instadm.demo`) is offline, uses fictional data, and never reads the real session.
- Visible GUI changes are demo changes. Update `app/src/demo/.../DemoData.kt` for new message kinds, media, or threads, and `DemoProtocol.kt` for behavior the demo must simulate.
- Add or adjust the screenshot case in `app/src/androidTestDemo/` and the expected image counts in `scripts/demo-screenshots.sh` when a captured screen changed, then run `./scripts/demo-screenshots.sh` on the branch. It replaces `docs/screenshots/`, regenerates the README gallery between the markers, and installs the demo APK.
- Run the script only when a captured screen looks different. Protocol, data, and behavior changes that no shot shows do not need a refresh; say so instead of churning committed images.
- If the emulator is unavailable or the capture fails, leave the images alone and say so.
- Commit refreshed `docs/screenshots/` with the change that altered them. Never hand-edit the generated README block.
- Keep fixtures fictional and locally generated, verify the demo works in airplane mode, and never add demo-only branches to production screens; `src/demo` is the seam.

## Build gotchas

- Gradle 9.6.0, AGP 9.4.1, Kotlin 2.2.10, Compose BOM 2026.09.00. Versions live in `gradle/libs.versions.toml`.
- AGP 9 has built-in Kotlin. Do not add `org.jetbrains.kotlin.android`; only the Compose compiler plugin is applied.
- `compileSdk` uses the AGP 9 block form: `compileSdk { version = release(37) }`. minSdk 24, targetSdk 37.
- Release shrinking uses `buildTypes.release.optimization { ... }`, not `isMinifyEnabled` or `proguardFiles`. There is no `proguard-rules.pro`; rules belong in `app/src/main/keepRules/`.
- `gradle/gradle-daemon-jvm.properties` requests Java 25 for the daemon. Leave it alone unless you intend to bump the toolchain.
- Configuration cache is on. Use `--no-configuration-cache` only to diagnose configuration-phase problems.
- `:app:assembleDebug` requires `app/libs/igcore.aar`, which `./scripts/build-android-aar.sh` generates and git ignores. `gomobile` and `gobind` must be on `PATH`, and `go-core/go.mod` must keep `tool golang.org/x/mobile/cmd/gobind` or `gomobile bind` refuses to run.
- The generated Java package is `com.goldrift.instadm.core.mobile`. `New` becomes `Mobile.new_`, and Go `int32` becomes Java `int`.
- `ABIS` selects a subset for the AAR script, `STRIP=0` keeps symbols, and per-ABI release splits sit behind `-Pinstadm.abiSplits=true`.
- Release `versionName` and `versionCode` come from `-PversionName` and `-PversionCode`; `versionCode` is `major*10000 + minor*100 + patch`.
- The demo screenshot tests need `-Pinstadm.testBuildType=demo`, which the script adds. The AVD is `Medium_Phone_API_37.0`.
- `connectedDebugAndroidTest` may reinstall the app and delete its external files. For a real-account run, install both APKs, push cookies, then invoke the runner directly.
