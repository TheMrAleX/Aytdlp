# Validation status

## Executed in this environment
- Maven/Google Maven, Gradle, GitHub and YouTube HTTPS responses checked.
- Real host extraction with yt-dlp 2026.08.19 / Node: jNQXAC9IVRw returned 16 formats with direct URLs. Media bytes and Android extraction were not tested by this command.
- Real debug build: ARM64, ARMv7, x86_64 and universal APKs.
- 13 compiled domain JUnit tests passed via JUnitCore. The additional cookie-header test is being compiled for the final validation run.
- Two deterministic process-fault tests passed: transfer interruption with preserved partial bytes and kill during merge without publishing the incomplete output. These test the deterministic fixture, not actual YouTube media.
- Rebuilt five misaligned WebP libraries for each 64-bit ABI; recursive audit of universal debug APK inspected 776 ELF files with zero incompatible entries.
- SDK zipalign -c -P 16 -v 4 passed for the universal debug APK.
- Detekt completed with zero findings under the documented configuration.
- Release with RSA-4096 signing/R8 produced APKs; final rebuild, lint and signature/alignment acceptance are recorded after completion.

## Device checks required
Open Settings/Maintenance and run Health check; QuickJS, EJS and FFmpeg must be ready. Analyze a public video, download video and audio, play both, test a shared link, decline notifications once, then grant them and check actions. Pause halfway, close/reopen, resume and verify the file. Test interruption during merge, Wi-Fi changes, charging rules, reboot and SD/SAF storage. Force-stop requires user relaunch by Android design. Run a 16 KB device/emulator with `adb shell getconf PAGE_SIZE` equal to 16384. Test API 26, 33, 34, 36 and 37 and an aggressive OEM. Do not assume 6+ hour or Doze behavior without measuring it.

## Remaining original scope, not claimed delivered
Full playlist/channel pagination and range editing; duplicate replace UX/download-archive integration; exact device codec capability warnings; arbitrary metadata tag editing/chapter-based clipping; drag/swipe queue reordering and per-item overnight windows; full grid/sort/folder library tools and moving completed media; cookie WebView login; runtime/FFmpeg in-app binary replacement; dynamic thumbnail accent/shared transitions; Quick Settings tile, shortcuts and Glance widget; generated device baseline profiles/Macrobenchmark comparative measurements. Native component updates intentionally use signed APK updates. These are development backlog, not hidden mocks or fake production results.

Live Update promotion, OEM endurance, Android 17 playback/native hardening, real-media pause/resume/merge corruption tests, remote CI/device tests and accessibility/performance measurements remain unverified until their evidence exists. The application makes no verified claim of being faster or more stable than Seal/YTDLnis.
