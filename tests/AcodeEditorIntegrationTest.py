"""Structural guards for the Compose-to-Acode host boundary."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / 'app/src/main/java/dev/pocket/notebook/web'


class AcodeEditorIntegrationTest(unittest.TestCase):
    def test_pipeline_modes_batches_and_frame_metrics_are_debug_scoped(self):
        control = (WEB / 'EditorDiagnosticsControl.kt').read_text()
        bridge = (WEB / 'EditorBridge.kt').read_text()
        frames = (WEB / 'EditorFrameDiagnostics.kt').read_text()
        activity = (ROOT / 'app/src/main/java/dev/pocket/notebook/MainActivity.kt').read_text()
        self.assertRegex(bridge, r'@JavascriptInterface\s+fun traceBatch')
        self.assertIn('@JavascriptInterface fun diagnosticOptions', bridge)
        self.assertIn('json.length > 65536', control)
        self.assertIn('coerceAtMost(32)', control)
        self.assertIn('traceWorker.queue.clear()', control)
        self.assertIn('epoch != traceGeneration', control)
        self.assertIn('selectionMode', control)
        self.assertIn('revealMode', control)
        self.assertIn('setDiagnosticsOptions', control)
        self.assertIn('LAYOUT_MEASURE_DURATION', frames)
        self.assertIn('removeOnFrameMetricsAvailableListener', frames)
        self.assertIn('worker?.quitSafely()', frames)
        self.assertIn('EditorDiagnosticsControl.release(this)', activity)

    def test_diagnostic_switch_is_debug_only_and_uses_central_log(self):
        control = (WEB / 'EditorDiagnosticsControl.kt').read_text()
        bridge = (WEB / 'EditorBridge.kt').read_text()
        activity = (ROOT / 'app/src/main/java/dev/pocket/notebook/MainActivity.kt').read_text()
        self.assertIn('var enabled = false', control)
        self.assertIn('ApplicationInfo.FLAG_DEBUGGABLE', control)
        self.assertIn('ContextCompat.RECEIVER_EXPORTED', control)
        self.assertIn('dev.pocket.notebook.EDITOR_DIAGNOSTICS', control)
        self.assertIn('EditorDiagnosticsControl.install(this)', activity)
        self.assertIn('if (EditorDiagnosticsControl.enabled) NotebookLog.webTrace(json)', bridge)
        self.assertIn('setDiagnosticsEnabled($enabled)', control)

    def test_native_menu_and_input_use_acode_port(self):
        native = (WEB / 'EditorActionModeWebView.java').read_text()
        bridge = (WEB / 'EditorBridge.kt').read_text()
        factory = (WEB / 'WebViewFactory.kt').read_text()
        self.assertIn('suppressActionModeUi(mode, menu)', native)
        self.assertIn('callback.onCreateActionMode(mode, menu)', native)
        self.assertIn('mode.hide(0)', native)
        self.assertNotIn('mode.finish()', native)
        self.assertIn('ViewCompat.getRootWindowInsets(this)', native)
        self.assertIn('fun setNativeContextMenuDisabled(disabled: Boolean)', bridge)
        self.assertIn('if (editor) EditorActionModeWebView(context)', factory)
        self.assertNotIn('restoreAfterCut', (WEB / 'EditorKeyboardController.kt').read_text())
        keyboard = (WEB / 'EditorKeyboardController.kt').read_text()
        self.assertIn('webView.keyboardRevision == revision', keyboard)
        self.assertIn('hasEditingFocus', keyboard)
        self.assertNotIn('postDelayed', keyboard)
        self.assertNotIn('restartInput', keyboard.split('fun keepAfterCut', 1)[1])
        self.assertIn('KEYCODE_BACK', native)
        self.assertIn('cancelKeyboardRequest()', native)

    def test_cut_retains_transient_window_focus_but_cancels_activity_exit(self):
        native = (WEB / 'EditorActionModeWebView.java').read_text()
        host = (WEB / 'EditorLifecycleEffects.kt').read_text()
        keyboard = (WEB / 'EditorKeyboardController.kt').read_text()
        handler = native.split('void onWindowFocusChanged', 1)[1].split('@Override', 1)[0]
        self.assertNotIn('cancelKeyboardRequest', handler)
        self.assertIn('if (hasWindowFocus) updateImeConfiguration', handler)
        self.assertIn('keyboardVisible, canKeepCutIme(), hasWindowFocus()', native)
        self.assertIn('!canKeepCutIme() || !hasWindowFocus()', native)
        self.assertIn('Lifecycle.Event.ON_PAUSE', host)
        self.assertIn('cancelKeyboardRequest("activity-pause")', host)
        self.assertIn('cancelKeyboardRequest("dispose")', host)
        self.assertNotIn('hasWindowFocus()', keyboard.split('fun keepAfterCut', 1)[1].split('fun toggle', 1)[0])

    def test_one_live_editor_is_moved_and_retained_for_preview(self):
        route = (ROOT / 'app/src/main/java/dev/pocket/notebook/feature/workspace/NotebookDetailScreen.kt').read_text()
        view = (WEB / 'NotebookEditorWebView.kt').read_text()
        self.assertEqual(route.count('NotebookEditorWebView('), 1)
        self.assertIn('movableContentOf<Modifier, Boolean>', route)
        self.assertIn('if (editorActive || editorOpened)', route)
        self.assertIn('Modifier.requiredSize(0.dp)', route)
        self.assertRegex(route, r'if \(previewActive \|\| previewOpened\)\s+movablePreview')
        self.assertIn('View.INVISIBLE', (WEB / 'WorkspaceWebViewVisibility.kt').read_text())
        self.assertIn('suspendKeyboardFocus', (WEB / 'EditorLifecycleEffects.kt').read_text())

    def test_modular_host_still_uses_the_upstream_engine(self):
        editor = ROOT / 'web/src/editor'
        self.assertIn('createMainEditorExtensions', (editor / 'configuration.js').read_text())
        self.assertIn('initQuickTools()', (editor / 'toolbar.js').read_text())
        self.assertIn('createSinglePaneRuntime', (editor / 'application.js').read_text())
        for name in ['TouchSelection.js', 'SelectionRecovery.js', 'AcodeScrollbar.js', 'EditorSearchBar.js']:
            self.assertFalse(list(editor.rglob(name)), name)


if __name__ == '__main__':
    unittest.main()
