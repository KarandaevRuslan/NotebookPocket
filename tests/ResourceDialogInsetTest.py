"""Source contracts for full-window resource dialogs and landscape insets.

These tests cannot replace Android keyboard/rotation instrumentation tests.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent.parent
VIEWER = ROOT / 'app/src/main/java/dev/pocket/notebook/feature/files/viewer'


class ResourceDialogInsetTest(unittest.TestCase):
    def test_resource_dialogs_use_full_window_and_own_ime_insets(self):
        for filename in ['ResourceTextEditor.kt', 'ResourceFileViewer.kt']:
            with self.subTest(filename=filename):
                code = (VIEWER / filename).read_text()
                self.assertIn('decorFitsSystemWindows = false', code)
                self.assertIn('usePlatformDefaultWidth = false', code)
                self.assertIn('Modifier.fillMaxSize()', code)
                self.assertIn('.imePadding()', code)
                self.assertIn('contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)', code)
                self.assertIn('windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)', code)

    def test_text_editor_does_not_pad_landscape_display_cutout_side(self):
        code = (VIEWER / 'ResourceTextEditor.kt').read_text()
        self.assertIn('WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)', code)
        self.assertIn('WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)', code)
        self.assertEqual(code.count('.windowInsetsPadding('), 2)
        self.assertNotIn('.safeDrawingPadding()', code)

    def test_image_viewer_insets_unchanged(self):
        code = (VIEWER / 'ResourceFileViewer.kt').read_text()
        self.assertIn('Modifier.fillMaxSize().safeDrawingPadding().imePadding()', code)
