# Third-Party Notices

InstaDM includes the third-party components listed below. The full license text
for the GNU Affero General Public License, version 3, is in [`LICENSE`](LICENSE).
Other license texts are available from the upstream projects.

## Go core

The `igcli` binary links the following Go modules. Versions are pinned in
[`go-core/go.mod`](go-core/go.mod) and recorded in
[`go-core/go.sum`](go-core/go.sum). Module paths are reproduced exactly as
required by Go's dependency metadata.

| Module | Version | License |
|---|---|---|
| filippo.io/edwards25519 | v1.2.0 | BSD-3-Clause |
| github.com/andybalholm/brotli | v1.2.4 | MIT |
| github.com/apache/thrift | v0.24.0 | Apache-2.0; see upstream NOTICE |
| github.com/beeper/poly1305 | v0.0.0-20250815183548-d4eede7bbf3c | BSD-3-Clause; source headers retain the Go license |
| github.com/beeper/req/v3 | v3.0.0-20260925135712-b49593750e56 | MIT; replacement for github.com/imroc/req/v3 |
| github.com/coder/websocket | v1.8.15 | ISC |
| github.com/coreos/go-systemd/v22 | v22.7.0 | Apache-2.0 (journal package) |
| github.com/google/flatbuffers | v25.12.19+incompatible | Apache-2.0 |
| github.com/google/go-querystring | v1.2.0 | BSD-3-Clause |
| github.com/google/uuid | v1.6.0 | BSD-3-Clause |
| github.com/icholy/digest | v1.2.0 | MIT |
| github.com/imroc/req/v3 | v3.56.0 | MIT |
| github.com/klauspost/compress | v1.20.0 | BSD-3-Clause |
| github.com/mattn/go-colorable | v0.1.15 | MIT |
| github.com/mattn/go-isatty | v0.0.20 | MIT |
| github.com/petermattis/goid | v0.0.0-20260918085751-abfca077860b | Apache-2.0 |
| github.com/quic-go/qpack | v0.6.0 | MIT |
| github.com/quic-go/quic-go | v0.61.0 | MIT |
| github.com/refraction-networking/utls | v1.8.2 | BSD-3-Clause |
| github.com/rs/xid | v1.6.0 | MIT |
| github.com/rs/zerolog | v1.35.1 | MIT |
| github.com/tidwall/gjson | v1.20.0 | MIT |
| github.com/tidwall/match | v1.1.1 | MIT |
| github.com/tidwall/pretty | v1.2.1 | MIT |
| github.com/tidwall/sjson | v1.2.5 | MIT |
| github.com/yuin/goldmark | v1.8.6 | MIT |
| go.mau.fi/mautrix-meta | v0.2609.1-0.20261002183013-0e6bf6c054c0 | AGPL-3.0-or-later |
| go.mau.fi/util | v0.10.2-0.20260918225449-a4c0d5b86aa8 | MPL-2.0 |
| go.mau.fi/zeroconfig | v0.2.0 | MPL-2.0 |
| golang.org/x/crypto | v0.58.0 | BSD-3-Clause |
| golang.org/x/exp | v0.0.0-20261014143641-f0251eb4aa3e | BSD-3-Clause |
| golang.org/x/net | v0.61.0 | BSD-3-Clause |
| golang.org/x/sys | v0.49.0 | BSD-3-Clause |
| golang.org/x/text | v0.43.0 | BSD-3-Clause |
| google.golang.org/protobuf | v1.36.12 | BSD-3-Clause |
| gopkg.in/natefinch/lumberjack.v2 | v2.2.1 | MIT |
| gopkg.in/yaml.v3 | v3.0.1 | MIT OR Apache-2.0 |
| maunium.net/go/mautrix | v0.31.1-0.20260925133620-c6024bacd2e6 | MPL-2.0 |

`go.mau.fi/mautrix-meta` is licensed under AGPL-3.0-or-later. Its upstream
distribution also includes `LICENSE.exceptions` for named downstream projects;
those exceptions do not apply to InstaDM.

## Android app

The Android app uses the components declared in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) and
[`app/build.gradle.kts`](app/build.gradle.kts).

| Component | License |
|---|---|
| AndroidX Core KTX, Activity Compose, Lifecycle, Navigation, Compose UI, Material3, Material Icons, AndroidX Test, and Espresso | Apache-2.0 |
| Kotlin standard library and Kotlin Compose compiler plugin | Apache-2.0 |
| Android Gradle Plugin, Gradle, and the Foojay toolchain resolver | Apache-2.0 |
| JUnit 4 | Eclipse Public License 1.0 |

## License references

- AGPL-3.0-or-later: [`LICENSE`](LICENSE) or <https://www.gnu.org/licenses/agpl-3.0.html>
- Apache-2.0: <https://www.apache.org/licenses/LICENSE-2.0>
- BSD-3-Clause: <https://opensource.org/license/bsd-3-clause>
- EPL-1.0: <https://www.eclipse.org/legal/epl-v10.html>
- ISC: <https://opensource.org/license/isc-license-txt>
- MIT: <https://opensource.org/license/mit>
- MPL-2.0: <https://www.mozilla.org/en-US/MPL/2.0/>

If you distribute a binary, include this notice file and the license texts
required by the components above. For AGPL-3.0 components, provide the
corresponding source as required by that license.
