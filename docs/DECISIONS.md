# Decisions

## Identity and license
User chose Aytdlp and exact package `con.themralex.aydlp` (the `con` spelling is intentional). New application code is GPL-3.0-only to match the GPL wrapper. Preserve the initial MIT license attribution in THIRD_PARTY_NOTICES. Never commit signing material.

## Sources checked 2026-10-12
- https://developer.android.com/build/releases/gradle-plugin — stable AGP 9.4, SDK 37 compatibility, Gradle >=9.6, JDK >=17.
- https://developer.android.com/about/versions/17/behavior-changes-17 — native DCL read-only files, background audio service, large-screen resizing.
- https://developer.android.com/develop/background-work/background-tasks/uidt — UIDT API 34+, notification attachment, user-visible launch constraints and force-stop behavior.
- https://developer.android.com/develop/background-work/services/fgs/timeout — dataSync timeout must stop service promptly.
- https://developer.android.com/develop/ui/views/notifications/live-update — supported progress notifications, system eligibility conditions.
- https://github.com/yt-dlp/yt-dlp/wiki/EJS — EJS mandatory for YouTube; QuickJS supported; Bun deprecated. Official zipimport yt-dlp bundles EJS.
- https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide — default client policy changes frequently; web SABR, android_vr lacks made-for-kids, web_safari currently offers HLS, tv can return DRM/SABR.
- https://github.com/yausername/youtubedl-android — upstream now distributes JunkFood02 Maven coordinates; releases add 16 KB and QuickJS.
- https://github.com/Brainicism/bgutil-ytdlp-pot-provider — service/provider infrastructure is unsuitable as a default offline Android dependency; no embedded Node server or remote attestation service. Client configuration remains editable locally.
- https://ffmpeg.org/legal.html — inspect concrete FFmpeg build flags and notices; reject nonfree builds.

## Execution
Compile SDK 37.2 (stable) using the minor API DSL. The old repository2-1 SDK manifest omitted API 37; repository2-3 publishes platforms;android-37.2. Target SDK 36 initially until native DCL and audio hardening are tested on Android 17. No orientation or resizing restrictions. API 34+ user-triggered downloads use UIDT. Scheduled/recovery work uses ordinary constrained jobs; UIDT cannot be initiated arbitrarily from the background. API 26-33 uses a foreground service. WorkManager handles short maintenance tasks and scheduled wakeups, not an unbounded six-hour foreground dataSync loop.

## Native engine isolation
Use upstream for native runtime initialization and packaged dependencies. Own ProcessBuilder execution passes a List<String> and `--` before each input; never call shell, wrapper destroyProcessById (its upstream implementation constructs shell commands), or accept --exec/--config-location custom arguments. Bounded output prevents unbounded process logs. Pause sends TERM then KILL after timeout. Runtime self-test is separate from live extraction.

## Continuation semantics
`--continue` restarts extraction before reusing partial media, refreshing signed URLs. Byte-exact continuation depends on protocol and server Range support. HLS live segments that are no longer available cannot be recovered. A killed FFmpeg merge restarts from retained source streams; an incomplete merged output is not published. Do not promise exact continuation for every supported site.

## aria2c
Omitted: no measurement yet demonstrates an advantage over yt-dlp fragment concurrency with equivalent resumability. Fragment concurrency remains configurable.

## Updates and native code
yt-dlp zipimport updates are official HTTPS release artifacts checked against official SHA2-256SUMS. A hash served by the same origin is integrity checking, not an independent signature. Stage, validate imports/runtime, smoke-test, then activate atomically; preserve bundled and previous versions. QuickJS and FFmpeg native updates travel in signed APK updates, avoiding arbitrary native executable loading and ABI mistakes. EJS updates with official yt-dlp bundle. No remote configuration or downloaded executable plugins by default.

## Current limitations
Host HTTPS checks returned real Maven, Gradle, GitHub and YouTube content. They do not prove Android extraction, media CDN reachability, 16 KB runtime behavior, OEM survival or benchmark performance. Device-validation evidence must be recorded separately.

## Native alignment repair
Recursive inspection found five WebP libraries inside the published FFmpeg payload misaligned on each 64-bit ABI. Rebuilt WebP v1.5.0 from commit a4d7a715337ded4451fec90ff8ce79728e04126c with NDK r28c and max-page-size=16384, then atomically repacked the AAR. Recursive ELF audit passes for both 64-bit ABIs; 32-bit ABIs use 4 KB pages and are not labeled 16 KB devices. Run-time compatibility still requires device tests. The patched AAR is generated reproducibly, never committed.

## Live extraction evidence
Host yt-dlp 2026.08.19 with Node extracted jNQXAC9IVRw: 16 formats and 16 direct URLs. No media download or Android execution is implied. The old BaW_jenozKc test returned unavailable, so the smoke URL was changed. Smoke rejection preserves the previous engine and may indicate network/video availability rather than an extractor defect.
