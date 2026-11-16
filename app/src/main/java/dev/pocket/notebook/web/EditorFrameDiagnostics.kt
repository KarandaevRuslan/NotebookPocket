package dev.pocket.notebook.web

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import dev.pocket.notebook.core.logging.NotebookLog

/** Debug-only metrics for the Activity window, not a claim of WebView GPU time.
 * Aggregate off the UI thread; retain no per-frame histories or UI contents.
 */
internal object EditorFrameDiagnostics {
    private var window: Window? = null
    private var worker: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    @Volatile private var generation = 0

    fun start(target: Window) {
        if (window === target && listener != null) return
        stop()
        val epoch = ++generation
        val thread = HandlerThread("EditorFrameMetrics").apply { start() }
        val rate = target.decorView.display?.refreshRate?.toDouble()?.takeIf { it > 0 } ?: 60.0
        val budgetMs = 1000.0 / rate
        var frames = 0; var slow = 0; var lost = 0
        var totalNs = 0L; var maxTotalNs = 0L; var maxLayoutNs = -1L; var maxDrawNs = -1L
        var since = SystemClock.uptimeMillis()
        val callback = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            if (!EditorDiagnosticsControl.enabled || epoch != generation) return@OnFrameMetricsAvailableListener
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (total < 0) return@OnFrameMetricsAvailableListener
            frames++; lost += dropped; totalNs += total
            maxTotalNs = maxOf(maxTotalNs, total)
            maxLayoutNs = maxOf(maxLayoutNs, metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
            maxDrawNs = maxOf(maxDrawNs, metrics.getMetric(FrameMetrics.DRAW_DURATION))
            if (total / 1_000_000.0 > budgetMs) slow++
            val now = SystemClock.uptimeMillis()
            if (now - since >= 1000 && frames > 0) {
                NotebookLog.trace(NotebookLog.Area.DIAGNOSTICS, "diagnostics-native-frames",
                    "clock=${System.currentTimeMillis()} frames=$frames slowFrames=$slow dropped=$lost budgetMs=$budgetMs " +
                    "avgTotalMs=${totalNs / 1_000_000.0 / frames} maxTotalMs=${maxTotalNs / 1_000_000.0} " +
                    "maxLayoutMs=${if (maxLayoutNs < 0) -1.0 else maxLayoutNs / 1_000_000.0} maxDrawMs=${if (maxDrawNs < 0) -1.0 else maxDrawNs / 1_000_000.0}")
                frames=0;slow=0;lost=0;totalNs=0;maxTotalNs=0;maxLayoutNs=-1;maxDrawNs=-1;since=now
            }
        }
        runCatching { target.addOnFrameMetricsAvailableListener(callback, Handler(thread.looper)) }
            .onSuccess { window=target;worker=thread;listener=callback }
            .onFailure { thread.quitSafely();NotebookLog.w("EditorDiagnostics", "Frame metrics unavailable: ${it.javaClass.simpleName}") }
    }

    fun stop() {
        generation++
        val old = listener
        if (old != null) runCatching { window?.removeOnFrameMetricsAvailableListener(old) }
        window=null;listener=null;worker?.quitSafely();worker=null
    }
}
