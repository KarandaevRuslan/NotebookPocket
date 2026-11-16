package dev.pocket.notebook.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.pocket.notebook.i18n.AppStrings

/** Same destination model as in the supplied MobileApp: one source for bar and rail. */
data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    // Keep the full label in bottom navigation and accessibility semantics.
    // A rail is only 80 dp wide: use a purpose-made short visible caption.
    val railLabel: String = label,
)

fun topLevelDestinations(strings: AppStrings): List<TopLevelDestination> =
    listOf(
        TopLevelDestination(
            Routes.LIBRARY,
            strings.library,
            Icons.Default.FolderOpen,
            strings.railLibrary,
        ),
        TopLevelDestination(Routes.WORKSPACE, strings.workspace, Icons.Default.Description),
        TopLevelDestination(Routes.SETTINGS, strings.settings, Icons.Default.Settings),
    )
