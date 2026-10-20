# InstaDM

An Android Instagram DM client built around a Go protocol core.

- [`go-core/`](go-core/) — Go protocol lab and CLI: cookie validation, mailbox,
  thread read, text send, and DGW realtime watch. See
  [`go-core/README.md`](go-core/README.md).
- [`app/`](app/) — Android Jetpack Compose app. It is currently an early UI
  scaffold and does not yet embed the Go core.

## Status

The Go core implements the first three protocol levels. Media, reactions, edits,
unsend, durable outbox, and Android integration are not implemented yet.

## License

InstaDM is licensed under the GNU Affero General Public License, version 3 or
later. See [`LICENSE`](LICENSE).

Third-party components and their licenses are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
