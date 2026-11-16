package dev.pocket.notebook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.pocket.notebook.settings.AppTheme

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3657B5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E8FF), onPrimaryContainer = Color(0xFF112A70),
    secondary = Color(0xFF49656A), secondaryContainer = Color(0xFFD3EFF2),
    background = Color(0xFFF7F8FC), surface = Color(0xFFF7F8FC),
    surfaceVariant = Color(0xFFE4E8F2), outlineVariant = Color(0xFFDDE2EA)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB3C6FF), onPrimary = Color(0xFF18367F),
    primaryContainer = Color(0xFF294693), onPrimaryContainer = Color(0xFFE0E8FF),
    secondary = Color(0xFFB1D0D4), secondaryContainer = Color(0xFF344F54),
    background = Color(0xFF10131A), surface = Color(0xFF10131A),
    surfaceVariant = Color(0xFF303641), outlineVariant = Color(0xFF444C5A)
)

@Composable
fun isDarkTheme(choice: AppTheme): Boolean = when (choice) {
    AppTheme.DARK -> true
    AppTheme.LIGHT -> false
    AppTheme.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun NotebookTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
