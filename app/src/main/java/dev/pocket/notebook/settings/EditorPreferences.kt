package dev.pocket.notebook.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** Saved editor-only preferences; distinct from the surrounding app theme. */
enum class EditorTheme { FOLLOW_APP, LIGHT, DARK }

class EditorPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("editor-preferences", Context.MODE_PRIVATE)

    var theme by mutableStateOf(
        runCatching { EditorTheme.valueOf(store.getString("theme", "FOLLOW_APP")!!) }
            .getOrDefault(EditorTheme.FOLLOW_APP)
    )
        private set
    var fontSize by mutableIntStateOf(store.getInt("font_size", 14).coerceIn(11, 24))
        private set
    var wordWrap by mutableStateOf(store.getBoolean("word_wrap", true))
        private set
    var autocomplete by mutableStateOf(store.getBoolean("autocomplete", true))
        private set
    var quickTools by mutableStateOf(store.getBoolean("quicktools", true))
        private set

    fun setEditorTheme(value: EditorTheme) {
        theme = value
        store.edit().putString("theme", value.name).apply()
    }
    fun updateFontSize(value: Int) {
        fontSize = value.coerceIn(11, 24)
        store.edit().putInt("font_size", fontSize).apply()
    }
    fun updateWordWrap(value: Boolean) {
        wordWrap = value
        store.edit().putBoolean("word_wrap", value).apply()
    }
    fun updateAutocomplete(value: Boolean) {
        autocomplete = value
        store.edit().putBoolean("autocomplete", value).apply()
    }
    fun updateQuickTools(value: Boolean) {
        quickTools = value
        store.edit().putBoolean("quicktools", value).apply()
    }
}
