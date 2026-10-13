#!/usr/bin/env bash
set -euo pipefail

mkdir -p screenshots
capture_evidence() {
  local result=$?
  trap - EXIT
  adb logcat -d > screenshots/logcat.txt || true
  adb shell am start -n con.themralex.aydlp/.ui.MainActivity > screenshots/launch.txt 2>&1 || true
  adb exec-out screencap -p > screenshots/device-16kb.png || true
  exit "$result"
}
trap capture_evidence EXIT

adb shell getconf PAGE_SIZE | tr -d '\r' | tee screenshots/page-size.txt
test "$(cat screenshots/page-size.txt)" = 16384
./gradlew connectedDebugAndroidTest --max-workers=2
