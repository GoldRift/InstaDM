#!/usr/bin/env bash
# Builds the offline demo app, drives the real UI on an emulator, and writes
# light/dark/two-pane screenshots. The demo APK has its own application ID and
# never touches a real session.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${OUT:-$ROOT/docs/screenshots}"
AVD_NAME="${AVD_NAME:-Medium_Phone_API_37.0}"
REMOTE_DIR="/sdcard/Download/instadm-demo"
SUITE_PACKAGE="com.goldrift.instadm.demo.screenshots"
LIGHT_CLASS="$SUITE_PACKAGE.DemoScreenshotTest"
DARK_CLASS="$SUITE_PACKAGE.DemoDarkScreenshotTest"
TWO_PANE_CLASS="$SUITE_PACKAGE.DemoTwoPaneScreenshotTest"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" && -f "$ROOT/local.properties" ]]; then
  SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | head -n1)"
fi
if [[ -z "$SDK" || ! -x "$SDK/platform-tools/adb" ]]; then
  echo "Android SDK not found; set ANDROID_HOME or local.properties." >&2
  exit 1
fi
ADB="$SDK/platform-tools/adb"

if [[ -n "${ANDROID_SERIAL:-}" && "$ANDROID_SERIAL" != emulator-* ]]; then
  echo "Refusing to run demo capture on non-emulator serial $ANDROID_SERIAL." >&2
  exit 1
fi

pick_emulator() {
  "$ADB" devices | awk '/^emulator-[0-9]+[[:space:]]+device$/{print $1; exit}'
}

wait_for_emulator() {
  for _ in $(seq 1 120); do
    local serial
    serial="$(pick_emulator)"
    if [[ -n "$serial" ]] &&
      [[ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
      SERIAL="$serial"
      export ANDROID_SERIAL="$SERIAL"
      return 0
    fi
    sleep 2
  done
  return 1
}

start_emulator() {
  if [[ ! -x "$SDK/emulator/emulator" ]]; then
    echo "Emulator binary not found under $SDK." >&2
    exit 1
  fi
  echo "Starting $AVD_NAME…"
  nohup "$SDK/emulator/emulator" -avd "$AVD_NAME" -no-snapshot-load \
    -no-snapshot-save -no-audio >/tmp/instadm-demo-emulator.log 2>&1 &
  wait_for_emulator
}

adb_shell() { "$ADB" -s "$SERIAL" shell "$@"; }
adb_shell_nofail() { adb_shell "$@" >/dev/null 2>&1 || true; }

configure_ui() {
  adb_shell_nofail settings put global sysui_demo_allowed 1
  adb_shell_nofail am broadcast -a com.android.systemui.demo -e command enter
  local hhmm
  hhmm="$(adb_shell date +%H%M 2>/dev/null | tr -d '\r')"
  adb_shell_nofail am broadcast -a com.android.systemui.demo -e command clock -e hhmm "${hhmm:-1200}"
  adb_shell_nofail am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
  adb_shell_nofail am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
  adb_shell_nofail am broadcast -a com.android.systemui.demo -e command notifications -e visible false
  adb_shell_nofail settings put global window_animation_scale 0
  adb_shell_nofail settings put global transition_animation_scale 0
  adb_shell_nofail settings put global animator_duration_scale 0
  adb_shell_nofail settings put system font_scale 1.0
}

ensure_device() {
  if [[ -n "${SERIAL:-}" ]] &&
    [[ "$("$ADB" -s "$SERIAL" get-state 2>/dev/null)" == "device" ]] &&
    [[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
    export ANDROID_SERIAL="$SERIAL"
    return 0
  fi
  echo "Emulator unavailable; restarting…" >&2
  if [[ -n "${SERIAL:-}" ]]; then
    "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
  fi
  start_emulator
  configure_ui
}

restore_device() {
  adb_shell_nofail cmd uimode night no
  adb_shell_nofail wm user-rotation free
  adb_shell_nofail wm fixed-to-user-rotation default
  adb_shell_nofail settings put system accelerometer_rotation 1
  adb_shell_nofail settings put system user_rotation 0
  adb_shell_nofail settings put global window_animation_scale 1
  adb_shell_nofail settings put global transition_animation_scale 1
  adb_shell_nofail settings put global animator_duration_scale 1
  adb_shell_nofail am broadcast -a com.android.systemui.demo -e command exit
}
trap restore_device EXIT

SERIAL="${ANDROID_SERIAL:-$(pick_emulator)}"
if [[ -z "$SERIAL" ]]; then
  start_emulator
else
  export ANDROID_SERIAL="$SERIAL"
fi
ensure_device
echo "Using $SERIAL"

capture_suite() {
  local class="$1" night="$2" folder="$3" expected="$4"
  local attempt count
  for attempt in 1 2; do
    ensure_device
    configure_ui
    echo "Capturing $folder (attempt $attempt)…"
    adb_shell_nofail cmd uimode night "$night"
    # Each suite starts from a normal portrait phone; the two-pane test asks
    # UiAutomation for landscape itself so the rotation is not lost.
    adb_shell_nofail wm user-rotation free
    adb_shell_nofail wm fixed-to-user-rotation default
    adb_shell_nofail settings put system accelerometer_rotation 1
    adb_shell_nofail settings put system user_rotation 0
    adb_shell_nofail rm -rf "$REMOTE_DIR"
    sleep 1

    if ! "$ROOT/gradlew" -p "$ROOT" :app:connectedDemoAndroidTest \
      -Pinstadm.testBuildType=demo \
      -Pandroid.testInstrumentationRunnerArguments.class="$class"; then
      echo "  suite failed; retrying" >&2
      continue
    fi

    ensure_device
    rm -rf "$OUT/$folder"
    mkdir -p "$OUT/$folder"
    "$ADB" -s "$SERIAL" pull "$REMOTE_DIR/." "$OUT/$folder/" >/dev/null 2>&1 || true
    count="$(find "$OUT/$folder" -name '*.png' -size +0c | wc -l | tr -d ' ')"
    if (( count >= expected )); then
      echo "  $count images"
      return 0
    fi
    echo "  expected $expected images, got $count; retrying" >&2
    rm -rf "$OUT/$folder"
  done
  echo "Could not capture $folder." >&2
  exit 1
}

capture_suite "$LIGHT_CLASS" no light 11
capture_suite "$DARK_CLASS" yes dark 5
capture_suite "$TWO_PANE_CLASS" no two-pane 2

python3 "$ROOT/scripts/update-readme-screenshots.py" "$OUT" "$ROOT/README.md"

if [[ "${INSTALL_DEMO:-1}" == "1" ]]; then
  ensure_device
  "$ADB" -s "$SERIAL" install -r "$ROOT/app/build/outputs/apk/demo/app-demo.apk" >/dev/null
  echo "Installed demo APK on $SERIAL"
fi

echo
echo "Screenshots: $OUT"
echo "README gallery updated"
