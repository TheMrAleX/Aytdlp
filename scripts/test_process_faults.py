import hashlib, os, signal, subprocess, sys, tempfile, time, unittest, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
class ProcessFaultTests(unittest.TestCase):
    def start(self, directory):
        return subprocess.Popen([sys.executable,str(ROOT/"fake_transfer.py"),str(directory)],start_new_session=True)
    def wait_for(self, condition):
        deadline=time.monotonic()+10
        while not condition():
            if time.monotonic()>deadline:self.fail("Timed out waiting for fault point")
            time.sleep(0.02)
    def test_pause_retains_partial_and_restart_matches(self):
        with tempfile.TemporaryDirectory() as temporary:
            d=Path(temporary);p=self.start(d);self.wait_for(lambda:(d/"media.part").exists() and (d/"media.part").stat().st_size>65536)
            os.killpg(p.pid,signal.SIGTERM);p.wait(timeout=3);retained=(d/"media.part").read_bytes();self.assertGreater(len(retained),0)
            p=self.start(d);p.wait(timeout=15);self.assertEqual(p.returncode,0);result=(d/"media.bin").read_bytes();self.assertEqual(result[:len(retained)],retained);self.assertEqual(len(result),1024*1024)
    def test_kill_during_merge_does_not_publish_partial(self):
        with tempfile.TemporaryDirectory() as temporary:
            d=Path(temporary);p=self.start(d);self.wait_for(lambda:(d/"media.pending").exists() and (d/"media.pending").stat().st_size>65536)
            source_hash=hashlib.sha256((d/"source.bin").read_bytes()).digest();os.killpg(p.pid,signal.SIGKILL);p.wait(timeout=3)
            self.assertFalse((d/"media.bin").exists());p=self.start(d);p.wait(timeout=15);self.assertEqual(hashlib.sha256((d/"media.bin").read_bytes()).digest(),source_hash)
    def runner(self, engine):
        return subprocess.Popen([sys.executable,str(ROOT.parent/"app/src/main/assets/runner.py"),str(engine),"supervisor-test"],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    def test_runner_launches_when_proc_access_is_denied(self):
        with tempfile.TemporaryDirectory() as temporary:
            d=Path(temporary)
            # Simulate Android denying proc reads inside the actual launcher.
            runner=ROOT.parent/"app/src/main/assets/runner.py"
            bootstrap=d/"bootstrap.py"
            bootstrap.write_text("import builtins, runpy, sys\noriginal=builtins.open\ndef guarded(path,*a,**kw):\n if str(path).startswith('/proc/'): raise PermissionError('Android proc denied')\n return original(path,*a,**kw)\nbuiltins.open=guarded\nrunpy.run_path(sys.argv.pop(1),run_name='__main__')\n")
            engine=d/"engine.py";engine.write_text("print('EXTRACTION_STARTED',flush=True)\n")
            p=subprocess.Popen([sys.executable,str(bootstrap),str(runner),str(engine),"proc-test"],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
            try:
                p.wait(timeout=5)
                self.assertEqual(p.returncode,0,p.stderr.read())
                self.assertIn('EXTRACTION_STARTED',p.stdout.read())
            finally:
                if p.poll() is None:p.kill()
                p.wait();p.stdin.close();p.stdout.close();p.stderr.close()
    def test_runner_kills_descendants_when_owner_pipe_closes(self):
        with tempfile.TemporaryDirectory() as temporary:
            d=Path(temporary);heartbeat=d/"heartbeat";engine=d/"engine.py"
            child="import pathlib,time\np=pathlib.Path("+repr(str(heartbeat))+")\nwhile True: p.write_text(str(time.monotonic()));time.sleep(.03)"
            engine.write_text("import subprocess,sys,time\nsubprocess.Popen([sys.executable,'-c',"+repr(child)+"])\ntime.sleep(60)\n")
            p=self.runner(engine)
            try:
                self.wait_for(heartbeat.exists)
                p.stdin.close()
                p.wait(timeout=5)
                self.assertNotEqual(p.returncode,0)
                last=heartbeat.read_text();time.sleep(.15)
                self.assertEqual(last,heartbeat.read_text(),'Orphaned descendant kept running')
            finally:
                if p.poll() is None:os.killpg(p.pid,signal.SIGKILL)
                p.wait();p.stdout.close();p.stderr.close()
    def test_solver_in_executable_zip_is_recognized(self):
        with tempfile.TemporaryDirectory() as temporary:
            engine=Path(temporary)/"yt-dlp"
            engine.write_bytes(b"#!/usr/bin/env python3\n")
            with zipfile.ZipFile(engine,"a") as archive:
                archive.writestr("yt_dlp_ejs/__init__.py", "")
            result=subprocess.run([sys.executable,str(ROOT.parent/"app/src/main/assets/runner.py"),str(engine),"solver-fixture","--aytdlp-verify-solver"],capture_output=True,text=True,timeout=10)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertIn("AYTDLP_EJS_OK",result.stdout)
    def test_metadata_writer_preserves_large_json_and_bounds_binary_output(self):
        import ast, io, json
        source = ast.parse((ROOT.parent / "app/src/main/assets/runner.py").read_text())
        node = next(n for n in source.body if isinstance(n, ast.ClassDef) and n.name == "MetadataOutput")
        namespace = {}
        exec(compile(ast.Module(body=[node], type_ignores=[]), "runner.py", "exec"), namespace)
        target = io.TextIOWrapper(io.BytesIO(), encoding="utf-8")
        writer = namespace["MetadataOutput"](target)
        payload = json.dumps({"title": "x" * 3_000_000})
        writer.write(payload)
        writer.flush()
        self.assertEqual(json.loads(target.buffer.getvalue())["title"], "x" * 3_000_000)
        with self.assertRaises(ValueError):
            writer.buffer.write(b"x" * (33 * 1024 * 1024))
        self.assertEqual(len(target.buffer.getvalue()), len(payload))

    def metadata_run(self, engine, output):
        args = [sys.executable, str(ROOT.parent / "app/src/main/assets/runner.py"), str(engine), "metadata-test", "--aytdlp-json-output", str(output)]
        p = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            p.wait(timeout=10)
            stdout, stderr = p.communicate()
            return subprocess.CompletedProcess(args, p.returncode, stdout, stderr)
        finally:
            if p.poll() is None:
                os.killpg(p.pid, signal.SIGKILL)
                p.communicate()

    def test_large_metadata_is_not_sent_to_diagnostic_pipe(self):
        import json
        with tempfile.TemporaryDirectory() as temporary:
            d = Path(temporary)
            engine = d / "engine.py"
            engine.write_text('import json; print(json.dumps({"title": "x" * 3_000_000}))')
            output = d / "metadata.json"
            result = self.metadata_run(engine, output)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertLess(len(result.stdout), 100)
            self.assertEqual(len(json.loads(output.read_text())["title"]), 3_000_000)

    def test_metadata_limit_rejects_oversized_results(self):
        with tempfile.TemporaryDirectory() as temporary:
            d = Path(temporary)
            engine = d / "engine.py"
            engine.write_text('print("x" * (33 * 1024 * 1024))')
            output = d / "metadata.json"
            result = self.metadata_run(engine, output)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Metadata exceeds", result.stderr)
            self.assertLessEqual(output.stat().st_size, 32 * 1024 * 1024)

    def test_solver_missing_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            engine=Path(temporary)/"yt-dlp"
            engine.write_bytes(b"#!/usr/bin/env python3\n")
            with zipfile.ZipFile(engine,"a") as archive:
                archive.writestr("yt_dlp/__init__.py", "")
            result=subprocess.run([sys.executable,str(ROOT.parent/"app/src/main/assets/runner.py"),str(engine),"solver-fixture","--aytdlp-verify-solver"],capture_output=True,text=True,timeout=10)
            self.assertNotEqual(result.returncode,0)
            self.assertIn("EJS solver missing",result.stderr)
if __name__=="__main__":unittest.main()
