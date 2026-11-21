# instadm (InstaDM)

Two build units, no root Go module:
- `go-core/`: Go module (`github.com/goldrift/instadm/go-core`) with protocol core (`igcore/`), CLI (`cmd/igcli/`), and gomobile wrapper (`mobile/`).
- `app/`: Android module (`:app`), Jetpack Compose on top of generated `app/libs/igcore.aar`. `baselineprofile/` (`:baselineprofile`) only generates the startup profile `:app` consumes.

For detailed Android internals, adb cookie push, and release/ABI flags, see `.agents/reference.md`.

## Workflow

Work on whatever branch is checked out; if asked to create one, use `feat/<slug>`, `fix/<slug>`, `chore/<slug>`, `docs/<slug>`, or `test/<slug>`. Commit as you go with Conventional Commits (one logical change per commit). Never push, open a PR, or create a tag unless asked. Releases use annotated `vMAJOR.MINOR.PATCH` tags (`.github/workflows/release.yml`).

Before returning to `main`:
- New or changed behavior has a test that fails without the change.
- Relevant checks pass.
- Leave `docs/screenshots/` alone unless you are cutting the release (see Demo build).
- Rebase onto `main` and `git merge --ff-only`. The user deletes the branch.

## Checks

Go (from `go-core/`, offline):
```bash
go build ./... && go vet ./... && go test ./...
go test ./igcore -run TestName -count=1
```

Android (from repo root):
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests 'com.goldrift.instadm.MediaPolicyTest'
./gradlew :app:assembleDebug
```

Run what matches your edits:
- `go-core/` changed: Go build, vet, test.
- `igcore` or `mobile` changed: rebuild `app/libs/igcore.aar` first via `./scripts/build-android-aar.sh` (Gradle does not compile Go).
- Kotlin changed: `:app:testDebugUnitTest`.
- UI or Go binding changed: `:app:assembleDebug`.

Never run `connectedDebugAndroidTest`, `:app:lintDebug`, or real-account checks unless asked. If something could not be verified, say so and list what to tap.

`app/src/test` is JVM-only; `app/src/androidTest` needs a device (Room, mapping, realtime parsing, outbox). Keep tests at the JVM seam when possible; when only a device can catch it, add the `androidTest` case and note it is unrun.

## Testing & writing

- **Red before green**: write a failing test at a public seam before implementing, one vertical slice at a time. Name the seam in one line first. Existing seams: `igcore` normalize layer, `ChatStore`, `Outbox`, `ProtocolMapper`, `MediaPolicy`.
- **Good tests**: assert independent known-good literals through public interfaces; never mock internals or recompute implementation logic in assertions. Load the `tdd` skill only for complex test-first design or new seam architecture.
- **Plain prose**: active voice, short concrete sentences, no AI filler/buzzwords, no em dashes, no decorative emojis. Load `.agents/skills/unslop/SKILL.md` only for long-form docs (`README.md`, release notes), not commit messages, comments, or short UI strings.
- **Comments**: write self-explanatory code. Comment only protocol quirks, workarounds, invariants, ordering/lifetime constraints, or magic values. No narration, banners, or doc blocks restating a signature. Do not comment untouched code.

## Go core

- `igcore` is a thin normalized layer over pinned `mautrix-meta`. Check pinned source; never invent endpoints, field names, or doc IDs.
- Keep `replace github.com/imroc/req/v3 => ...` in `go.mod` (Go ignores dependency replaces).
- Go binary is `/usr/bin/go`; do not install through mise.
- Real-account checks use `~/.config/instadm/cookies.json` (`0600`). Never commit real cookies or unredacted captures (`fixtures/` has redacted samples; `testdata/example-cookies.json` has placeholders).
- `send` and `send-media` support `--outbox` (CLI-only, Unix-only, `flock`, `0600`, absolute media paths).
- `watch` is the only socket-holding path: 1 to 30 s backoff, stops on permanent auth, challenge, or rate-limit errors.
- Keep `go-core/README.md` and `README.md` current for user-visible changes.

## Android

- Room (`app/schemas/`, `exportSchema = true`) is the UI source of truth. Any entity change requires a version bump and migration; there is no destructive fallback.
- UI code calls `data/GoClient.kt` only, never the generated `Client`.
- `Conversation.id` is the short IG thread ID; `ChatMessage.id` stays a `String` (e.g. `mid.$...`).
- Attachments cap at 8 MiB (images) and 25 MiB (other) in `MediaPolicy`.
- The chat list is a reversed `LazyColumn` with stable keys. Never key the scroll-to-newest effect on `messages.size`, or prepending older pages jumps the reader to the bottom.
- Manifest requires `INTERNET`; gomobile/R8 rules live in `app/src/main/keepRules/rules.keep`.

## Demo build

- `demo` build type (`com.goldrift.instadm.demo`) is offline, fictional, and never reads real sessions. Keep all demo hooks in `src/demo`; never add demo branches to production screens.
- Visible UI changes require updating `app/src/demo/.../DemoData.kt` (new message kinds, media, threads) and `DemoProtocol.kt` (simulated behavior), and must work in airplane mode.
- When a captured screen looks different, do not regenerate yet. `docs/screenshots/` is pinned to the latest release tag so the README gallery keeps matching the published APK; refreshing it during feature work is misleading and must not land on `main`.
- Refresh screenshots only in the release commit right before creating a `v*` tag: update `app/src/androidTestDemo/` and expected counts in `scripts/demo-screenshots.sh`, run `./scripts/demo-screenshots.sh` (needs `Medium_Phone_API_37.0` and rewrites both `docs/screenshots/` and the README gallery), and commit the result with the release. Never hand-edit the generated README gallery block, and leave images alone if the emulator is unavailable.

## Build gotchas

- Gradle 9.6.0, AGP 9.4.1, Kotlin 2.2.10, Compose BOM 2026.09.00 (`gradle/libs.versions.toml`). `gradle/gradle-daemon-jvm.properties` requests Java 25; leave it alone.
- AGP 9 has built-in Kotlin: do not add `org.jetbrains.kotlin.android` (only the Compose compiler plugin).
- `compileSdk` uses `compileSdk { version = release(37) }`; minSdk 24, targetSdk 37.
- Release shrinking uses `buildTypes.release.optimization { ... }`, not `isMinifyEnabled` or `proguardFiles`; rules belong in `app/src/main/keepRules/`.
- Configuration cache is on; use `--no-configuration-cache` only to diagnose config-phase errors.
- `app/libs/igcore.aar` is git-ignored; `gomobile` and `gobind` must be on `PATH`, and `go-core/go.mod` must keep `tool golang.org/x/mobile/cmd/gobind`.
- Generated Java package is `com.goldrift.instadm.core.mobile` (`New` becomes `Mobile.new_`, Go `int32` becomes Java `int`).
