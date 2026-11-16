package dev.pocket.notebook.web

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.pocket.notebook.R
import dev.pocket.notebook.core.logging.NotebookLog as Log
import dev.pocket.notebook.core.logging.NotebookLog
import dev.pocket.notebook.feature.files.LocalPocketTools
import dev.pocket.notebook.i18n.localizedString
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Preview never receives a JavascriptInterface: imported notebooks cannot call native APIs. */
@Composable
fun NotebookPreviewWebView(
    id: String,
    source: String,
    dark: Boolean,
    language: String,
    encrypted: Boolean = false,
    resetRevision: Int = 0,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val encryptedNow by androidx.compose.runtime.rememberUpdatedState(encrypted)
    val context = LocalContext.current
    var resumeRevision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        resumeRevision++
    }
    val tools = dev.pocket.notebook.feature.files.LocalPocketTools.current
    val originHost = remember(id) { previewHost(id) }
    val scope = remember(id) { java.util.UUID.randomUUID().toString() }
    val revision = tools.revision
    var attachmentNames by remember(id) { mutableStateOf<List<String>>(emptyList()) }
    var attachmentRevision by remember(id) { androidx.compose.runtime.mutableIntStateOf(-1) }
    LaunchedEffect(id, revision) {
        attachmentNames =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { tools.repo.attachmentNames(id) }.getOrDefault(emptyList())
            }
        attachmentRevision = revision
    }
    val base = "https://$originHost/notebook-resources/$scope/"
    var web by remember(id) { mutableStateOf<WebView?>(null) }
    var pageFinished by remember(id) { mutableStateOf(false) }
    var ready by remember(id) { mutableStateOf(false) }
    var visible by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var reload by remember(id) { mutableStateOf(0) }
    var legacyAttempted by remember(id) { mutableStateOf(false) }

    Box(modifier.clipToBounds().background(if (dark) Color(0xFF10131A) else Color(0xFFF7F8FC))) {
        AndroidView(
            factory = { context ->
                createNotebookWebView(
                        context,
                        LocalAssetsClient(
                            context,
                            owner = "Observable",
                            originHost = originHost,
                            pageFinished = { pageFinished = true },
                            notebookResource =
                                NotebookResourceLoader(tools.repo, id, scope, originHost)::read,
                            onAssetError = {
                                Log.e("NotebookPreview", "asset load failed: $it")
                                error = it
                                ready = false
                                visible = false
                            },
                        ),
                        dark,
                    )
                    .apply {
                        applyPreviewStoragePolicy(this, originHost, encrypted)
                        webChromeClient =
                            NotebookConsoleClient(NotebookLog.Area.PREVIEW) { !encryptedNow }
                        web = this
                        Log.i("NotebookPreview", "loading preview entry point")
                        loadUrl(webPageUrl("preview.html", dark, language, originHost))
                    }
            },
            update = { view ->
                applyPreviewStoragePolicy(view, originHost, encrypted)
                updateWorkspaceWebViewVisibility(view, active)
            },
            onRelease = { view ->
                if (web === view) web = null
                pageFinished = false
                ready = false
                visible = false
                view.stopLoading()
                view.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (active)
            WebLoadOverlay(visible && error == null, error, language) {
                error = null
                ready = false
                visible = false
                pageFinished = false
                reload += 1
                legacyAttempted = false
                Log.i("NotebookPreview", "manual retry")
                web?.apply { loadUrl(webPageUrl("preview.html", dark, language, originHost)) }
            }
    }

    LaunchedEffect(
        web,
        pageFinished,
        source,
        dark,
        language,
        id,
        reload,
        encrypted,
        attachmentNames,
        attachmentRevision,
        resumeRevision,
    ) {
        val view = web ?: return@LaunchedEffect
        if (!pageFinished || attachmentRevision < 0 || error != null) return@LaunchedEffect
        Log.i("NotebookPreview", "page finished; waiting for PreviewApi.render (10s timeout)")
        // Keep the render reactive but debounce edit keystrokes in split mode.
        if (ready) delay(300)
        val environment =
            JSONObject()
                .put("dark", dark)
                .put("language", language)
                .put("locale", java.util.Locale.forLanguageTag(language).toLanguageTag())
                .put("timeZone", java.util.TimeZone.getDefault().id)
                .put(
                    "hourCycle",
                    if (android.text.format.DateFormat.is24HourFormat(context)) "h23" else "h12",
                )
                .put("epochMillis", System.currentTimeMillis())
                .put("elapsedRealtimeMillis", android.os.SystemClock.elapsedRealtime())
                .put(
                    "bootCount",
                    android.provider.Settings.Global.getInt(
                        context.contentResolver,
                        android.provider.Settings.Global.BOOT_COUNT,
                        -1,
                    ),
                )
        // The previous release persisted form state under the shared appassets origin.
        // Read only this document's key using a short-lived trusted, bridge-free WebView;
        // never expose the old origin (or other documents' values) to notebook JavaScript.
        val legacy =
            if (ready || legacyAttempted) null else {
                legacyAttempted = true
                try {
                    LegacyPreviewStateMigration.pending(context, id, source, encrypted)
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    Log.w("NotebookPreview", "legacy form state unavailable")
                    null
                }
            }
        val payload = "{id:${JSONObject.quote(id)},source:${JSONObject.quote(source)}}"
        val migrate = legacy?.let { old ->
            if (old.value == null && old.timers == null) ""
            else {
                val form = old.value?.let { JSONObject.quote(it) } ?: "null"
                val timers = old.timers?.let { JSONObject.quote(it) } ?: "null"
                "window.PreviewApi.migrateLegacyState(p.id, p.source, $form, $timers);"
            }
        } ?: ""
        val js =
            """
            (function () {
              if (!window.PreviewApi?.render) return false;
              window.PreviewApi.setPreferences($environment);
              var p = $payload;
              $migrate
              window.PreviewApi.render(p.source, p.id, $encrypted, {names:${org.json.JSONArray(attachmentNames)},base:${JSONObject.quote(base)}});
              return true;
            })()
        """
                .trimIndent()
        repeat(100) {
            if (error != null) return@LaunchedEffect
            val result = view.evaluateJs(js)
            if (result == "true") {
                Log.i("NotebookPreview", "PreviewApi handshake OK; render requested")
                ready = true
                legacy?.let { LegacyPreviewStateMigration.markComplete(context, it) }
                setWebViewBackground(view, dark)
                view.post { view.invalidate() }
                visible = true
                return@LaunchedEffect
            }
            delay(100)
        }
        if (!ready && error == null) {
            Log.e("NotebookPreview", "timeout: window.PreviewApi not created after 10s")
            // Give diagnostics even if the HTML loaded with status 200.
            val diag =
                view.evaluateJs(
                    "JSON.stringify({ready:!!window.PreviewApi,stage:window.__npBootStage||'none',error:window.__npBootError||null})"
                )
            Log.e("NotebookPreview", "JS diagnostic status: $diag")
            error = localizedString(context, language, R.string.preview_failed_to_start)
        }
    }

    LaunchedEffect(web, ready, active, resumeRevision) {
        val view = web ?: return@LaunchedEffect
        if (ready && active) {
            val sample =
                JSONObject()
                    .put("epochMillis", System.currentTimeMillis())
                    .put("elapsedRealtimeMillis", android.os.SystemClock.elapsedRealtime())
                    .put(
                        "bootCount",
                        android.provider.Settings.Global.getInt(
                            context.contentResolver,
                            android.provider.Settings.Global.BOOT_COUNT,
                            -1,
                        ),
                    )
            view.evaluateJs("window.PreviewApi?.syncClock?.($sample)")
        }
    }

    // Intentionally not a reload: PreviewApi clears this notebook's localStorage
    // and rebuilds the original Observable input graph in place.
    LaunchedEffect(web, ready, resetRevision) {
        if (resetRevision == 0 || !ready) return@LaunchedEffect
        val view = web ?: return@LaunchedEffect
        val result = view.evaluateJs("Boolean(window.PreviewApi?.resetState?.())")
        if (result == "true") {
            Log.i("NotebookPreview", "reset to defaults completed")
        } else {
            Log.e("NotebookPreview", "reset failed: PreviewApi.resetState result=$result")
            error = localizedString(context, language, R.string.reset_preview_failed)
        }
    }
}
