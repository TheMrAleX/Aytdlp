# Release signing and publication

The initial `v0.1.0` release can bootstrap a new RSA-4096 signing identity when no published release exists and no signing secrets are configured. The keystore and random passwords never enter git, release notes or plaintext artifacts. The workflow encrypts their backup with OpenSSL CMS AuthEnvelopedData / AES-256-GCM to `.github/signing-recipient.pem` and publishes only `signing-backup.cms`.

The recipient's private key is held privately by the owner. Recover the backup with:

```bash
openssl cms -decrypt -binary -inform DER -in signing-backup.cms \
  -recip recipient-cert.pem -inkey recipient-key.pem -out signing-backup.zip
```

The decrypted ZIP contains `aytdlp.jks`, `signing.json` and instructions. Keep it private and backed up; never commit or publish it. The signed APKs include a public certificate/fingerprint for identity checks.

## Subsequent releases

Configure repository Actions secrets from the retained backup:

- `AYTDLP_KEYSTORE_BASE64`: base64 of `aytdlp.jks`.
- `AYTDLP_STORE_PASSWORD`: its store password from `signing.json`.
- `AYTDLP_KEY_PASSWORD`: its key password from `signing.json`.

The alias is `aytdlp`. Create a `vMAJOR.MINOR.PATCH` tag or use the workflow's `release_tag` input. VersionName comes from the tag and VersionCode is `major * 1000000 + minor * 1000 + patch` (minor and patch below 1000). Quality checks, the 16 KB device gate, unit tests and release lint, signatures and ZIP/ELF audits must pass before publication. An existing release is left unchanged; failed builds do not publish an unsigned APK.

Changing to an unrelated signing key normally requires uninstalling the previous app. Android key-rotation mechanisms require the old key and additional configuration; this workflow does not create a rotation lineage. Preserve the initial key if compatible updates are desired.

Main-branch pushes currently publish `v0.1.1` when it does not yet exist. They do not publish a new version on every commit. Subsequent versions appear automatically from version tags with the signing secrets configured.
