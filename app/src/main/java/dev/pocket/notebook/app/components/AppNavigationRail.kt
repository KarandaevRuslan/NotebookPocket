package dev.pocket.notebook.app.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.app.navigation.TopLevelDestination

/** Based on MobileApp/AppNavigationRail.kt: scrollable route list for short landscape windows. */
@Composable
fun AppNavigationRail(
    currentRoute: String?,
    destinations: List<TopLevelDestination>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            destinations.forEach { destination ->
                NavigationRailItem(
                    selected = currentRoute == destination.route,
                    onClick = {
                        if (currentRoute != destination.route) onNavigate(destination.route)
                    },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = {
                        Text(
                            text = destination.railLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
    }
}
