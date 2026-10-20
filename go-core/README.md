# go-core — Instagram protocol lab

Levels 1–3 of the Instagram DM implementation: a desktop CLI that validates exported
Instagram cookies, lists DM threads, reads recent messages, sends text and images/files,
adds/removes reactions, marks threads read, downloads attachments, and watches the DGW
realtime stream.

It does **not** create new DMs, persist a durable outbox, or touch the Android app.
Those are later levels.

## Upstream dependency

The protocol implementation comes from
[`go.mau.fi/mautrix-meta`](https://github.com/mautrix/meta), pinned to a specific
upstream commit:

```text
v0.2609.1-0.20261002183013-0e6bf6c054c0
```

`mautrix-meta` is AGPL-3.0. If this code is distributed, the corresponding source and
license notices must be provided.

The upstream module pins a patched `req` fork. Go ignores `replace` directives from
dependency modules, so `go.mod` repeats that replacement explicitly. Do not remove it;
without it, stock `req` fails to compile against the pinned `quic-go` version.

## Requirements

- Go 1.27.1 or newer
- A secondary Instagram account. Do not test with a primary account.
- Cookies exported from a browser session logged in to `instagram.com`.

## Cookie export

The CLI accepts any of these formats:

1. Flat JSON:

   ```json
   {
     "sessionid": "...",
     "csrftoken": "...",
     "ds_user_id": "123456789",
     "mid": "...",
     "ig_did": "...",
     "rur": "...",
     "shbid": "...",
     "shbts": "..."
   }
   ```

2. A Cookie-Editor style JSON array (`[{"name": "...", "value": "..."}]`).

3. A raw `Cookie` header:

   ```text
   sessionid=...; csrftoken=...; ds_user_id=...
   ```

Required cookies are `sessionid`, `csrftoken`, and `ds_user_id`. The optional routing
cookies should be included when the exporter provides them.

Store real cookie files outside this repository, for example:

```text
~/.config/instadm/cookies.json
```

with permissions `0600`. `cookies*.json` is gitignored. The tracked example is
`testdata/example-cookies.json` and contains placeholders only. The CLI never prints
cookie values; keep `--log-level debug` off when sharing output, because the upstream
library is more verbose at that level.

## Usage

```bash
cd go-core

# List DM threads
go run ./cmd/igcli mailbox --cookies ~/.config/instadm/cookies.json

# Machine-readable output for a fixture
go run ./cmd/igcli mailbox --cookies ~/.config/instadm/cookies.json --json > /tmp/mailbox.json

# Paste a Cookie header directly
go run ./cmd/igcli mailbox --cookie-header "sessionid=...; csrftoken=...; ds_user_id=..."

# Read cookies from stdin
cat ~/.config/instadm/cookies.json | go run ./cmd/igcli mailbox --cookies -

# Read the newest 20 messages in a thread
# Use the short "id" field from mailbox --json as the thread ID.
go run ./cmd/igcli thread <thread-id> --cookies ~/.config/instadm/cookies.json

# Read a smaller page
go run ./cmd/igcli thread <thread-id> --cookies ~/.config/instadm/cookies.json --limit 5

# Machine-readable thread output
go run ./cmd/igcli thread <thread-id> --cookies ~/.config/instadm/cookies.json --json > /tmp/thread.json

# Load an older page using the oldest_cursor from the previous page
go run ./cmd/igcli thread <thread-id> --after <cursor> --limit 20 --cookies ~/.config/instadm/cookies.json

# Send a text message to an existing thread
go run ./cmd/igcli send <thread-id> "hello from igcli" --cookies ~/.config/instadm/cookies.json

# Send with an explicit OTID (for retries/reconciliation)
go run ./cmd/igcli send <thread-id> "hello" --otid 1792364344000123 --cookies ~/.config/instadm/cookies.json

# Retry transient failures with the same OTID
go run ./cmd/igcli send <thread-id> "hello" --retry 3 --cookies ~/.config/instadm/cookies.json

# Edit one of your own text messages
go run ./cmd/igcli edit <thread-id> <message-id> "updated text" --cookies ~/.config/instadm/cookies.json

# Remove one of your own messages for everyone
go run ./cmd/igcli unsend <thread-id> <message-id> --cookies ~/.config/instadm/cookies.json

# Upload and send an image or file
go run ./cmd/igcli send-media <thread-id> ./photo.png --cookies ~/.config/instadm/cookies.json

# List the attachments on a message
go run ./cmd/igcli media <thread-id> <message-id> --cookies ~/.config/instadm/cookies.json

# Download the first attachment
go run ./cmd/igcli media <thread-id> <message-id> --save /tmp/photo.png --cookies ~/.config/instadm/cookies.json

# Download a specific attachment from a multi-media message
go run ./cmd/igcli media <thread-id> <message-id> --part 1 --save /tmp/video.mp4 --cookies ~/.config/instadm/cookies.json

# Mark a thread read up to its newest message
go run ./cmd/igcli mark-read <thread-id> --cookies ~/.config/instadm/cookies.json

# Mark read up to a specific message
go run ./cmd/igcli mark-read <thread-id> --message-id <message-id> --cookies ~/.config/instadm/cookies.json

# Add and remove a reaction
go run ./cmd/igcli react <thread-id> <message-id> "❤" --cookies ~/.config/instadm/cookies.json
go run ./cmd/igcli react <thread-id> <message-id> "❤" --remove --cookies ~/.config/instadm/cookies.json

# Watch DGW events until Ctrl-C
go run ./cmd/igcli watch --cookies ~/.config/instadm/cookies.json

# Watch for 30 seconds, only one thread, machine-readable NDJSON
go run ./cmd/igcli watch --duration 30s --thread <thread-id> --json --cookies ~/.config/instadm/cookies.json
```

Flags:

| Flag | Description |
|---|---|
| `--cookies <path>` | Cookie file, or `-` for stdin |
| `--cookie-header <raw>` | Raw Cookie header |
| `--json` | Normalized JSON instead of a table |
| `--log-level <level>` | `error`, `warn` (default), `info`, `debug` |
| `--timeout <duration>` | Network timeout, default `90s`; bootstrap timeout for `watch` |
| `--limit <n>` | Thread messages to fetch, 1–100, default `20` |
| `--after <cursor>` | `thread`: load older messages after this cursor |
| `--reply-to <message-id>` | `send`, `send-media`: reply to an existing message |
| `--otid <otid>` | `send`, `send-media`, `edit`: reuse a specific offline threading ID |
| `--retry <n>` | `send`: retry transient failures with the same OTID, default `0` |
| `--message-id <id>` | `mark-read`: mark read up to this message, default newest |
| `--remove` | `react`: remove the reaction instead of adding it |
| `--save <path>` | `media`: download the selected attachment to this file |
| `--part <n>` | `media`: attachment index to download, default `0` |
| `--duration <duration>` | `watch`: stop after this duration, default `0` (until interrupted) |
| `--thread <thread-id>` | `watch`: only print events for this thread |

`IG_COOKIES` may contain a raw header or JSON cookie input instead of passing a flag.

For the `thread` command, flags may be placed before or after `<thread-id>`, but do not
split them across both positions (the Go flag parser stops at the first positional
argument).

## What the output contains

The default table shows the viewer, `seq_id`, and one row per thread: short thread ID,
thread FBID, title, last activity, folder, and flags (`unread`, `muted`, `pinned`,
`group`).

`--json` adds the long thread ID, thread key, subtype, and participants. The output
contains no cookies or bootstrap tokens, but it still contains account and contact
metadata (names, usernames, thread IDs). Treat fixture files as personal data and keep
them out of version control unless they have been redacted.

The `thread` table shows the thread metadata followed by one row per message: local
timestamp, sender (`me` for your own messages), content kind, and text. Messages are
printed oldest-to-newest. Media is represented as `[image]`, `[video]`, `[sticker]`, and
so on. `--json` adds message IDs, offline threading IDs, sender FBIDs, and
`has_more_older`/`oldest_cursor` for the next pagination step. Media messages also get a
`media` array with attachment FBIDs, full and preview CDN URLs, dimensions, and duration
where available. CDN URLs are sensitive and expire; do not put them in shared fixtures.

Use `thread --after <cursor>` with the previous page's `oldest_cursor` to fetch older
messages. The older-page output uses the same message table and prints the next cursor.
The page is returned oldest-to-newest, and all messages are older than the previous
page's oldest message.

One ID detail: the viewer's `fbid` in the mailbox is the profile ID from
`PolarisViewer`, while message senders use the internal EIMU ID. `is_from_viewer` uses
the EIMU ID, so the two numbers can differ for the same account.

The `send` result includes the thread ID, canonical message ID, OTID, timestamp, and the
sent text. Keep the OTID if a send times out; re-running with the same `--otid` is safer
than generating a new one. `--retry` retries transient failures with that same OTID.
Permanent errors (expired session, challenge, checkpoint, rate limit) are not retried.
A durable cross-process outbox belongs to the Android/Room stage, not this CLI. `send`
only targets existing threads; new-DM creation via `recipient_igids` is deferred.

`send-media` uploads a local file through the web upload endpoint, then sends the
resulting attachment ID to an existing thread. It prints the message ID, attachment
FBID, filename, detected MIME type, byte size, OTID, and timestamp. Images are limited
to 8 MiB and other files to 25 MiB; these are client-side limits. MIME type is detected
from the file contents, with an extension fallback. The upload path is image/file first;
the Android-only video fallback for accounts that reject web video uploads is not
implemented. Web upload has been accepted for PNG, JPEG, and MP4 in testing. Captions
are not supported, matching the send mutation; send text separately.

`media` lists the attachments on one message. With `--save`, it downloads the selected
`--part` (default `0`) to a file. The command re-queries the message first, so the CDN
URL is fresh; if the CDN rejects the URL with HTTP 403, it re-queries once and retries.
Download URLs expire, so re-run the command instead of storing a URL long-term.
Downloads are capped at 25 MiB and written with mode `0600`. It supports
images, video, audio, GIFs, stickers, and multi-media messages. The source bytes may be
recompressed by the CDN, so compare decoded pixels rather than file hashes. Both listing
and downloading search the newest 100 messages; older media messages need a paginated
lookup in a later pass.

`mark-read` resolves the short thread ID to the long thread ID required by read
receipts, selects the newest message (or `--message-id`), sends the initial read
mutation, then sends the validation mutation with the message timestamp. It prints the
short and long thread IDs, message ID, timestamp, and whether Instagram validated the
read. The CLI is stateless, so it does not persist a local read watermark; the Android
app will do that. A specific `--message-id` must be within the newest 100 messages.

`edit` replaces the text of one of your own messages. Instagram enforces a 15-minute
edit window and a maximum of five edits per message; violations fail at the server. The
mutation sends `target_item_id: ""`, the new body, a new OTID, and the target IG message
ID. The result includes the thread, message, OTID, timestamp, and new text. Inbound
edits arrive as `message.edit` deltas.

`unsend` removes one of your own messages for everyone. Instagram requires the long
thread ID for the unsend mutation, so the command resolves the short ID through a thread
query first. It prints both thread IDs, the message ID, and the server's `unsent` flag.
Inbound deletes arrive as `message.delete` deltas. The CLI does not keep local rows; the
Android app should mark the row deleted and keep a tombstone so a late delta or backfill
does not resurrect it. Unsend only applies to your own messages.

`react` adds or removes one reaction on a message. Instagram allows one reaction per
sender per message, so adding a different emoji replaces the previous one. The target
message ID is the IG message ID from `thread --json`. Message JSON now also includes a
`reactions` array with emoji, sender FBID, and timestamp. Reaction removal deltas may omit
the emoji; the CLI always sends the emoji you provide.

The `watch` human output prints connection state, new messages, edits, deletes,
reactions, and read receipts. `--json` emits one JSON object per line (NDJSON). Message
text is included in both forms, so treat watch output as sensitive. `seq_id` events are
included in JSON but omitted from the human output to avoid noise.

## Mobile wrapper

`mobile/` is a JSON-only wrapper designed for `gomobile bind`. It keeps the Android
boundary small: every method returns normalized JSON and an error, and no Go types cross
the boundary except strings, bytes, and primitives.

```go
client, err := mobile.New(cookieJSON)
defer client.Close()

mailboxJSON, err := client.MailboxJSON(90)
threadJSON, err := client.ThreadJSON(threadID, 20, "", 90)
```

| Method | Purpose |
|---|---|
| `New(cookiesJSON)` | Parse cookies and create a client |
| `Version()` | Pinned protocol version |
| `MailboxJSON(timeoutSeconds)` | Inbox and viewer JSON |
| `ThreadJSON(threadID, limit, afterCursor, timeoutSeconds)` | Newest page, or older page when `afterCursor` is set |
| `SendTextJSON(threadID, text, replyTo, otid, timeoutSeconds)` | Text send result JSON |
| `EditJSON(threadID, messageID, text, otid, timeoutSeconds)` | Edit result JSON |
| `UnsendJSON(threadID, messageID, timeoutSeconds)` | Unsend result JSON |
| `SendMediaJSON(threadID, filename, mimeType, data, replyTo, otid, timeoutSeconds)` | Upload/send in-memory bytes |
| `ReactJSON(threadID, messageID, emoji, remove, timeoutSeconds)` | Reaction result JSON |
| `MarkReadJSON(threadID, messageID, timeoutSeconds)` | Read-receipt result JSON |
| `DownloadMediaJSON(threadID, messageID, part, destPath, timeoutSeconds)` | Download one attachment to `destPath` |
| `Close()` | Release the client |

A `timeoutSeconds` value of zero or less uses 90 seconds. `SendMediaJSON` takes bytes
rather than a path so Android can pass the result of reading a content URI; the Go side
still enforces type and size limits. `DownloadMediaJSON` writes to a normal filesystem
path, so Kotlin should pass a path under the app cache directory. Errors are returned to
Kotlin as exceptions. The AAR build requires `gomobile` and an Android NDK; the wrapper
itself is covered by host tests.

## Validation status

- Build, cookie parsing, table rendering, thread normalization, error paths, and the
  one-shot refresh after a 403 media response are covered by local tests.
- Real-account acceptance passed:
  - cookie login, inbox bootstrap, and mailbox query returned the viewer, `seq_id`, and
    a DM thread;
  - `thread` returned 20 messages with IDs, OTIDs, timestamps, and sender metadata, in
    oldest-to-newest order, with `has_more_older=true`;
  - `watch` connected to DGW and reported `connected` with a sequence ID;
  - `send` returned a canonical message ID, OTID, and timestamp, and the DGW echo
    arrived as `message.new` with matching message ID and OTID;
  - `mark-read` returned `validated=true` for the newest message;
  - `thread --after` returned the previous page's older messages, ordered
    oldest-to-newest, with the next cursor and no overlap;
  - `react` added and removed a reaction, and `watch` reported matching
    `reaction.add`/`reaction.remove` deltas;
  - `send-media` uploaded PNG, JPEG, and MP4 files and sent them; every returned message
    ID and OTID matched its `message.new` watch delta, which reported `kind=image` or
    `kind=video` with `media_count=1`;
  - `thread --json` exposed each attachment's FBID, CDN URLs, preview URL, and
    64×64 dimensions;
  - `media` listed and downloaded all three: the PNG decoded to identical pixels, the
    JPEG was 64×64 sRGB, and the MP4 was H.264 at 64×64 for 1 second. The CDN
    recompressed the image bytes, so file hashes are not stable;
  - `edit` changed a fresh text message within the edit window, and `watch` reported a
    matching `message.edit` delta;
  - `unsend` removed that message for everyone, `watch` reported a matching
    `message.delete` delta, and a later thread query no longer contained it;
  - the `mobile` wrapper's real-account `MailboxJSON` and `ThreadJSON` calls returned
    valid JSON with the pinned protocol version.
- Test cookies live outside the repo at `~/.config/instadm/cookies.json` with mode
  `0600`.
- `fixtures/mailbox.redacted.json` and `fixtures/thread.redacted.json` are synthetic,
  redacted samples of the normalized shapes. Do not add unredacted captures under
  `fixtures/`; the `.gitignore` only tracks these two files.

## Development

```bash
cd go-core
go build ./...
go vet ./...
go test ./...
```

## License

InstaDM is licensed under the GNU Affero General Public License, version 3 or
later. See [`LICENSE`](../LICENSE) and
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).
