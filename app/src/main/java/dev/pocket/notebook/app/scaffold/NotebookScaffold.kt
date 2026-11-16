package dev.pocket.notebook.app.scaffold

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import dev.pocket.notebook.app.adaptive.AdaptiveLayoutType
import dev.pocket.notebook.app.adaptive.rememberAdaptiveLayoutType
import dev.pocket.notebook.app.navigation.TopLevelDestination

/** Coordinator as in MobileApp/GeoInspectScaffold: no screen or navigation logic here. */
@Composable
fun NotebookScaffold(
    route: String?,
    destinations: List<TopLevelDestination>,
    navigate: (String) -> Unit,
    fullscreen: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    // The NavHost (including its Android WebView) must be MOVED, not destroyed
    // and recreated, when the navigation layout switches between bottom bar
    // and rail. Otherwise Android loses the live native selection ActionMode
    // and its drag handles during device rotation.
    val latestContent = rememberUpdatedState(content)
    val movableContent = remember {
        movableContentOf<Modifier> { childModifier -> latestContent.value(childModifier) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        when (rememberAdaptiveLayoutType(maxWidth, maxHeight)) {
            AdaptiveLayoutType.SinglePane ->
                SinglePaneScaffold(route, destinations, navigate, fullscreen, movableContent)
            AdaptiveLayoutType.TwoPane ->
                TwoPaneScaffold(route, destinations, navigate, fullscreen, movableContent)
        }
    }
}
