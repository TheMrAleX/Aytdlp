"""Supervise the extractor without depending on Android /proc access.

ProcessBuilder must keep its stdin PIPE open for the lifetime of the job.
Only the watchdog reads that pipe; EOF means the owning app has gone away.
"""
import os
import runpy
import select
import signal
import sys

engine, token = sys.argv[1:3]
sys.argv = [engine] + sys.argv[3:]
if sys.argv[1:] == ["--aytdlp-verify-solver"]:
    import zipfile
    with zipfile.ZipFile(engine) as archive:
        if not any(name.startswith("yt_dlp_ejs/") for name in archive.namelist()):
            raise RuntimeError("Bundled EJS solver missing; update yt-dlp")
    print("AYTDLP_EJS_OK", flush=True)
    sys.exit(0)

os.setsid()
group = os.getpid()
watcher = os.fork()
if watcher == 0:
    signal.signal(signal.SIGTERM, lambda *_: os._exit(0))
    # Do not keep the extractor output pipe alive if the runner is killed.
    os.close(1)
    os.close(2)
    while True:
        ready, _, _ = select.select([0], [], [], 0.5)
        if os.getppid() != group or (ready and not os.read(0, 1024)):
            try:
                os.killpg(group, signal.SIGKILL)
            except ProcessLookupError:
                pass
            os._exit(0)

# yt-dlp and ffmpeg cannot consume the watchdog pipe or ask for input.
with open(os.devnull, "rb") as null:
    os.dup2(null.fileno(), 0)
print("AYTDLP_PID:" + str(group), flush=True)
try:
    runpy.run_path(engine, run_name="__main__")
finally:
    try:
        os.kill(watcher, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        os.waitpid(watcher, 0)
    except ChildProcessError:
        pass
