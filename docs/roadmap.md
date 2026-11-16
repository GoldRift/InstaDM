# Roadmap

What is still missing, roughly in the order a user would notice it. Delete a line when
it lands; `README.md` and the code describe what exists.

## Chat

- Message requests have no screen, so nothing accepts or declines one.
- Group DMs are read-only: no path creates a group, invites a member, or leaves one.
- The outbox retries text, media, and voice notes only. Reactions, edits, unsends, and
  read marks go out once.

## Background

- Nothing syncs while `RealtimeService` is stopped: there is no WorkManager job and no
  polling fallback.
- A restart refetches from the newest page, because `seq_id` reaches Room but no stored
  cursor survives the process.

## Data

- Single-account fields live in `app_meta`; there is no `accounts` table.
- Receipts collapse into message and thread state; there is no `read_receipts` table.
- No persisted sync cursor and no full-text index.

## Operations

- No diagnostics screen or redacted export, so a bug report starts from `logcat`.
- Video upload uses the web path only; an account that rejects it has no fallback.
- `go-core` imports the pinned module instead of vendoring the subset it uses, so an
  upstream bump needs a manual diff against `fixtures/`.

## Only a signed-in account can verify

Sends, reactions, edits, unsends, voice notes, shared reels, typing indicators, deltas
after a reconnect, and old media. `go test` covers none of them.
