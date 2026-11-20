#!/usr/bin/env bash
# Builds a release-parity, side-by-side dev APK (com.goldrift.instadm.dev) for a
# single ABI, signed with the local debug keystore. Optional --serve hosts the
# APK over LAN with a terminal QR code; --install pushes it over adb.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
abi="${ABIS:-arm64-v8a}"
serve=0
install_adb=0
rebuild_aar=0
host="${HOST:-}"
port="${PORT:-8765}"

usage() {
  cat <<'EOF'
Usage: scripts/build-dev.sh [options]

Builds a signed, R8-optimized side-by-side APK (com.goldrift.instadm.dev) for a
single ABI (default: arm64-v8a) without needing the release keystore.

Options:
  --serve          Serve the built APK over HTTP on your LAN and print a QR code
  --install        Install the built APK on a connected adb device and launch it
  --rebuild-aar    Rebuild app/libs/igcore.aar even if Go sources have not changed
  --abi <abi>      Target ABI (default: arm64-v8a)
  --host <ip>      Host IP to advertise in the --serve URL/QR (default: auto LAN IP)
  --port <port>    TCP port for --serve (default: 8765)
  -h, --help       Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serve)
      serve=1
      shift
      ;;
    --install)
      install_adb=1
      shift
      ;;
    --rebuild-aar)
      rebuild_aar=1
      shift
      ;;
    --abi)
      abi="${2:-}"
      shift 2
      ;;
    --host)
      host="${2:-}"
      shift 2
      ;;
    --port)
      port="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown option '$1'" >&2
      usage >&2
      exit 1
      ;;
  esac
done

case "$abi" in
  armeabi-v7a|arm64-v8a|x86|x86_64) ;;
  *)
    echo "error: unsupported ABI '$abi' (expected armeabi-v7a, arm64-v8a, x86, or x86_64)" >&2
    exit 1
    ;;
esac

aar="$repo_root/app/libs/igcore.aar"

aar_has_abi() {
  python3 - "$aar" "jni/$abi/libgojni.so" <<'PY'
import sys, zipfile
try:
    with zipfile.ZipFile(sys.argv[1]) as zf:
        sys.exit(0 if sys.argv[2] in zf.namelist() else 1)
except (OSError, zipfile.BadZipFile):
    sys.exit(1)
PY
}

aar_is_stale() {
  [[ ! -f "$aar" ]] && return 0
  aar_has_abi || return 0
  if [[ "$repo_root/go-core/go.mod" -nt "$aar" || "$repo_root/go-core/go.sum" -nt "$aar" ]]; then
    return 0
  fi
  if [[ -n "$(find "$repo_root/go-core/igcore" "$repo_root/go-core/mobile" \
      -type f -name '*.go' ! -name '*_test.go' -newer "$aar" -print -quit)" ]]; then
    return 0
  fi
  return 1
}

if [[ "$rebuild_aar" -eq 1 ]] || aar_is_stale; then
  ABIS="$abi" "$repo_root/scripts/build-android-aar.sh"
  if [[ "$abi" != "x86_64" ]]; then
    echo "Note: app/libs/igcore.aar now has $abi only; run ./scripts/build-android-aar.sh before x86_64 emulator runs."
  fi
else
  echo "Reusing up-to-date app/libs/igcore.aar ($abi present)."
fi

cd "$repo_root"

short_sha="$(git rev-parse --short HEAD 2>/dev/null || echo local)"
dirty=""
if [[ -n "$(git status --porcelain 2>/dev/null || true)" ]]; then
  dirty=".dirty"
fi
latest_tag="$(git tag -l 'v*' --sort=-v:refname 2>/dev/null | head -n1 || true)"
base_version="${latest_tag#v}"
base_version="${base_version:-0.0.0}"
version_name="${base_version}+${short_sha}${dirty}"

./gradlew :app:assembleDev \
  -Pinstadm.abis="$abi" \
  -PversionName="$version_name"

