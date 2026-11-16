"""Static guards for Windows deployment wiring (full PowerShell execution needs Windows)."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parent.parent


class AndroidDeployWrappersTest(unittest.TestCase):
    def test_build_keeps_web_and_gradle_and_deploys(self):
        s = (ROOT / 'build-apk.ps1').read_text()
        self.assertIn('[switch]$SkipWeb', s)
        self.assertIn("@(':app:assembleDebug')", s)
        self.assertIn('[switch]$NoInstall', s)
        self.assertIn('[switch]$Install', s)
        self.assertIn('Install-NotebookApkForUser0', s)
        self.assertIn('Get-NotebookAdbSerial', s)
        self.assertIn('No authorized Android device', s)

    def test_user_zero_only_and_preserves_data(self):
        s = (ROOT / 'scripts/AndroidDeploy.ps1').read_text()
        self.assertRegex(s, r'install -r -t --user 0 \$fullApk')
        self.assertRegex(s, r'pm list packages --user 0 dev\.pocket\.notebook')
        self.assertRegex(s, r'am start --user 0 -n dev\.pocket\.notebook/\.MainActivity')
        self.assertIn('pm list users', s)
        self.assertIn('am get-current-user', s)
        self.assertNotRegex('\n'.join(line for line in s.splitlines() if not line.lstrip().startswith('#')), r'\badb\s+uninstall\b|\bpm\s+clear\b|\bpm\s+remove-user\b')
        self.assertIn('Multiple Android devices connected', s)

    def test_wrappers_delegate_to_shared_helper(self):
        for name in ['install-apk.ps1', 'run-app.ps1']:
            s = (ROOT / name).read_text()
            self.assertIn('AndroidDeploy.ps1', s)
            self.assertIn('Get-NotebookAdbSerial', s)
        build_install = (ROOT / 'build-install.ps1').read_text()
        self.assertIn("'build-apk.ps1'", build_install)
        self.assertIn('-Install', build_install)

    def test_diagnostics_logs_do_not_require_file(self):
        s = (ROOT / 'docs/BUILDING.md').read_text()
        self.assertIn("adb logcat -v time -s NotebookPocket:V AndroidRuntime:E '*:S'", s)
        s = (ROOT / 'docs/BUILDING.md').read_text()
        self.assertIn('without creating a file', s)
        self.assertIn("adb logcat -v time -s NotebookPocket:V AndroidRuntime:E '*:S'", s)


if __name__ == '__main__':
    unittest.main()
