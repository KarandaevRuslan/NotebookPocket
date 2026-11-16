package dev.pocket.notebook.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Context
import androidx.compose.runtime.mutableStateOf

enum class AppLanguage { SYSTEM, RU, EN }
enum class AppTheme { SYSTEM, LIGHT, DARK }

/** Small persisted user preferences, observable by Compose without Activity recreation. */
class AppPreferences(context: Context) {
    val editor = EditorPreferences(context)
    private val store = context.getSharedPreferences("ui-preferences", Context.MODE_PRIVATE)

    var language by mutableStateOf(
        runCatching { AppLanguage.valueOf(store.getString("language", "SYSTEM")!!) }
            .getOrDefault(AppLanguage.SYSTEM)
    )
        private set

    var theme by mutableStateOf(
        runCatching { AppTheme.valueOf(store.getString("theme", "SYSTEM")!!) }
            .getOrDefault(AppTheme.SYSTEM)
    )
        private set

    fun updateLanguage(value: AppLanguage) {
        language = value
        store.edit().putString("language", value.name).apply()
    }

    fun updateTheme(value: AppTheme) {
        theme = value
        store.edit().putString("theme", value.name).apply()
    }
}
