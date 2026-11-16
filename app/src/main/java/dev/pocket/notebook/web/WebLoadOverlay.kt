package dev.pocket.notebook.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import dev.pocket.notebook.R
import dev.pocket.notebook.i18n.localizedString

/** Shared, opaque loading/error overlay: neither WebView shows a white first frame. */
@Composable
internal fun WebLoadOverlay(
    ready: Boolean,
    error: String?,
    language: String,
    onRetry: () -> Unit
) {
    if (ready) return
    Column(
        // Opaque overlay conceals the WebView before the first frame is ready.
        // Never set WebView.alpha = 0: some devices defer WebView compositing
        // until visible, leaving a blank editor even after the API is ready.
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        if (error == null) {
            CircularProgressIndicator()
        } else {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text(localizedString(LocalContext.current, language, R.string.retry))
            }
        }
    }
}
