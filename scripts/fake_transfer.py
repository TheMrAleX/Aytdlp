# Deterministic fault-injection engine; never included in production.
import os, sys, time
from pathlib import Path
root=Path(sys.argv[1]); root.mkdir(exist_ok=True)
part=root/"media.part"; total=1024*1024
with part.open("ab") as output:
    while part.stat().st_size < total:
        output.write(bytes([part.stat().st_size//8192 % 251])*8192);output.flush();os.fsync(output.fileno());time.sleep(0.025)
source=root/"source.bin";part.replace(source)
merged=root/"media.pending"
with source.open("rb") as input,merged.open("wb") as output:
    while True:
        block=input.read(8192)
        if not block:break
        output.write(block);output.flush();time.sleep(0.025)
merged.replace(root/"media.bin")
