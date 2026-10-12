#!/usr/bin/env python3
import hashlib, urllib.request
from pathlib import Path
VERSION="2026.08.19"
EXPECTED="1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6"
root=Path(__file__).resolve().parents[1];target=root/"app/src/main/assets/yt-dlp"
with urllib.request.urlopen("https://github.com/yt-dlp/yt-dlp/releases/download/"+VERSION+"/yt-dlp",timeout=60) as response:data=response.read()
assert hashlib.sha256(data).hexdigest()==EXPECTED,"Bundled engine checksum mismatch"
target.write_bytes(data)
print("Bundled yt-dlp",VERSION,"verified")
