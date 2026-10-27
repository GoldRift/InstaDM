#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -n "${VERSION:-}" && ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "error: VERSION must be MAJOR.MINOR.PATCH, got '$VERSION'" >&2
  exit 1
fi

# Release supports every ABI; the stripped AAR therefore includes all four.
ABIS=armeabi-v7a,arm64-v8a,x86,x86_64 "$repo_root/scripts/build-android-aar.sh"

release_dir="$repo_root/app/build/outputs/apk/release"
rm -rf -- "$release_dir"

cd "$repo_root"
./gradlew :app:assembleRelease -Pinstadm.abiSplits=true "$@"

if [[ -n "${VERSION:-}" ]]; then
  dist="$repo_root/build/dist"
  rm -rf -- "$dist"
  mkdir -p "$dist"
  shopt -s nullglob
  for apk in "$release_dir"/*.apk; do
    name="$(basename "$apk")"
    name="${name#app-}"
    name="${name%-release-unsigned.apk}"
    name="${name%-release.apk}"
    cp "$apk" "$dist/instadm-${VERSION}-${name}.apk"
  done
  apk_count="$(find "$dist" -maxdepth 1 -type f -name '*.apk' | wc -l)"
  if [[ "$apk_count" -ne 4 ]]; then
    echo "error: expected 4 ABI APKs, got $apk_count" >&2
    exit 1
  fi
  (cd "$dist" && sha256sum ./*.apk >SHA256SUMS)
fi

echo
if [[ -n "${INSTADM_KEYSTORE:-}" ]]; then
  echo "Signed release APKs:"
else
  echo "Release APKs (unsigned; set INSTADM_KEYSTORE and passwords to sign):"
fi
if [[ -n "${VERSION:-}" ]]; then
  ls -lh "$dist"
else
  ls -lh "$release_dir"/*.apk
fi
