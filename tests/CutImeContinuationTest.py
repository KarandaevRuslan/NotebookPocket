from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent


class CutImeContinuationTest(unittest.TestCase):
    def test_production_state_machine_handles_both_ime_event_orders(self):
        java = shutil.which('java')
        if not java:
            self.skipTest('JDK 17 is required for native Cut state tests')
        env = os.environ.copy()
        jdk = Path(java).resolve().parent.parent
        if os.name != 'nt':
            env['LD_LIBRARY_PATH'] = os.pathsep.join([str(jdk / 'lib'), str(jdk / 'lib/server'), env.get('LD_LIBRARY_PATH', '')])
        with tempfile.TemporaryDirectory(dir=ROOT) as output:
            compile_result = subprocess.run([
                java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', output,
                str(ROOT / 'app/src/main/java/dev/pocket/notebook/web/CutImeContinuation.java'),
                str(ROOT / 'tests/CutImeContinuationHarness.java')
            ], env=env, capture_output=True, text=True, timeout=30)
            self.assertEqual(compile_result.returncode, 0, compile_result.stdout + compile_result.stderr)
            result = subprocess.run([java, '-cp', output, 'dev.pocket.notebook.web.CutImeContinuationHarness'],
                                    env=env, capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('14 event-order/cancellation scenarios passed', result.stdout)


if __name__ == '__main__':
    unittest.main()
