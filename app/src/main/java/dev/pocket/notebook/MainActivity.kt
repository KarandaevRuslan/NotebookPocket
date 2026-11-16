package dev.pocket.notebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import dev.pocket.notebook.app.RepositoryBootstrap
import dev.pocket.notebook.i18n.resolveLanguage
import dev.pocket.notebook.settings.AppPreferences
import dev.pocket.notebook.app.NotebookPocketApp
import dev.pocket.notebook.ui.theme.isDarkTheme
import dev.pocket.notebook.web.EditorDiagnosticsControl

class MainActivity : ComponentActivity() {
    override fun onDestroy() {
        EditorDiagnosticsControl.release(this)
        super.onDestroy()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EditorDiagnosticsControl.install(this)
        // With adjustResize and Compose imePadding, the WebView receives its actual
        // available height when the soft keyboard opens, including on Android 15.
        enableEdgeToEdge()
        setContent {
            val preferences = remember { AppPreferences(applicationContext) }
            val dark = isDarkTheme(preferences.theme)
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            RepositoryBootstrap(language = resolveLanguage(preferences.language)) { repository -> NotebookPocketApp(repository, preferences) }
        }
    }
}
