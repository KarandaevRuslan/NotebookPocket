"""Regression coverage for tag-triggered APK builds and protected release signing."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parent.parent
WORKFLOW = (ROOT / '.github/workflows/release.yml').read_text(encoding='utf-8')

class TagApkWorkflowTest(unittest.TestCase):
    def test_tag_push_is_registered(self):
        self.assertRegex(WORKFLOW, r"(?m)^  push:\s*\n    tags: \['v\*'\]")

    def test_tag_build_is_independent_of_signing_and_emulator_gates(self):
        tag_job = WORKFLOW.split('  tag-apk:', 1)[1].split('  verify:', 1)[0]
        self.assertIn("github.event_name == 'push'", tag_job)
        self.assertIn('scripts/check_release.py', tag_job)
        self.assertIn('npm ci && npm run build', tag_job)
        self.assertIn(':app:assembleDebug', tag_job)
        self.assertNotIn('needs:', tag_job)
        self.assertNotIn('secrets.', tag_job)
        self.assertNotIn('environment: release', tag_job)

    def test_tag_output_is_installable_apk_and_checksum(self):
        tag_job = WORKFLOW.split('  tag-apk:', 1)[1].split('  verify:', 1)[0]
        self.assertIn('app/build/outputs/apk/debug/app-debug.apk', tag_job)
        self.assertIn('sha256sum *.apk > SHA256SUMS', tag_job)
        self.assertIn('actions/upload-artifact@', tag_job)
        self.assertIn('if-no-files-found: error', tag_job)
        self.assertIn('retention-days: 30', tag_job)

    def test_signed_release_requires_manual_dispatch_and_keeps_guards(self):
        manual = WORKFLOW.split('  verify:', 1)[1]
        self.assertIn("github.event_name == 'workflow_dispatch' && github.ref_type == 'tag'", manual)
        self.assertIn('needs: [verify, device-tests]', manual)
        self.assertIn('environment: release', manual)
        self.assertIn('secrets.ANDROID_KEYSTORE_BASE64', manual)
        self.assertIn(':app:assembleRelease', manual)
        self.assertIn('--draft --verify-tag', manual)

    def test_project_docs_link_to_nexus_upstream(self):
        url='https://github.com/KarandaevRuslan/Nexus'
        for name in ('README.md', 'ARCHITECTURE.md', 'docs/NEXUS.md'):
            self.assertIn(url, (ROOT / name).read_text(encoding='utf-8'))

if __name__ == '__main__':
    unittest.main()
