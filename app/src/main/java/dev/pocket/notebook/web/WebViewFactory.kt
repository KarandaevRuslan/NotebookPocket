package dev.pocket.notebook.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import dev.pocket.notebook.core.logging.NotebookLog as Log
import java.io.ByteArrayInputStream

internal const val WEB_BASE = "https://appassets.androidplatform.net/assets/web/"
internal const val EDITOR_HOST = "appassets.androidplatform.net"
private const val WEB_ASSET_PREFIX = "/assets/web/"

private fun isCriticalWebResource(uri: Uri, host: String): Boolean =
    uri.host == host &&
        uri.path?.startsWith(WEB_ASSET_PREFIX) == true &&
        listOf(".html", ".js", ".css", ".wasm").any { uri.path!!.endsWith(it) }

/**
 * Offline asset loader with diagnostic traces, not a network proxy. No notebook source text, DOM
 * contents or document IDs are logged.
 */
internal class LocalAssetsClient(
    context: Context,
    private val owner: String = "WebView",
    private val originHost: String = EDITOR_HOST,
    private val pageFinished: (() -> Unit)? = null,
    private val onAssetError: (String) -> Unit = {},
    private val notebookResource: ((Uri) -> WebResourceResponse?)? = null,
) : WebViewClient() {
    private val main = Handler(Looper.getMainLooper())
    private val loader =
        WebViewAssetLoader.Builder()
            .setDomain(originHost)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val uri = request.url
        if (uri.scheme != "https" || uri.host != originHost) {
            Log.w(
                "NotebookAssets",
                "$owner blocked external request: ${uri.scheme}://${uri.host}${uri.path}",
            )
            return blocked()
        }
        if (uri.path?.startsWith("/notebook-resources/") == true)
            return notebookResource?.invoke(uri) ?: blocked()
        if (uri.path == "/favicon.ico") return emptyFavicon()

        val response = loader.shouldInterceptRequest(uri)
        val critical = request.isForMainFrame || isCriticalWebResource(uri, originHost)
        if (critical) {
            Log.d(
                "NotebookAssets",
                "$owner request ${request.method} $uri -> " +
                    if (response == null) "MISSING"
                    else "${response.statusCode} ${response.mimeType}",
            )
        }
        if (response == null && critical) {
            main.post { onAssetError("Bundled asset missing: $uri") }
        }
        return response ?: blocked()
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        Log.w("NotebookAssets", "$owner navigation prevented: ${request.url}")
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.i("NotebookAssets", "$owner page started: $url")
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        Log.i("NotebookAssets", "$owner page finished: $url")
        pageFinished?.invoke()
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        response: WebResourceResponse,
    ) {
        super.onReceivedHttpError(view, request, response)
        Log.e(
            "NotebookAssets",
            "$owner HTTP ${response.statusCode} ${response.reasonPhrase}: ${request.url}; main=${request.isForMainFrame}",
        )
        if (
            response.statusCode >= 400 &&
                request.url.host == originHost &&
                (request.isForMainFrame || isCriticalWebResource(request.url, originHost))
        ) {
            main.post { onAssetError("HTTP ${response.statusCode}: ${request.url}") }
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        super.onReceivedError(view, request, error)
        Log.e(
            "NotebookAssets",
            "$owner network/error ${error.errorCode}: ${error.description}; " +
                "main=${request.isForMainFrame}; url=${request.url}",
        )
        if (request.isForMainFrame || isCriticalWebResource(request.url, originHost)) {
            main.post {
                onAssetError(
                    "WebView error ${error.errorCode}: ${error.description} (${request.url})"
                )
            }
        }
    }

    private fun emptyFavicon() =
        WebResourceResponse("image/x-icon", null, ByteArrayInputStream(ByteArray(0)))

    private fun blocked() =
        WebResourceResponse(
            "text/plain",
            "UTF-8",
            403,
            "Blocked",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
}

internal fun setWebViewBackground(view: WebView, dark: Boolean) {
    view.setBackgroundColor(Color.parseColor(if (dark) "#10131a" else "#f7f8fc"))
}

@SuppressLint("SetJavaScriptEnabled")
internal fun createNotebookWebView(
    context: Context,
    client: WebViewClient,
    dark: Boolean,
    editor: Boolean = false,
): WebView =
    (if (editor) EditorActionModeWebView(context) else WebView(context)).apply {
        setWebViewBackground(this, dark)
        isFocusable = true
        isFocusableInTouchMode = true
        overScrollMode = View.OVER_SCROLL_NEVER
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = editor
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.defaultTextEncodingName = "UTF-8"
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webViewClient = client
    }

internal fun webPageUrl(
    page: String,
    dark: Boolean,
    language: String,
    host: String = EDITOR_HOST,
): String =
    "https://$host/assets/web/" +
        page +
        "?theme=" +
        (if (dark) "dark" else "light") +
        "&language=" +
        (if (language == "ru") "ru" else "en")

/**
 * One deterministic, non-editor origin per document; guessed origins still cannot cross SOP/CSP.
 */
internal fun previewHost(id: String): String =
    "preview-" +
        dev.pocket.notebook.data.security.NotebookCrypto.sha256(id.toByteArray()).take(32) +
        ".notebook.invalid"

internal fun applyPreviewStoragePolicy(view: WebView, host: String, encrypted: Boolean) {
    view.settings.domStorageEnabled = !encrypted
    if (encrypted) android.webkit.WebStorage.getInstance().deleteOrigin("https://$host")
}
