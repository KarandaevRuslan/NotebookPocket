package dev.pocket.notebook.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.R
import dev.pocket.notebook.i18n.localizedString
import dev.pocket.notebook.settings.EditorPreferences
import dev.pocket.notebook.settings.EditorTheme
import kotlin.math.roundToInt

/** Editor settings are separate from navigation and from global appearance. */
@Composable
fun EditorSettingsSection(preferences: EditorPreferences, language: String) {
    val context = LocalContext.current
    fun label(id: Int) = localizedString(context, language, id)
    HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 10.dp))
    Text(label(R.string.editor_settings), style = MaterialTheme.typography.titleMedium)
    Text(label(R.string.editor_theme), modifier = Modifier.padding(top = 12.dp))
    EditorChoice(label(R.string.editor_follow_app), preferences.theme == EditorTheme.FOLLOW_APP) {
        preferences.setEditorTheme(EditorTheme.FOLLOW_APP)
    }
    EditorChoice(label(R.string.light), preferences.theme == EditorTheme.LIGHT) {
        preferences.setEditorTheme(EditorTheme.LIGHT)
    }
    EditorChoice(label(R.string.dark), preferences.theme == EditorTheme.DARK) {
        preferences.setEditorTheme(EditorTheme.DARK)
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label(R.string.editor_font_size))
        Text("${preferences.fontSize} sp", style = MaterialTheme.typography.labelLarge)
    }
    Slider(
        value = preferences.fontSize.toFloat(),
        onValueChange = { preferences.updateFontSize(it.roundToInt()) },
        valueRange = 11f..24f,
        steps = 12
    )
    EditorToggle(label(R.string.editor_word_wrap), preferences.wordWrap, preferences::updateWordWrap)
    EditorToggle(label(R.string.editor_autocomplete), preferences.autocomplete, preferences::updateAutocomplete)
    EditorToggle(label(R.string.editor_quicktools), preferences.quickTools, preferences::updateQuickTools)
}

@Composable
private fun EditorChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun EditorToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
