# Validation status

## Executed in this environment
- Maven/Google Maven, Gradle, GitHub and YouTube HTTPS responses checked.
- Real host extraction with yt-dlp 2026.08.19 / Node: jNQXAC9IVRw returned 16 formats with direct URLs. Media bytes and Android extraction were not tested by this command.
- Real debug build: ARM64, ARMv7, x86_64 and universal APKs.
- 14 compiled domain JUnit tests passed via JUnitCore, including cookie-header redaction.
- Two deterministic process-fault tests passed: transfer interruption with preserved partial bytes and kill during merge without publishing the incomplete output. These test the deterministic fixture, not actual YouTube media.
- Rebuilt five misaligned WebP libraries for each 64-bit ABI; recursive audit of universal debug APK inspected 776 ELF files with zero incompatible entries.
- SDK zipalign -c -P 16 -v 4 passed for the universal debug APK.
- Detekt completed with zero findings under the documented configuration.
- Clean release build with RSA-4096 signing/R8 succeeded. ktlintCheck, detekt and lintRelease passed; lint has 0 errors and 18 warnings. All four signed release APKs passed apksigner, zipalign -P 16 and recursive ELF audits (258 ARM64, 258 ARMv7, 260 x86_64, 776 universal ELF files). SHA256SUMS.txt records final hashes. GitHub release publication still requires signing secrets managed by the repository owner.

## Device checks required
Open Settings/Maintenance and run Health check; QuickJS, EJS and FFmpeg must be ready. Analyze a public video, download video and audio, play both, test a shared link, decline notifications once, then grant them and check actions. Pause halfway, close/reopen, resume and verify the file. Test interruption during merge, Wi-Fi changes, charging rules, reboot and SD/SAF storage. Force-stop requires user relaunch by Android design. Run a 16 KB device/emulator with `adb shell getconf PAGE_SIZE` equal to 16384. Test API 26, 33, 34, 36 and 37 and an aggressive OEM. Do not assume 6+ hour or Doze behavior without measuring it.

## Remaining original scope, not claimed delivered
Full playlist/channel pagination and range editing; duplicate replace UX/download-archive integration; exact device codec capability warnings; arbitrary metadata tag editing/chapter-based clipping; drag/swipe queue reordering and per-item overnight windows; full grid/sort/folder library tools and moving completed media; cookie WebView login; runtime/FFmpeg in-app binary replacement; dynamic thumbnail accent/shared transitions; Quick Settings tile, shortcuts and Glance widget; generated device baseline profiles/Macrobenchmark comparative measurements. Native component updates intentionally use signed APK updates. These are development backlog, not hidden mocks or fake production results.

Live Update promotion, OEM endurance, Android 17 playback/native hardening, real-media pause/resume/merge corruption tests, Additional Android device/version tests and accessibility/performance measurements remain unverified until their evidence exists. The application makes no verified claim of being faster or more stable than Seal/YTDLnis.

GitHub Actions verify job succeeded in run 38314689735: build, domain tests, lint, ktlint, detekt, process fixtures and APK alignment audits. The initial 16 KB device job failed before emulator startup because sdkmanager was not on PATH; setup has been corrected. That run did not pass the device gate; see the later successful continuation below.

Signed artifacts and a private signing backup were saved for delivery. Host execution disconnected after successful uploads, preventing further local metadata writeback. The delivered SHA256SUMS.txt accompanies the downloaded APKs; the repository checksum record below was generated before the final runtime-health serialization rebuild and must not be used to validate the final delivered files.


## Continuation verified 2026-10-13
Source commit: f07a518924f9180d0e512ff8c37a4556771a5e46.
Evidence: https://github.com/TheMrAleX/Aytdlp/actions/runs/38335090282

- Both verify and device_16kb jobs completed successfully.
- 18 domain tests passed, including repeated search normalization and preservation of extractor JSON larger than 64 KiB.
- Four host tests passed: interrupted transfer, interrupted merge, EJS detection in an executable ZIP with a Python shebang, and rejection when EJS is missing.
- Android 15 / API 35 x86_64 emulator confirmed PAGE_SIZE=16384. Three instrumented tests passed: bundled runtime/solver health, database persistence across reopen, and onboarding/navigation.
- Health checks execute QuickJS arithmetic, yt-dlp --version and FFmpeg --version. EJS availability is inspected with Python ZIP support. This does not prove a real YouTube challenge was solved or media downloaded.
- The universal debug APK recursive audit inspected 776 ELF objects with zero incompatible objects; SDK zipalign -P 16 passed.
- Releases now depend on the device_16kb gate as well as verify. KVM permissions are enabled only for the CI runner user, and diagnostic logs/page-size evidence are retained on failures.

Resolved blockers: AndroidJUnit4 rejecting a non-void persistence test; repeated ytsearch1 prefixes; silent 64 KiB truncation of extractor JSON; Android ZipFile rejecting the executable yt-dlp ZIP's shebang. The last fix applies to health checks and staged updates.

