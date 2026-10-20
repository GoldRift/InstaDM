#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root/go-core"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  if [[ ! -d "$ANDROID_HOME/ndk" ]]; then
    echo "error: no NDK found under $ANDROID_HOME/ndk" >&2
    exit 1
  fi
  ANDROID_NDK_HOME="$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
fi
export ANDROID_NDK_HOME

if ! command -v gomobile >/dev/null 2>&1; then
  echo "error: gomobile is not on PATH. Install it with:" >&2
  echo "  go install golang.org/x/mobile/cmd/gomobile@latest" >&2
  echo "  go install golang.org/x/mobile/cmd/gobind@latest" >&2
  echo "and add $(go env GOPATH)/bin to PATH." >&2
  exit 1
fi

if ! grep -q '^tool golang.org/x/mobile/cmd/gobind$' go.mod; then
  go get -tool golang.org/x/mobile/cmd/gobind
fi

mkdir -p "$repo_root/app/libs"
gomobile bind \
  -target=android \
  -androidapi 24 \
  -javapkg=com.goldrift.instadm.core \
  -o "$repo_root/app/libs/igcore.aar" \
  ./mobile

echo "Built $repo_root/app/libs/igcore.aar"
