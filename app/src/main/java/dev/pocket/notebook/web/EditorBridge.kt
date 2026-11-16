package dev.pocket.notebook.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import dev.pocket.notebook.core.logging.NotebookLog as Log
import dev.pocket.notebook.core.logging.NotebookLog
import org.json.JSONTokener

/** The only JS-to-Kotlin bridge; it is installed exclusively on the trusted editor. */
internal class EditorBridge(
    context: Context,
    private val onChange: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onCursorSelection: (Int, Int, Int) -> Unit,
    private val onSearchStateChange: (String) -> Unit,
    private val onFullscreenChange: (Boolean) -> Unit,
    private val onKeyboardToggle: () -> Unit,
    private val onKeepKeyboardAfterCut: (Int) -> Unit,
    private val onBeginClipboardCut: (Int) -> Unit,
    private val onCancelClipboardCut: () -> Unit,
    private val onNativeContextMenuDisabled: (Boolean) -> Unit,
    private val onSystemConfiguration: () -> String,
    private val onError: (String) -> Unit,
    private val onSave: () -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private val clipboard =
        context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        handler.post {
            if (EditorDiagnosticsControl.enabled)
                NotebookLog.trace(
                    NotebookLog.Area.IME,
                    "clipboard-write-begin",
                    "clock=${System.currentTimeMillis()}",
                )
            clipboard.setPrimaryClip(ClipData.newPlainText("Notebook Pocket", text))
            if (EditorDiagnosticsControl.enabled)
                NotebookLog.trace(
                    NotebookLog.Area.IME,
                    "clipboard-write-end",
                    "clock=${System.currentTimeMillis()}",
                )
        }
    }

    @JavascriptInterface
    fun readClipboard(): String {
        return runCatching {
                clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            }
            .getOrDefault("")
    }

    @JavascriptInterface
    fun toggleKeyboard() {
        handler.post { onKeyboardToggle() }
    }

    @JavascriptInterface fun diagnosticsEnabled(): Boolean = EditorDiagnosticsControl.enabled

    @JavascriptInterface fun diagnosticOptions(): String = EditorDiagnosticsControl.optionsJson()

    @JavascriptInterface
    fun trace(json: String) {
        if (EditorDiagnosticsControl.enabled) NotebookLog.webTrace(json)
    }

    @JavascriptInterface
    fun traceBatch(json: String) {
        EditorDiagnosticsControl.traceBatch(json)
    }

    @JavascriptInterface
    fun keepKeyboardAfterCut(revision: Int) {
        handler.post { onKeepKeyboardAfterCut(revision) }
    }

    @JavascriptInterface
    fun beginClipboardCut(revision: Int) {
        handler.post { onBeginClipboardCut(revision) }
    }

    @JavascriptInterface
    fun cancelClipboardCut() {
        handler.post { onCancelClipboardCut() }
    }

    @JavascriptInterface
    fun setNativeContextMenuDisabled(disabled: Boolean) {
        handler.post { onNativeContextMenuDisabled(disabled) }
    }

    @JavascriptInterface fun systemConfiguration(): String = onSystemConfiguration()

    @JavascriptInterface
    fun setFullscreen(enabled: Boolean) {
        handler.post { onFullscreenChange(enabled) }
    }

    @JavascriptInterface
    fun save() {
        handler.post { onSave() }
    }

    @JavascriptInterface
    fun changed(source: String) {
        handler.post { onChange(source) }
    }

    @JavascriptInterface
    fun cursorSelection(anchor: Int, head: Int, scrollTop: Int) {
        handler.post { onCursorSelection(anchor, head, scrollTop) }
    }

    /** Search terms are forwarded to the in-memory WorkspaceViewModel only. */
    @JavascriptInterface
    fun searchState(json: String) {
        if (json.length <= 8192) handler.post { onSearchStateChange(json) }
    }

    @JavascriptInterface
    fun ready() {
        Log.i("NotebookEditor", "JS bridge: ready()")
        handler.post { onReady() }
    }

    @JavascriptInterface
    fun failed(message: String) {
        Log.e("NotebookEditor", "JS bridge: failed(): $message")
        handler.post { onError(message) }
    }
}

/** evaluateJavascript gives a JSON-encoded JS string, not its raw body. */
fun readEditorSource(webView: WebView?, fallback: String, callback: (String) -> Unit) {
    if (webView == null) {
        callback(fallback)
        return
    }
    webView.evaluateJavascript("window.EditorApi?.getSource()") { raw ->
        val source = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: fallback
        callback(source)
    }
}
