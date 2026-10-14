#!/usr/bin/env bash
set -euo pipefail

mkdir -p screenshots
capture_evidence() {
  local result=$?
  trap - EXIT
  adb logcat -d > screenshots/logcat.txt || true
  adb shell am start -n con.themralex.aydlp.preview/con.themralex.aydlp.ui.MainActivity > screenshots/launch.txt 2>&1 || true
  adb exec-out screencap -p > screenshots/device-16kb.png || true
  for screen in download-dark analysis-error queue library settings-dark download-light; do
    adb exec-out run-as con.themralex.aydlp.preview cat "files/ui-$screen.png" > "screenshots/ui-$screen.png" || true
  done
  exit "$result"
}
trap capture_evidence EXIT

adb shell getconf PAGE_SIZE | tr -d '\r' | tee screenshots/page-size.txt
test "$(cat screenshots/page-size.txt)" = 16384
# Run instrumentation directly so Gradle does not uninstall the app before
# screenshots are collected. Preserve the full runner output and fail on tests.
./gradlew assembleDebug assembleDebugAndroidTest --max-workers=2
adb install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w con.themralex.aydlp.preview.test/androidx.test.runner.AndroidJUnitRunner | tee screenshots/instrumentation.txt
grep -Eq '^OK \([0-9]+ tests?\)' screenshots/instrumentation.txt
