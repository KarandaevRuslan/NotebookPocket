package dev.pocket.notebook.web

import android.view.View
import android.webkit.WebView

/** Apply in AndroidView.update: a zero Compose size alone does not hide a WebView surface. */
internal fun updateWorkspaceWebViewVisibility(view: WebView, active: Boolean) {
    val target = if (active) View.VISIBLE else View.INVISIBLE
    val changed = view.visibility != target
    view.visibility = target
    view.isEnabled = active
    view.importantForAccessibility =
        if (active) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    if (changed) {
        if (active) view.onResume()
        else {
            view.clearFocus()
            view.onPause()
        }
    }
}
