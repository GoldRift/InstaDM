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

# Accept Android ABI names so callers do not have to remember gomobile's
# GOARCH names. All four by default: arm64 phones and the x86_64 emulator both
# work without rebuilding. Pass ABIS=arm64-v8a for a release-only AAR.
abis="${ABIS:-armeabi-v7a,arm64-v8a,x86,x86_64}"
targets=()
IFS=',' read -r -a requested_abis <<<"$abis"
for abi in "${requested_abis[@]}"; do
  abi="${abi//[[:space:]]/}"
  [[ -n "$abi" ]] || continue
  case "$abi" in
    armeabi-v7a) targets+=("android/arm") ;;
    arm64-v8a) targets+=("android/arm64") ;;
    x86) targets+=("android/386") ;;
    x86_64) targets+=("android/amd64") ;;
    *)
      echo "error: unknown ABI '$abi' (expected armeabi-v7a, arm64-v8a, x86, or x86_64)" >&2
      exit 1
      ;;
  esac
done
if [[ ${#targets[@]} -eq 0 ]]; then
  echo "error: ABIS is empty" >&2
  exit 1
fi
target_list="$(IFS=','; echo "${targets[*]}")"

# -s -w drops the ELF symbol table and DWARF, about a third of each
# libgojni.so. -trimpath keeps host paths out. Go panic traces, reflection, and
# JNI exports still work because .gopclntab and .dynsym are kept. STRIP=0
# retains symbols for native debugging.
strip_flags=()
if [[ "${STRIP:-1}" != "0" ]]; then
  strip_flags=(-trimpath -ldflags="-s -w")
fi

mkdir -p "$repo_root/app/libs"
gomobile bind \
  -target="$target_list" \
  -androidapi 24 \
  -javapkg=com.goldrift.instadm.core \
  "${strip_flags[@]}" \
  -o "$repo_root/app/libs/igcore.aar" \
  ./mobile

echo "Built $repo_root/app/libs/igcore.aar for: $target_list"
echo "For per-ABI release APKs, run scripts/build-release.sh."
ls -lh "$repo_root/app/libs/igcore.aar"
