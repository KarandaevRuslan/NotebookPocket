package dev.pocket.notebook.web

import dev.pocket.notebook.settings.EditorPreferences
import dev.pocket.notebook.settings.EditorTheme
import org.json.JSONObject

/** Serialization boundary for native preferences; document text always uses JSON quoting. */
internal object EditorProtocol {
    fun preferences(preferences: EditorPreferences, dark: Boolean, language: String): String {
        return """(function(){window.EditorApi?.setPreferences({
            dark:$dark,
            language:${JSONObject.quote(language)},
            theme:${JSONObject.quote(when (preferences.theme) {
                EditorTheme.LIGHT -> "light"
                EditorTheme.DARK -> "dark"
                EditorTheme.FOLLOW_APP -> "system"
            })},
            fontSize:${preferences.fontSize},
            wordWrap:${preferences.wordWrap},
            autocomplete:${preferences.autocomplete},
            showQuickTools:${preferences.quickTools}
        })})()"""
            .trimIndent()
    }
}
