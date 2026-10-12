import hashlib, os, signal, subprocess, sys, tempfile, time, unittest
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
if __name__=="__main__":unittest.main()
