package dev.pocket.notebook.web

import android.webkit.WebView
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Await a single JS handshake instead of repeatedly rendering while callbacks race. */
internal suspend fun WebView.evaluateJs(script: String): String = suspendCancellableCoroutine { cont ->
    evaluateJavascript(script) { value ->
        if (cont.isActive) cont.resume(value)
    }
}
