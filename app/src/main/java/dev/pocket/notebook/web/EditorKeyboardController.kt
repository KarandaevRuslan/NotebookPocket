package dev.pocket.notebook.web

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Explicit app toolbar action. Acode owns focus and clipboard edit behavior. */
internal object EditorKeyboardController {
    /** A single Cut completion, after Chromium has applied the DOM edit.
     * A prior visible IME and live editing focus are required. Back, hiding
     * the editor or pausing the activity cancels the captured revision.
     * A transient clipboard window suspends showing until focus returns.
     * No retries, restartInput, focus cycling or delayed recovery timers.
     */
    fun keepAfterCut(webView: EditorActionModeWebView, revision: Int) {
        webView.logDiagnostic("cut-focus-request", revision)
        fun canKeep() = webView.isAttachedToWindow &&
            webView.hasFocus() && webView.visibility == android.view.View.VISIBLE &&
            webView.keyboardRevision == revision
        if (!canKeep()) { webView.logDiagnostic("cut-focus-skipped", revision); return }
        webView.evaluateJavascript("window.EditorApi?.hasEditingFocus?.() === true") { focused ->
            webView.logDiagnostic(if (focused == "true") "cut-dom-focus-yes" else "cut-dom-focus-no", revision)
            if (focused != "true" || !canKeep()) { webView.logDiagnostic("cut-completion-skipped", revision); return@evaluateJavascript }
            // Completion before/after an IME hide is handled by the same
            // one-shot state machine; showing before a late hide was a race.
            webView.completeKeyboardAfterCut(revision)
        }
    }

    fun toggle(webView: WebView) {
        (webView as? EditorActionModeWebView)?.let { it.logDiagnostic("ime-toolbar-toggle", it.keyboardRevision) }
        val ime = WindowInsetsCompat.Type.ime()
        val manager = webView.context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        val visible = ViewCompat.getRootWindowInsets(webView)?.isVisible(ime) == true
        if (visible) {
            (webView as? EditorActionModeWebView)?.cancelKeyboardRequest("toolbar-hide")
            webView.evaluateJavascript("window.EditorApi?.suspendKeyboardFocus?.()") {
                if (!webView.isAttachedToWindow) return@evaluateJavascript
                webView.clearFocus()
                ViewCompat.getWindowInsetsController(webView)?.hide(ime)
                manager.hideSoftInputFromWindow(webView.windowToken, 0)
            }
        } else {
            webView.requestFocus()
            webView.evaluateJavascript("window.EditorApi?.focusForKeyboard?.()") {
                webView.post {
                    if (!webView.isAttachedToWindow) return@post
                    ViewCompat.getWindowInsetsController(webView)?.show(ime)
                    manager.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }
}
