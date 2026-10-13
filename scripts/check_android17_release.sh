#!/usr/bin/env bash
set -euo pipefail
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
mkdir -p release-startup
sdkmanager 'platforms;android-37.0' 'system-images;android-37.0;google_apis_ps16k;x86_64' emulator platform-tools
printf 'no\n' | avdmanager create avd --force -n release17 --package 'system-images;android-37.0;google_apis_ps16k;x86_64' --device pixel_6
emulator -avd release17 -no-window -gpu swiftshader -no-snapshot -noaudio -no-boot-anim -memory 4096 -cores 2 > release-startup/emulator.log 2>&1 &
emulator_pid=$!
cleanup() {
  adb logcat -d -v threadtime > release-startup/system-logcat.txt || true
  adb emu kill || true
  kill "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT
adb wait-for-device
ready=0
for attempt in $(seq 1 150); do
  if test "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 && \
     adb shell service check input 2>/dev/null | grep -q 'found' && \
     adb shell service check package 2>/dev/null | grep -q 'found'; then
    ready=1
    break
  fi
  sleep 2
done
test "$ready" = 1 || { echo 'Android 17 framework services never became ready'; exit 1; }
adb shell input keyevent 82
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
bash scripts/check_release_startup.sh "$1"
