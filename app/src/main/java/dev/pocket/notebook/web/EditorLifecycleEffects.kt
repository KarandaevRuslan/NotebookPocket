package dev.pocket.notebook.web

import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Native Back dispatch, diagnostics subscriptions and IME ownership share the live view lifetime.
 */
@Composable
internal fun EditorLifecycleEffects(web: WebView?, ready: Boolean, active: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(web) {
        val view = web as? EditorActionModeWebView
        if (view != null) EditorDiagnosticsControl.attach(view)
        onDispose { if (view != null) EditorDiagnosticsControl.detach(view) }
    }

    // A clipboard popup can briefly take window focus while this activity
    // remains resumed. Actual navigation/backgrounding must cancel Cut IME.
    DisposableEffect(web, lifecycleOwner) {
        val view = web as? EditorActionModeWebView
        val observer = LifecycleEventObserver { _, event ->
            // Log the lifecycle owner separately from window/view focus. It
            // may be a navigation entry; window focus alone cannot tell us
            // whether this cancellation was triggered in the failed Cut.
            if (view != null && event == Lifecycle.Event.ON_PAUSE) {
                view.logDiagnostic("ime-lifecycle-pause", view.keyboardRevision)
                view.cancelKeyboardRequest("activity-pause")
            } else if (view != null && event == Lifecycle.Event.ON_RESUME) {
                view.logDiagnostic("ime-lifecycle-resume", view.keyboardRevision)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view?.cancelKeyboardRequest("dispose")
        }
    }

    // Acode owns the stack of Find, palette and settings overlays. Let it
    // consume Back first, then delegate to the existing app/fullscreen handler.
    DisposableEffect(backDispatcher, web, ready, active) {
        val dispatcher = backDispatcher
        val view = web
        if (dispatcher == null || view == null || !ready || !active) {
            onDispose {}
        } else {
            var disposed = false
            val callback =
                object : OnBackPressedCallback(true) {
                    private var pending = false

                    override fun handleOnBackPressed() {
                        (view as? EditorActionModeWebView)?.cancelKeyboardRequest("back-dispatch")
                        if (pending) return
                        pending = true
                        view.evaluateJavascript("window.EditorApi?.handleBack?.() === true") {
                            handled ->
                            pending = false
                            if (disposed || !view.isAttachedToWindow) return@evaluateJavascript
                            if (handled != "true") {
                                isEnabled = false
                                try {
                                    dispatcher.onBackPressed()
                                } finally {
                                    if (view.isAttachedToWindow) isEnabled = true
                                }
                            }
                        }
                    }
                }
            dispatcher.addCallback(callback)
            onDispose {
                disposed = true
                callback.remove()
            }
        }
    }

    // A Code tab can become visible after CodeMirror was measured at 0x0 while
    // its WebView was parked under Preview/Resources. Re-measure on first entry
    // and every return: otherwise the custom Acode scrollbars may only flash
    // during boot and never show again while the user scrolls.
    LaunchedEffect(web, ready, active) {
        val view = web ?: return@LaunchedEffect
        if (ready && active) {
            view.postOnAnimation {
                view.postOnAnimation {
                    if (view.isAttachedToWindow && view.visibility == android.view.View.VISIBLE)
                        view.evaluateJavascript("window.EditorApi?.refresh?.()", null)
                }
            }
        }
    }

    // Preview hides the live editor without destroying CodeMirror state. Do
    // not restore focus on re-entry: a tap or the keyboard action owns that.
    LaunchedEffect(web, ready, active) {
        val view = web ?: return@LaunchedEffect
        if (ready && !active) {
            view.evaluateJavascript("window.EditorApi?.suspendKeyboardFocus?.()") {
                view.clearFocus()
            }
        }
    }
}
