#!/usr/bin/env python3
"""Publish only an authenticated, encrypted backup of bootstrap signing material."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--keystore", required=True)
    parser.add_argument("--recipient", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    key = Path(args.keystore)
    passwords = {name: os.environ[name] for name in
                 ("AYTDLP_STORE_PASSWORD", "AYTDLP_KEY_PASSWORD")}
    if not all(passwords.values()):
        raise ValueError("Signing passwords are missing")
    with tempfile.TemporaryDirectory() as directory:
        archive = Path(directory) / "signing-backup.zip"
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as backup:
            backup.write(key, "aytdlp.jks")
            backup.writestr("signing.json", json.dumps({
                "alias": "aytdlp", **passwords,
            }, indent=2))
            backup.writestr("README.txt", "Private Aytdlp signing backup. Never commit or publish these files.\n"
                            "Set AYTDLP_KEYSTORE_BASE64 to base64 of aytdlp.jks, and the two password secrets from signing.json.\n"
                            "Alias: aytdlp. Preserve this key for compatible updates.\n")
        subprocess.run(["openssl", "cms", "-encrypt", "-binary", "-aes-256-gcm",
                        "-in", str(archive), "-outform", "DER", "-out", args.output,
                        args.recipient], check=True)


if __name__ == "__main__":
    main()
