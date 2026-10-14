# Aytdlp

Native Kotlin / Compose downloader built around yt-dlp, FFmpeg and QuickJS. Package: `con.themralex.aydlp`. Android 8+ (API 26). Compile SDK 37.2; target SDK 36. GPL-3.0-only.

**Initial release 0.1.0. This is not the complete original product specification and is not certified for production use.** A successful host extraction, ELF audit or compilation is not proof of Android extraction or long-running download reliability. See [validation](docs/VALIDATION.md) for executed evidence and remaining scope.

## Implemented
- Paste/search, foreground clipboard availability notice, shared-link sheet, HTTP(S) intents and bounded text batch import.
- Metadata/format analysis, flat playlists, per-item queueing, audio/video profiles, resolution/codec/container selection, subtitles, covers, chapters, SponsorBlock and time trimming.
- Own ExtractionEngine abstraction and bounded, shell-free ProcessBuilder execution, process-group supervisor, startup PID reconciliation.
- Room queue with explicit transitions, configurable concurrency/fragments, pause/continue, schedules, retries and error classification.
- UIDT jobs on Android 14+, earlier foreground transfer service, maintenance and wakeup workers.
- MediaStore/SAF destinations with temporary copies and size checks; no MANAGE_EXTERNAL_STORAGE.
- Media3 player with background session and PiP, searchable library, sharing and file management.
- Custom foundation-based Compose components, Manrope font, dark/light/AMOLED themes, eight accents, English/Spanish.
- Keystore-encrypted cookies, verified staged yt-dlp updates with smoke test and retained fallback, redacted diagnostics and settings/queue backups.

## UI and runtime refresh
See [review and scope](docs/REVIEW.md). Debug APKs install as **Aytdlp Preview** alongside the existing app with separate data; do not uninstall your production app to test the preview. Release signing and package identity remain unchanged.

## Build
Install JDK 21, Android SDK platform 37.2, build tools 37.0.0, NDK 28.2.13676358, CMake and Ninja.

```bash
python3 scripts/prepare_engine.py
python3 scripts/prepare_native.py --ndk "$ANDROID_HOME/ndk/28.2.13676358" --ninja "$(command -v ninja)"
./gradlew assembleDebug testDebugUnitTest lintDebug ktlintCheck detekt
python3 scripts/test_process_faults.py
python3 scripts/audit_native.py app/build/outputs/apk/debug/app-universal-debug.apk
```

Release signing uses environment variables AYTDLP_KEYSTORE (absolute path), AYTDLP_STORE_PASSWORD, AYTDLP_KEY_PASSWORD; alias aytdlp. `assembleRelease` enables R8/resource shrinking and produces ARM64, ARMv7, x86_64 and universal APKs. Generated native AARs and engine assets are ignored by git and prepared reproducibly before a build.

## Automation intents
Send `ACTION_SEND` with MIME `text/plain` and `Intent.EXTRA_TEXT`, or ACTION_VIEW with an HTTP(S) URL, to ShareActivity. The custom `con.themralex.aydlp.QUEUE` action also accepts `text/plain`/EXTRA_TEXT. All inputs are validated and shown for user-triggered download; a third-party app cannot silently execute custom arguments. Advanced arguments are allowlisted and URLs follow `--`; shell-executing options are rejected.

## Maintenance
See [YouTube runbook](docs/MAINTENANCE.md), [architecture plan](docs/PLAN.md) and [decisions](docs/DECISIONS.md). CI compiles and runs checks on pushes; a separate 16 KB emulator job records native/UI results. The initial release bootstraps a signing identity with an encrypted owner backup. Subsequent releases use owner-managed signing secrets; see docs/SIGNING.md. No credentials belong in the repository.

## Screenshots
Actual device screenshots are produced by the emulator workflow and uploaded as device evidence. No fabricated screenshots are included before that job succeeds.

## Legal
Users are responsible for copyright and each platform's terms. No DRM circumvention, ads, telemetry or accounts. See LICENSE, THIRD_PARTY_NOTICES.md and font/WebP license notices.
