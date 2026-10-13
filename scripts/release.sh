#!/usr/bin/env bash
set -euo pipefail

test "$RELEASE_TAG" != "" && [[ "$RELEASE_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]
export AYTDLP_VERSION_NAME="${RELEASE_TAG#v}"
export AYTDLP_VERSION_CODE
AYTDLP_VERSION_CODE="$(python3 - <<'PY'
import os
major, minor, patch = map(int, os.environ['AYTDLP_VERSION_NAME'].split('.'))
assert 0 <= minor < 1000 and 0 <= patch < 1000
code = major * 1000000 + minor * 1000 + patch
assert 0 < code <= 2100000000
print(code)
PY
)"
export AYTDLP_KEYSTORE="$RUNNER_TEMP/aytdlp-release.jks"
mkdir -p release-assets

if test -n "${KEYSTORE_BASE64:-}"; then
  test -n "${AYTDLP_STORE_PASSWORD:-}" && test -n "${AYTDLP_KEY_PASSWORD:-}"
  printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$AYTDLP_KEYSTORE"
else
  # Bootstrap one initial identity; later releases must preserve that identity.
  published="$(gh api "repos/$GH_REPO/releases" --jq '[.[] | select(.draft == false)] | length')"
  test "$published" = 0 || { echo 'Configure the existing signing key in repository secrets for subsequent releases.'; exit 1; }
  export AYTDLP_STORE_PASSWORD="$(openssl rand -hex 32)"
  export AYTDLP_KEY_PASSWORD="$AYTDLP_STORE_PASSWORD"
  echo "::add-mask::$AYTDLP_STORE_PASSWORD"
  keytool -genkeypair -alias aytdlp -keyalg RSA -keysize 4096 -validity 36500 \
    -storetype JKS -keystore "$AYTDLP_KEYSTORE" \
    -storepass:env AYTDLP_STORE_PASSWORD -keypass:env AYTDLP_KEY_PASSWORD \
    -dname 'CN=Aytdlp, OU=Release, O=Aytdlp'
  python3 scripts/seal_signing_backup.py --keystore "$AYTDLP_KEYSTORE" \
    --recipient .github/signing-recipient.pem --output release-assets/signing-backup.cms
fi

./gradlew assembleRelease lintRelease --max-workers=2
keytool -exportcert -rfc -alias aytdlp -keystore "$AYTDLP_KEYSTORE" \
  -storepass:env AYTDLP_STORE_PASSWORD -file release-assets/signing-certificate.pem

for apk in app/build/outputs/apk/release/*.apk; do
  "$ANDROID_HOME/build-tools/37.0.0/apksigner" verify --verbose --print-certs "$apk"
  "$ANDROID_HOME/build-tools/37.0.0/zipalign" -c -P 16 -v 4 "$apk"
  name="$(basename "$apk")"
  abi="${name#app-}"
  abi="${abi%-release.apk}"
  destination="release-assets/Aytdlp-${AYTDLP_VERSION_NAME}-${abi}.apk"
  cp "$apk" "$destination"
  python3 scripts/audit_native.py "$destination" > "$destination.native.json"
done

openssl x509 -in release-assets/signing-certificate.pem -noout -sha256 -fingerprint > release-assets/signing-fingerprint.txt
(cd release-assets && sha256sum *.apk signing-certificate.pem signing-fingerprint.txt *.native.json > SHA256SUMS.txt)
if test -f release-assets/signing-backup.cms; then
  (cd release-assets && sha256sum signing-backup.cms >> SHA256SUMS.txt)
fi

# CI runs a cold-start check of this optimized APK before publication.
if test "${RELEASE_PUBLISH:-1}" = 1; then
  bash scripts/publish_release.sh
fi
