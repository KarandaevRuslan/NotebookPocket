package dev.pocket.notebook.app.scaffold

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.app.components.AppNavigationRail
import dev.pocket.notebook.app.navigation.TopLevelDestination
import dev.pocket.notebook.web.EditorDiagnosticsControl

/**
 * Landscape inset policy follows the supplied MobileApp/MapNotesTwoPaneScaffold.
 *
 * The Android *system* navigation bar can be on either physical side, depending on rotation and
 * navigation mode. navigationBars.Horizontal provides exactly that inset; safeDrawing.Horizontal
 * would also inset the opposite edge for a camera cutout, wasting valuable width in landscape.
 *
 * Apply the side inset to the complete row (rail + content), and handle only the top status bar /
 * bottom system navigation bar inside each pane.
 */
@Composable
internal fun TwoPaneScaffold(
    route: String?,
    destinations: List<TopLevelDestination>,
    navigate: (String) -> Unit,
    fullscreen: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    Row(
        Modifier.fillMaxSize()
            .onSizeChanged {
                EditorDiagnosticsControl.traceLayout(
                    "viewport-compose-wide-window",
                    it.width,
                    it.height,
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
    ) {
        if (!fullscreen)
            AppNavigationRail(
                currentRoute = route,
                destinations = destinations,
                onNavigate = navigate,
                modifier =
                    Modifier.windowInsetsPadding(
                            WindowInsets.statusBars.only(WindowInsetsSides.Top)
                        )
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        ),
            )
        Scaffold(
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        ) { inner ->
            content(
                Modifier.fillMaxSize()
                    .padding(inner)
                    .consumeWindowInsets(inner)
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .imePadding()
                    .onSizeChanged {
                        EditorDiagnosticsControl.traceLayout(
                            "viewport-compose-wide-content",
                            it.width,
                            it.height,
                        )
                    }
            )
        }
    }
}
