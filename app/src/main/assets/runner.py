# Native Python process-group supervisor. Never invokes a shell.
import os, sys, signal, time, runpy
engine, token = sys.argv[1:3]
sys.argv = [engine] + sys.argv[3:]
# This bounded, read-only check launches no child processes.
if sys.argv[1:] == ["--aytdlp-verify-solver"]:
    import zipfile
    with zipfile.ZipFile(engine) as archive:
        if not any(name.startswith("yt_dlp_ejs/") for name in archive.namelist()):
            raise RuntimeError("Bundled EJS solver missing; update yt-dlp")
    print("AYTDLP_EJS_OK", flush=True)
    sys.exit(0)
os.setsid()
parent = os.getppid()
group = os.getpid()
parent_start = open("/proc/%d/stat" % parent).read().split(") ", 1)[1].split()[19]
print("AYTDLP_PID:" + str(group), flush=True)
watcher = os.fork()
if watcher == 0:
    signal.signal(signal.SIGTERM, lambda *_: os._exit(0))
    while True:
        try:
            same_parent = open("/proc/%d/stat" % parent).read().split(") ", 1)[1].split()[19] == parent_start
        except (OSError, IndexError):
            same_parent = False
        if os.getppid() != group or not same_parent:
            try: os.killpg(group, signal.SIGKILL)
            except ProcessLookupError: pass
            os._exit(0)
        time.sleep(0.5)
# Metadata goes to its own bounded file, never through the diagnostic line buffer.
metadata = None
if len(sys.argv) > 2 and sys.argv[1] == "--aytdlp-json-output":
    metadata = open(sys.argv[2], "w", encoding="utf-8")
    del sys.argv[1:3]

class MetadataOutput:
    def __init__(self, target):
        self.target = target
        self.size = 0

    def __getattr__(self, name):
        return getattr(self.target, name)

    @property
    def buffer(self):
        return self

    def write(self, text):
        self.size += len(text if isinstance(text, bytes) else text.encode("utf-8"))
        if self.size > 32 * 1024 * 1024:
            raise ValueError("Metadata exceeds 32 MiB; select fewer playlist items")
        return self.target.buffer.write(text) if isinstance(text, bytes) else self.target.write(text)

    def flush(self):
        self.target.flush()

original_stdout = sys.stdout
try:
    if metadata is not None:
        sys.stdout = MetadataOutput(metadata)
    runpy.run_path(engine, run_name="__main__")
finally:
    sys.stdout = original_stdout
    if metadata is not None:
        metadata.close()
    try: os.kill(watcher, signal.SIGTERM)
    except ProcessLookupError: pass
    try: os.waitpid(watcher, 0)
    except ChildProcessError: pass
