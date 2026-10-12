#!/usr/bin/env python3
"""Build WebP 1.5.0 with 16 KB LOAD alignment, then repack upstream FFmpeg AAR.
Original upstream binaries are untouched except the five verified misaligned WebP libraries.
"""
import argparse, hashlib, io, subprocess, urllib.request, zipfile, os
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument("--ndk",required=True);parser.add_argument("--cmake",default="cmake");parser.add_argument("--ninja",default="ninja");parser.add_argument("--source");parser.add_argument("--arm64-build");parser.add_argument("--x64-build");args=parser.parse_args()
root=Path(__file__).resolve().parents[1];native=root/"native";native.mkdir(exist_ok=True)
def download(url,path):
    if not path.exists():
        with urllib.request.urlopen(url,timeout=60) as response:path.write_bytes(response.read())
aar=native/"upstream-ffmpeg.aar";download("https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/ffmpeg/0.18.1/ffmpeg-0.18.1.aar",aar)
source=Path(args.source) if args.source else native/"libwebp"
if not source.exists():subprocess.run(["git","clone","--depth","1","--branch","v1.5.0","https://github.com/webmproject/libwebp.git",str(source)],check=True)
commit=subprocess.check_output(["git","-C",str(source),"rev-parse","HEAD"],text=True).strip();assert commit=="a4d7a715337ded4451fec90ff8ce79728e04126c"
builds={}
for abi,arg in [("arm64-v8a",args.arm64_build),("x86_64",args.x64_build)]:
    build=Path(arg) if arg else native/("webp-"+abi)
    if not (build/"libwebp.so").exists():
        flags=["-DBUILD_SHARED_LIBS=ON","-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"]+["-DWEBP_BUILD_"+x+"=OFF" for x in ["ANIM_UTILS","CWEBP","DWEBP","GIF2WEBP","IMG2WEBP","VWEBP","WEBPINFO","EXTRAS"]]
        subprocess.run([args.cmake,"-S",str(source),"-B",str(build),"-G","Ninja","-DCMAKE_MAKE_PROGRAM="+args.ninja,"-DCMAKE_TOOLCHAIN_FILE="+str(Path(args.ndk)/"build/cmake/android.toolchain.cmake"),"-DANDROID_ABI="+abi,"-DANDROID_PLATFORM=26"]+flags,check=True)
        subprocess.run([args.cmake,"--build",str(build),"-j4"],check=True)
    builds[abi]=build
with zipfile.ZipFile(aar) as original,zipfile.ZipFile(native/"ffmpeg-16kb.aar.new","w",zipfile.ZIP_DEFLATED) as output:
    for entry in original.infolist():
        data=original.read(entry)
        abi=next((x for x in builds if entry.filename=="jni/"+x+"/libffmpeg.zip.so"),None)
        if abi:
            buffer=io.BytesIO()
            with zipfile.ZipFile(io.BytesIO(data)) as packed,zipfile.ZipFile(buffer,"w",zipfile.ZIP_DEFLATED) as patched:
                for lib in packed.infolist():
                    replacement=builds[abi]/Path(lib.filename).name
                    patched.writestr(lib,replacement.read_bytes() if lib.filename.startswith("usr/lib/") and replacement.exists() and replacement.suffix==".so" else packed.read(lib))
            data=buffer.getvalue()
        output.writestr(entry,data)
os.replace(native/"ffmpeg-16kb.aar.new",native/"ffmpeg-16kb.aar")
subprocess.run(["python3",str(root/"scripts/audit_native.py"),str(native/"ffmpeg-16kb.aar")],check=True,stdout=(native/"native-audit.json").open("w"))
print("Patched FFmpeg AAR passed recursive 64-bit ELF alignment audit")
