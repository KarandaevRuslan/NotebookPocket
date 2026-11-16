"""Guard the exact missing-Wrapper packaging regression seen on Windows."""
from pathlib import Path
import hashlib
import unittest
import zipfile

ROOT = Path(__file__).resolve().parent.parent


class GradleWrapperPackagingTest(unittest.TestCase):
    def test_official_wrapper_is_complete_and_verified(self):
        for name in ('gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.properties'):
            self.assertGreater((ROOT / name).stat().st_size, 0, name)
        jar = ROOT / 'gradle/wrapper/gradle-wrapper.jar'
        self.assertEqual(hashlib.sha256(jar.read_bytes()).hexdigest(),
                         '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046')
        with zipfile.ZipFile(jar) as archive:
            self.assertIsNone(archive.testzip())
            self.assertIn('org/gradle/wrapper/GradleWrapperMain.class', archive.namelist())
        properties = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text()
        self.assertIn('gradle-8.11.1-bin.zip', properties)
        self.assertIn('distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6', properties)

    def test_windows_checks_bundled_wrapper_before_rebuilding_web(self):
        script = (ROOT / 'build-apk.ps1').read_text()
        self.assertLess(script.index("$wrapper = Join-Path"), script.index('if (-not $SkipWeb)'))
        self.assertIn("$gradle = $wrapper", script)
        self.assertIn('Gradle Wrapper is incomplete', script)
        self.assertIn('gradle-wrapper.jar', script)
        self.assertIn('gradle-wrapper.properties', script)


if __name__ == '__main__':
    unittest.main()
