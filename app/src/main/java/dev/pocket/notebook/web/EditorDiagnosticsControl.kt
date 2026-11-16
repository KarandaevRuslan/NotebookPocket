package dev.pocket.notebook.web

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.app.Activity
import android.os.Build
import android.webkit.WebView
import androidx.core.content.ContextCompat
import dev.pocket.notebook.core.logging.NotebookLog
import java.util.WeakHashMap
import java.lang.ref.WeakReference
import android.view.Window
import org.json.JSONArray
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Debug-only adb switch. No receiver or trace observers in release builds. */
object EditorDiagnosticsControl {
    const val ACTION = "dev.pocket.notebook.EDITOR_DIAGNOSTICS"
    @Volatile var enabled = false
        private set
    private var installed = false
    private val views = WeakHashMap<EditorActionModeWebView, Boolean>()
    private var hostWindow = WeakReference<Window>(null)
    @Volatile private var selectionMode = "correct"
    @Volatile private var revealMode = "caret"
    @Volatile private var traceGeneration = 0
    private val dropped = AtomicInteger()
    private val traceWorker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(16), { work -> Thread(work, "EditorDiagnostics").apply { isDaemon = true } },
        { work, executor ->
            dropped.incrementAndGet()
            executor.queue.poll()
            if (!executor.isShutdown) executor.execute(work)
        })
    private val layoutTimes = mutableMapOf<String, Long>()

    fun optionsJson(): String = "{\"selectionMode\":\"$selectionMode\",\"revealMode\":\"$revealMode\"}"
    fun traceBatch(json: String) {
        if (!enabled || json.length > 65536) return
        val epoch = traceGeneration
        traceWorker.execute {
            if (!enabled || epoch != traceGeneration) return@execute
            val lost = dropped.getAndSet(0)
            if (lost > 0) NotebookLog.trace(NotebookLog.Area.DIAGNOSTICS, "diagnostics-native-dropped", "count=$lost")
            runCatching {
                val events = JSONArray(json)
                for (index in 0 until events.length().coerceAtMost(32)) {
                    if (!enabled || epoch != traceGeneration) break
                    events.optJSONObject(index)?.let { NotebookLog.webTrace(it.toString()) }
                }
            }
        }
    }

    fun traceLayout(event: String, width: Int, height: Int) {
        if (!enabled) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - (layoutTimes[event] ?: -1000L) < 150) return
        layoutTimes[event] = now
        NotebookLog.trace(NotebookLog.Area.VIEWPORT, event,
            "clock=${System.currentTimeMillis()} widthPx=$width heightPx=$height")
    }
    private var lastNav: Boolean? = null
    private var lastImeTrace = 0L
    fun traceScaffold(imeInsetPx: Int, navVisible: Boolean, fullscreen: Boolean) {
        if (!enabled) return
        val now = android.os.SystemClock.uptimeMillis()
        if (lastNav == navVisible && now - lastImeTrace < 150) return
        lastNav = navVisible; lastImeTrace = now
        NotebookLog.trace(NotebookLog.Area.IME, "ime-compose-state",
            "clock=${System.currentTimeMillis()} imeInsetPx=$imeInsetPx navVisible=$navVisible fullscreen=$fullscreen")
    }

    fun install(context: Context) {
        (context as? Activity)?.window?.let {
            hostWindow = WeakReference(it)
            if (enabled) EditorFrameDiagnostics.start(it)
        }
        val app = context.applicationContext
        if (installed || (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        installed = true
        runCatching { ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != ACTION) return
                traceGeneration++
                enabled = intent.getBooleanExtra("enabled", true)
                selectionMode = if (enabled && intent.getStringExtra("selectionMode") == "observe") "observe" else "correct"
                revealMode = if (enabled && intent.getStringExtra("revealMode") == "legacy") "legacy" else "caret"
                NotebookLog.trace(NotebookLog.Area.BOOT, "diagnostics-control", "enabled=$enabled views=${views.size} selectionMode=$selectionMode revealMode=$revealMode")
                if (enabled) hostWindow.get()?.let(EditorFrameDiagnostics::start)
                else { EditorFrameDiagnostics.stop(); traceWorker.queue.clear(); layoutTimes.clear(); lastNav = null }
                if (enabled) {
                    val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                    val webview = runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull()
                    val metrics=app.resources.displayMetrics
                    NotebookLog.trace(NotebookLog.Area.BOOT, "diagnostics-device", "app=$version sdk=${Build.VERSION.SDK_INT} webview=$webview model=${Build.MODEL.take(60)} density=${metrics.density} fontScale=${app.resources.configuration.fontScale}")
                }
                views.keys.toList().forEach { apply(it) }
            }
        }, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED) }.onFailure {
            installed=false
            NotebookLog.e("EditorDiagnostics", "Diagnostic receiver unavailable: ${it.javaClass.simpleName}")
        }
    }

    private fun apply(view: EditorActionModeWebView) {
        runCatching {
            view.setDiagnosticLoggingEnabled(enabled)
            view.evaluateJavascript("window.EditorApi?.setDiagnosticsEnabled($enabled);window.EditorApi?.setDiagnosticsOptions(${optionsJson()})", null)
        }.onFailure { NotebookLog.w("EditorDiagnostics", "Diagnostic view unavailable: ${it.javaClass.simpleName}") }
    }
    fun attach(view: EditorActionModeWebView) { views[view] = true; apply(view) }
    fun detach(view: EditorActionModeWebView) { views.remove(view); view.setDiagnosticLoggingEnabled(false) }
    fun release(context: Activity) {
        if (hostWindow.get() === context.window) {
            EditorFrameDiagnostics.stop()
            hostWindow = WeakReference<Window>(null)
        }
    }
}
