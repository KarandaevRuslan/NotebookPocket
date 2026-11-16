from pathlib import Path
import importlib.util
import tempfile
import subprocess
import unittest

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('hygiene', ROOT/'scripts/check_repository.py')
hygiene = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hygiene)

class RepositoryHygieneTest(unittest.TestCase):
    def test_generated_artifacts_and_unreviewed_images_are_rejected(self):
        paths = ['web/.test-cache/editor.mjs','app/build/app.apk','docs/new-image.svg','docs/photo.PNG','keys/release.jks','local.properties','.patch-backups/old/source.kt']
        self.assertEqual(len(paths), len(hygiene.violations(paths)))
        self.assertEqual([], hygiene.violations(sorted(hygiene.APPROVED_IMAGES)))
        self.assertEqual([], hygiene.violations(['gradle/wrapper/gradle-wrapper.jar','LICENSE.md','web/package-lock.json']))
    def test_gitignore_hides_new_artwork_but_keeps_required_runtime_fixtures(self):
        with tempfile.TemporaryDirectory() as folder:
            subprocess.run(['git','init','-q',folder], check=True)
            Path(folder,'.gitignore').write_text((ROOT/'.gitignore').read_text())
            paths = ['docs/new.png','docs/new.svg','web/.test-cache/test.mjs','app/src/main/assets/web/assets/editor.js']
            p = subprocess.run(['git','-C',folder,'check-ignore','--stdin'], input='\n'.join(paths)+'\n', capture_output=True,text=True)
            self.assertEqual(set(paths),set(p.stdout.splitlines()))
            for path in hygiene.APPROVED_IMAGES:
                p=subprocess.run(['git','-C',folder,'check-ignore',path],capture_output=True)
                self.assertEqual(1,p.returncode,path)
    def test_actions_are_pinned_and_signing_is_not_available_to_pull_request_jobs(self):
        import re
        for path in (ROOT/'.github/workflows').glob('*.yml'):
            for ref in re.findall(r'uses:\s+([^\s#]+)',path.read_text()):
                if ref.startswith('./'): continue
                self.assertRegex(ref,r'@[0-9a-f]{40}$')
        ci=(ROOT/'.github/workflows/ci.yml').read_text()
        self.assertNotIn('secrets.',ci)
        release=(ROOT/'.github/workflows/release.yml').read_text()
        self.assertIn('--draft --verify-tag',release)
        self.assertIn('environment: release',release)
        self.assertNotIn('pull_request_target',release)

if __name__ == '__main__': unittest.main()
