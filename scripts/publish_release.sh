#!/usr/bin/env bash
set -euo pipefail
export AYTDLP_VERSION_NAME="${RELEASE_TAG#v}"
test -s release-assets/SHA256SUMS.txt
(cd release-assets && sha256sum --check SHA256SUMS.txt)

cat > release-notes.md <<'NOTES'
Native Android release build with R8/resource shrinking and RSA signing. Minimum Android 8 (API 26). Download the ARM64 APK for a modern phone, or universal for all included ABIs.

Checks: release compilation, domain unit tests, Android lint, apksigner, ZIP/ELF alignment; publication also requires quality checks, debug runtime/persistence/navigation tests, and a separate cold-start check of the optimized release APK on Android 15 / 16 KB.

This initial release does not yet implement the entire original product specification. Real YouTube media download/resume, OEM endurance and comparative performance are not certified by these checks. See docs/VALIDATION.md and docs/MAINTENANCE.md.

SHA256SUMS.txt contains artifact hashes. signing-certificate.pem and signing-fingerprint.txt identify the release signer. If present, signing-backup.cms is an authenticated encrypted backup; it contains no publicly readable signing credentials. The owner has the private recovery key.

Future tags publish here automatically after their checks pass, using owner-managed AYTDLP_KEYSTORE_BASE64, AYTDLP_STORE_PASSWORD and AYTDLP_KEY_PASSWORD secrets. Keep the same signing identity for compatible app updates.
NOTES

gh release create "$RELEASE_TAG" release-assets/* --draft --target "$GITHUB_SHA" \
  --title "Aytdlp ${AYTDLP_VERSION_NAME}" --notes-file release-notes.md
gh release edit "$RELEASE_TAG" --draft=false --latest
