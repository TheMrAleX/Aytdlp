#!/usr/bin/env bash
set -euo pipefail
mkdir -p release-startup
adb install -r "$1"
adb logcat -c
adb shell getprop > release-startup/device.txt
adb shell am start -W -n con.themralex.aydlp/.ui.MainActivity | tee release-startup/launch.txt
sleep 8
adb logcat -d -v threadtime > release-startup/logcat.txt
adb shell dumpsys activity activities > release-startup/activities.txt
adb shell uiautomator dump /sdcard/release-ui.xml || true
adb pull /sdcard/release-ui.xml release-startup/ui.xml || true
adb shell screencap -p /sdcard/release.png
adb pull /sdcard/release.png release-startup/screenshot.png
if ! adb shell pidof con.themralex.aydlp; then
  cat release-startup/logcat.txt
  echo 'Published release process died after launch'
  exit 1
fi
if ! grep -q 'con.themralex.aydlp' release-startup/ui.xml; then
  cat release-startup/logcat.txt
  echo 'Published release UI is not visible'
  exit 1
fi
