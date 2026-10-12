# YouTube maintenance runbook

## 1. Preserve evidence and partial files
Pause the affected job. Do not clear app data, uninstall, or cancel the job: those actions can remove private .part/.ytdl state. Settings → Maintenance → Copy report records component versions, client chain and update outcome. Reports redact URLs, cookie/header lines and tokens. Never attach cookies.txt or the signing backup to an issue.

## 2. Classify the failure before changing clients
- Network/timeouts: verify Internet and Wi-Fi/charging constraints, retry after the connection returns.
- 429: wait, reduce simultaneous jobs and fragment concurrency; avoid repeated rapid extraction.
- 403 without explicit token/challenge evidence: refresh extraction to renew signed URLs, then compare clients. It is not automatically a PO Token failure.
- Missing JavaScript runtime: run Health check. The bundled QuickJS arithmetic test must return 42.
- EJS/nsig/challenge errors: update yt-dlp together with its bundled EJS solver. QuickJS native changes require a signed app update.
- Requested format unavailable: analyze again, clear the exact format ID or select another format.
- Private/age-restricted: import your own Netscape cookies.txt only when your account has access. Cookies are AES-GCM encrypted with Android Keystore and excluded from backups.
- Geographic restriction: the service may not offer the content in your region.
- No space: allow source streams plus merged output and destination-copy space; free storage and resume.
- Extractor regression: try a verified Nightly update, retain the previous working engine, and report the redacted diagnostic.

## 3. Update safely
Settings → Maintenance → choose Stable/Nightly/Master → Verify update and smoke-test. Downloads come only from official yt-dlp repositories over HTTPS. The app verifies SHA-256 against the official checksum list, checks bundled EJS and version execution, runs a real smoke extraction, then atomically activates the candidate. Failure rejects the candidate and preserves the current/bundled engine. A checksum is not an independent signature. The smoke video can become unavailable; distinguish that from a broken extractor.

The current smoke URL is https://www.youtube.com/watch?v=jNQXAC9IVRw. BaW_jenozKc was unavailable during development. Maintainers can change the source constant and rebuild if a smoke video disappears. Smoke test network failures intentionally prevent automatic activation.

## 4. Playback clients
Settings → Engine → YouTube client chain. Initial chain: android_vr,web_safari,web_embedded. The value is locally editable without publishing another APK.
- android_vr: currently useful without PO Tokens; made-for-kids content unavailable.
- web_safari: currently offers HLS variants without GVS PO Tokens; other variants can require tokens/SABR.
- web_embedded: limited to embeddable videos.
- tv: may return DRM/SABR without suitable cookies. DRM is never bypassed.
Follow https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide and https://github.com/yt-dlp/yt-dlp/wiki/EJS before changing defaults. Do not assume yesterday's client behavior still applies. bgutil is not bundled: its provider/server runtime and attestation lifecycle require a separate Android implementation, not merely a Python plugin import.

## 5. Recovery and system behavior
Pausing terminates the process group and preserves staging files; resuming re-extracts with --continue. HTTP continuation depends on server Range support. Expired live segments cannot always be recovered. FFmpeg merge interruption restarts from retained source streams. Incomplete destination files are not intentionally published.
Android 14+ user-triggered jobs use UIDT; automatic/scheduled wakeups use ordinary jobs under system quotas. Earlier Android uses a foreground service/WorkManager fallback. A system timeout must release work. Android force-stop intentionally blocks automatic restart until the user opens the app again. Manufacturer background restrictions require device testing; battery settings help is available without requesting blanket exemptions.

## 6. Build and native maintenance
Run scripts/prepare_engine.py and scripts/prepare_native.py before Gradle. The latter rebuilds WebP v1.5.0 with NDK r28c and repairs five misaligned libraries per 64-bit ABI in FFmpeg's ZIP payload. Audit nested ZIP native files, not only top-level .so files. Run scripts/audit_native.py on every final APK and zipalign -c -P 16 -v 4. Alignment inspection does not replace running on a 16 KB device. Runtime/FFmpeg native updates ship through signed APKs.

## 7. Signing and releases
Keep the signing backup private and store at least two offline copies. Losing the key prevents updating installed releases under the same identity. Repository secrets: AYTDLP_KEYSTORE_BASE64, AYTDLP_STORE_PASSWORD and AYTDLP_KEY_PASSWORD; alias aytdlp. Never commit these. Tag v* after CI/device acceptance. The release workflow refuses to publish unsigned artifacts and includes SHA256SUMS.
