@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pocket.notebook.feature.files.viewer

import android.util.Base64
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*

@Composable
internal fun ResourceSvgPreview(bytes: ByteArray) {
    val source =
        remember(bytes) {
            """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; object-src 'none'; frame-src 'none'; base-uri 'none'"><style>html,body{margin:0;width:100%;height:100%;background:transparent}img{width:100%;height:100%;object-fit:contain}</style></head><body><img src="data:image/svg+xml;base64,${Base64.encodeToString(bytes,Base64.NO_WRAP)}"></body></html>"""
        }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/",
                    source,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        modifier = Modifier.fillMaxSize().clipToBounds().testTag("resource-svg-preview"),
        onRelease = {
            it.stopLoading()
            it.destroy()
        },
    )
}
