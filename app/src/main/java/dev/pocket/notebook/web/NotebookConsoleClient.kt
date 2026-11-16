package dev.pocket.notebook.web

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import dev.pocket.notebook.core.logging.NotebookLog

/** Console diagnostics never replace content with a fatal overlay. Protected preview is silent. */
internal class NotebookConsoleClient(
    private val area: NotebookLog.Area,
    private val enabled: () -> Boolean = { true },
) : WebChromeClient() {
    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
        if (enabled()) {
            val priority =
                when (msg.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> android.util.Log.ERROR
                    ConsoleMessage.MessageLevel.WARNING -> android.util.Log.WARN
                    else -> android.util.Log.DEBUG
                }
            NotebookLog.webConsole(area, priority, msg.message(), msg.sourceId(), msg.lineNumber())
        }
        return true
    }
}
