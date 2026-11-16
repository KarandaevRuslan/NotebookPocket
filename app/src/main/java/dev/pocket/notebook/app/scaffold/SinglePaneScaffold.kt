package dev.pocket.notebook.app.scaffold

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.app.components.AppNavigationBar
import dev.pocket.notebook.app.navigation.TopLevelDestination
import dev.pocket.notebook.web.EditorDiagnosticsControl

/** Compact screen: material bottom nav owns its system-bar bottom; IME hides the nav. */
@Composable
internal fun SinglePaneScaffold(
    route: String?,
    destinations: List<TopLevelDestination>,
    navigate: (String) -> Unit,
    fullscreen: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    val imeInsetPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val keyboardOpen = imeInsetPx > 0
    SideEffect {
        EditorDiagnosticsControl.traceScaffold(imeInsetPx, !fullscreen && !keyboardOpen, fullscreen)
    }
    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .onSizeChanged {
                    EditorDiagnosticsControl.traceLayout(
                        "viewport-compose-window",
                        it.width,
                        it.height,
                    )
                }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            if (!fullscreen && !keyboardOpen) AppNavigationBar(route, destinations, navigate)
        },
    ) { inner ->
        content(
            Modifier.fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                // When the app nav bar is hidden (fullscreen or IME), Scaffold
                // no longer reserves the Android SYSTEM navigation bar inset.
                // Reserve it here; Compose consumes overlap with IME padding.
                .then(
                    if (fullscreen || keyboardOpen)
                        Modifier.windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        )
                    else Modifier
                )
                .imePadding()
                .onSizeChanged {
                    EditorDiagnosticsControl.traceLayout(
                        "viewport-compose-content",
                        it.width,
                        it.height,
                    )
                }
        )
    }
}
