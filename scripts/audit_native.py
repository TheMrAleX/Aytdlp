#!/usr/bin/env python3
"""Audit ELF files including native libraries inside ZIP payloads disguised as .so."""
import io, json, struct, sys, zipfile
from pathlib import Path
results = []
def inspect(data, name):
    if data[:4] == b"\x7fELF":
        bits = data[4]; endian = "<" if data[5] == 1 else ">"
        offset = struct.unpack_from(endian + ("Q" if bits == 2 else "I"), data, 32 if bits == 2 else 28)[0]
        size, count = struct.unpack_from(endian + "HH", data, 54 if bits == 2 else 42)
        alignments = []
        for index in range(count):
            pos = offset + index * size
            if struct.unpack_from(endian + "I", data, pos)[0] == 1:
                alignments.append(struct.unpack_from(endian + ("Q" if bits == 2 else "I"), data, pos + (48 if bits == 2 else 28))[0])
        results.append({"file": name, "bits": bits * 32, "alignments": alignments, "compatible": bits == 1 or all(x >= 16384 for x in alignments)})
    elif data[:2] == b"PK":
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                for entry in archive.infolist():
                    if entry.file_size <= 200_000_000 and ("/lib/" in entry.filename or ".so" in entry.filename or entry.filename.endswith(".aar")):
                        inspect(archive.read(entry), name + "!" + entry.filename)
        except zipfile.BadZipFile:
            pass
for arg in sys.argv[1:]:
    path = Path(arg)
    for file in (path.rglob("*.so") if path.is_dir() else [path]):
        inspect(file.read_bytes(), str(file))
print(json.dumps(results, indent=2))
sys.exit(1 if any(not item["compatible"] for item in results) else 0)