built_apk="$repo_root/app/build/outputs/apk/dev/app-${abi}-dev.apk"
if [[ ! -f "$built_apk" ]]; then
  echo "error: expected APK not found at $built_apk" >&2
  exit 1
fi

out_dir="$repo_root/build/dev"
mkdir -p "$out_dir"
out_name="instadm-dev-${short_sha}${dirty}-${abi}.apk"
out_apk="$out_dir/$out_name"
cp -f "$built_apk" "$out_apk"

echo
echo "Built $out_apk"
ls -lh "$out_apk"
sha256sum "$out_apk"

if [[ "$install_adb" -eq 1 ]]; then
  adb_bin="$(command -v adb || true)"
  if [[ -z "$adb_bin" ]]; then
    sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -z "$sdk" && -f "$repo_root/local.properties" ]]; then
      sdk="$(sed -n 's/^sdk\.dir=//p' "$repo_root/local.properties" | head -n1)"
    fi
    if [[ -n "$sdk" && -x "$sdk/platform-tools/adb" ]]; then
      adb_bin="$sdk/platform-tools/adb"
    fi
  fi
  if [[ -z "$adb_bin" ]]; then
    echo "error: adb not found on PATH or in ANDROID_HOME" >&2
    exit 1
  fi
  echo
  echo "Installing on connected device..."
  "$adb_bin" install -r "$out_apk"
  "$adb_bin" shell am start -n com.goldrift.instadm.dev/com.goldrift.instadm.MainActivity >/dev/null
fi

if [[ "$serve" -eq 1 ]]; then
  if [[ -z "$host" ]]; then
    host="$(python3 - <<'PY'
import socket
try:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.connect(("8.8.8.8", 80))
        print(s.getsockname()[0])
except OSError:
    print("127.0.0.1")
PY
)"
  fi
  echo
  exec python3 - "$out_apk" "$out_name" "$host" "$port" <<'PY'
import errno
import http.server
import os
import shutil
import signal
import socket
import subprocess
import sys

apk_path, apk_name, host, requested_port = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
apk_size = os.path.getsize(apk_path)
route = "/" + apk_name

class ApkOnlyHandler(http.server.BaseHTTPRequestHandler):
    def _send_headers(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/vnd.android.package-archive")
        self.send_header("Content-Length", str(apk_size))
        self.send_header("Content-Disposition", f'attachment; filename="{apk_name}"')
        self.send_header("Cache-Control", "no-store")
        self.end_headers()

    def do_HEAD(self):
        if self.path != route:
            self.send_error(404)
            return
        self._send_headers()

    def do_GET(self):
        if self.path != route:
            self.send_error(404)
            return
        self._send_headers()
        try:
            with open(apk_path, "rb") as fh:
                shutil.copyfileobj(fh, self.wfile, length=64 * 1024)
            print(f"Served {apk_name} to {self.client_address[0]}", flush=True)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def log_message(self, fmt, *args):
        pass

class Server(http.server.ThreadingHTTPServer):
    allow_reuse_address = True

try:
    httpd = Server(("0.0.0.0", requested_port), ApkOnlyHandler)
except OSError as exc:
    if exc.errno == errno.EADDRINUSE and requested_port != 0:
        httpd = Server(("0.0.0.0", 0), ApkOnlyHandler)
    else:
        raise

actual_port = httpd.server_address[1]
url = f"http://{host}:{actual_port}{route}"

if shutil.which("qrencode"):
    subprocess.run(["qrencode", "-t", "ANSIUTF8", url], check=False)

print(f"Download URL: {url}", flush=True)
print(f"If your phone cannot connect, allow TCP port {actual_port} or pass --host <ip>.", flush=True)
print("Press Ctrl+C to stop.", flush=True)

def handle_signal(_signum, _frame):
    raise KeyboardInterrupt

signal.signal(signal.SIGTERM, handle_signal)
try:
    httpd.serve_forever()
except KeyboardInterrupt:
    print("\nStopped.", flush=True)
finally:
    httpd.server_close()
PY
fi
