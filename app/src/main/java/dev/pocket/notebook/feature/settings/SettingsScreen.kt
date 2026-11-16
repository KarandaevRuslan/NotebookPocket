package dev.pocket.notebook.feature.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.i18n.AppStrings
import dev.pocket.notebook.settings.AppLanguage
import dev.pocket.notebook.settings.AppPreferences
import dev.pocket.notebook.settings.AppTheme
import dev.pocket.notebook.i18n.resolveLanguage

/** A proper top-level destination; navigation bar/rail is provided by the app scaffold. */
@Composable
fun SettingsScreen(preferences: AppPreferences, strings: AppStrings) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(strings.settings, style = MaterialTheme.typography.titleLarge)
        Text(strings.language, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        PreferenceChoice(strings.system, preferences.language == AppLanguage.SYSTEM) { preferences.updateLanguage(AppLanguage.SYSTEM) }
        PreferenceChoice(strings.russian, preferences.language == AppLanguage.RU) { preferences.updateLanguage(AppLanguage.RU) }
        PreferenceChoice(strings.english, preferences.language == AppLanguage.EN) { preferences.updateLanguage(AppLanguage.EN) }
        Text(strings.theme, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        PreferenceChoice(strings.system, preferences.theme == AppTheme.SYSTEM) { preferences.updateTheme(AppTheme.SYSTEM) }
        PreferenceChoice(strings.light, preferences.theme == AppTheme.LIGHT) { preferences.updateTheme(AppTheme.LIGHT) }
        PreferenceChoice(strings.dark, preferences.theme == AppTheme.DARK) { preferences.updateTheme(AppTheme.DARK) }
        EditorSettingsSection(preferences = preferences.editor, language = resolveLanguage(preferences.language))
    }
}

@Composable
private fun PreferenceChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
