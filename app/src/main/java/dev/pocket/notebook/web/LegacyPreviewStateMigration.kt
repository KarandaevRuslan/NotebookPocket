package dev.pocket.notebook.web

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.pocket.notebook.data.security.NotebookCrypto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import org.json.JSONObject

/**
 * One-time compatibility bridge for 0.9.2 form state. Runs trusted, script-free HTML on the OLD
 * editor origin, reads only the exact active notebook key, then destroys that WebView. Imported
 * source never executes on this origin and no JavascriptInterface is attached. Protected notebooks
 * are intentionally excluded. No document IDs or values are written to preferences/logs.
 */
internal object LegacyPreviewStateMigration {
    private const val PREFS = "notebook-preview-origin-migration-v1"
    private const val MAX_STATE_CHARS = 65_536
    private val stateVersion =
        Regex("<notebook\\b[^>]*\\bdata-state-version=[\\\"']([a-z0-9-]{1,32})[\\\"']", RegexOption.IGNORE_CASE)

    data class Pending(val key: String, val value: String?, val timers: String?)

    fun stateKey(id: String, source: String): String {
        val version = stateVersion.find(source)?.groupValues?.get(1)
        return "notebook-pocket:state:$id" + if (version == null) "" else ":$version"
    }

    private fun preferenceKey(key: String) = NotebookCrypto.sha256(key.toByteArray())

    suspend fun pending(context: Context, id: String, source: String, encrypted: Boolean): Pending? {
        if (encrypted) return null
        val key = stateKey(id, source)
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(preferenceKey(key), false)) return null
        // Migration is best-effort and must never stall a notebook's normal preview.
        // On timeout do not mark the key migrated; another visit may still recover it.
        val values = withTimeoutOrNull(3500) { readLegacyValues(context, key) } ?: return null
        return Pending(key, values.first, values.second)
    }

    fun markComplete(context: Context, pending: Pending) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(preferenceKey(pending.key), true).apply()
    }

    private suspend fun readLegacyValues(context: Context, key: String): Pair<String?, String?> =
        withContext(Dispatchers.Main) {
            val loaded = CompletableDeferred<Unit>()
            val web = createNotebookWebView(
                context,
                object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.complete(Unit)
                    }
                },
                dark = false,
                editor = false, // Plain, bridge-free WebView; only trusted HTML is loaded.
            ).apply { settings.domStorageEnabled = true }
            try {
                // loadDataWithBaseURL assigns this trusted page the legacy HTTPS origin.
                web.loadDataWithBaseURL(
                    "https://$EDITOR_HOST/assets/web/state-migration.html",
                    "<!doctype html><html><head><meta charset='utf-8'></head><body></body></html>",
                    "text/html", "UTF-8", null,
                )
                withTimeout(5000) { loaded.await() }
                val script =
                    "(function(){try{var f=localStorage.getItem(${JSONObject.quote(key)});" +
                        "var t=localStorage.getItem(${JSONObject.quote("notebook-pocket:timers:$key")});" +
                        "return JSON.stringify({form:typeof f==='string'&&f.length<=$MAX_STATE_CHARS?f:null," +
                        "timers:typeof t==='string'&&t.length<=$MAX_STATE_CHARS?t:null})" +
                        "}catch(e){return JSON.stringify({})}})()"
                val response = withTimeout(5000) { web.evaluateJs(script) }
                // evaluateJavascript returns an encoded JSON string; the inner JSON is bounded.
                val inner = JSONTokener(response).nextValue() as? String ?: "{}"
                val values = JSONObject(inner)
                (values.opt("form") as? String) to (values.opt("timers") as? String)
            } finally {
                web.stopLoading()
                web.destroy()
            }
        }
}
