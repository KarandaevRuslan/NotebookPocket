package dev.pocket.notebook.app.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.pocket.notebook.app.navigation.TopLevelDestination

/** Adapted from MobileApp/AppNavigationBar.kt; Material3 handles bottom bar system insets. */
@Composable
fun AppNavigationBar(
    currentRoute: String?,
    destinations: List<TopLevelDestination>,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { if (currentRoute != destination.route) onNavigate(destination.route) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label, maxLines = 1) },
            )
        }
    }
}