A universal development APK was rebuilt from this commit. It is a debug build, not a published signed production release. The earlier private signing key and release deliverables are not available in this workspace; preserve the existing signing identity and configure the owner-managed secrets for a production release.

The remaining original functional scope listed above remains pending. Real Android YouTube extraction/download, real-media resume/merge integrity, reboot/network/OEM endurance, 6+ hour transfers and performance comparisons are not claimed verified by these tests.


## Signed release publication 2026-10-13
Published release: https://github.com/TheMrAleX/Aytdlp/releases/tag/v0.1.0
Release source: 9cd60d74d94ac96ac411692a0cf1c002fc4c423e.
Publication evidence: https://github.com/TheMrAleX/Aytdlp/actions/runs/38338855710
Full quality/device workflow: https://github.com/TheMrAleX/Aytdlp/actions/runs/38338855775

Four real release APKs (ARM64, ARMv7, x86_64, universal) were built with R8/resource shrinking and a newly generated RSA-4096 key authorized by the owner. All four passed APK v2 signature verification, SDK ZIP alignment and recursive ELF audits. SHA256SUMS.txt and per-APK native reports are published alongside the APKs.

Signer certificate SHA-256:
4a8f566760b23bba9ee3c27109b4d2c3b791dedcb52c74c1c87b832e35dbac4b

The private signing backup was recovered from authenticated encrypted CMS, its JKS password/integrity was verified, and its certificate matched the published certificate. Only the encrypted backup and public certificate/fingerprint are published; the owner received the private backup separately. See docs/SIGNING.md for future release-secret setup.

The first release attempt requested an unavailable testReleaseUnitTest task. The corrected command builds assembleRelease and lintRelease; mandatory domain tests run in the preceding verified quality gate. The publication retry checked that application/build inputs matched the successfully tested source. The temporary retry workflow is removed after successful publication.

This resolves the production signing/publication blocker for v0.1.0. It does not resolve the remaining functional scope or unverified real-media/OEM/performance behavior listed above.


## Release startup crash correction, 2026-10-13
The published v0.1.0 optimized APK was **not** launched by the earlier debug instrumented tests. Its startup crash was reproduced directly from the published x86_64 APK on API 35 / 16 KB:
https://github.com/TheMrAleX/Aytdlp/actions/runs/38340929153

The log shows ExceptionInInitializerError in YoutubeDL.initPython -> ZipUtils.unzip, caused by a reflective ZIP extra-field implementation becoming non-concrete under R8. app/proguard-rules.pro now retains concrete ZipExtraField implementations and their public no-argument constructors, while still permitting name obfuscation. R8 and resource shrinking remain enabled.

Candidate application source: 006a9f4eb8625d202def94156a412307c097190b.
Optimized release compilation, lintRelease, signatures, ZIP/ELF audits and API 35 / 16 KB cold start succeeded:
https://github.com/TheMrAleX/Aytdlp/actions/runs/38341299316
Its first API 37 matrix job failed in SDK setup before app installation; this is not an app validation result. The subsequent API 37.2 attempt failed because the emulator reported boot complete before the input service was ready. A separate Android 17 test now waits for framework services and collects emulator/system logs. Android 17 validation is still pending at this checkpoint.

Four v0.1.1 APKs were re-signed with the original v0.1.0 key; all signatures verify and the signer SHA-256 matches the fingerprint above. Every non-META-INF ZIP entry was compared against the tested optimized candidate and remained byte-for-byte identical. These APKs are delivered privately; v0.1.1 is not yet published on GitHub. Existing-key repository secrets are needed for automated publication; no private signing material is committed.

The permanent publication workflow now builds/signs first, cold-starts the optimized release APK, and only then publishes it. The debug runtime/navigation gate remains separate. This correction does not establish physical ARM64 / Android 17 compatibility, live YouTube download, or the remaining product scope.


## UI/runtime review — 2026-10-13
Source d5dff56e0b05a76040abf8108ebd88d23577b2cb passed https://github.com/TheMrAleX/Aytdlp/actions/runs/38448972630 : 18 domain tests, six host process tests, four Android instrumented tests on API 35 / 16 KB, build, ktlint, detekt and native alignment audit. Android lint had zero errors and 20 warnings. The Android cancellation test now verifies canceling a silent real Python process and reusing its token; it initially exposed cancellation being misreported as an extraction error, which was corrected.

The Preview package is isolated from production. Captures were visually inspected: download/error, queue, library and settings. Inspection identified system-bar icon contrast and capture timing issues; the final polish corrects both and adds vertical spacing between accent rows. The PR records the final follow-up CI result. This does not verify a real Android YouTube download, ARM64 physical-device behavior, optimized release/R8 behavior for this revision, or long-running transfers.
