package dev.pocket.notebook.feature.library

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.data.NotebookEntry
import dev.pocket.notebook.data.NotebookMetadata
import dev.pocket.notebook.i18n.AppStrings

@Composable
fun NotebookMetadataDialog(
    notebook: NotebookEntry, strings: AppStrings, coverBytes: ByteArray?,
    onChooseCover: () -> Unit, onRemoveCover: () -> Unit,
    onDismiss: () -> Unit, onSave: (NotebookMetadata) -> Unit
) {
    var title by rememberSaveable(notebook.id) { mutableStateOf(notebook.title) }
    var description by rememberSaveable(notebook.id) { mutableStateOf(notebook.description) }
    var author by rememberSaveable(notebook.id) { mutableStateOf(notebook.author) }
    var version by rememberSaveable(notebook.id) { mutableStateOf(notebook.version) }
    val valid = title.isNotBlank() && title.length <= 120 && description.length <= 500 &&
        author.length <= 120 && version.length <= 64
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.editMetadata) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { if (it.length <= 120) title = it },
                    label = { Text(strings.titleLabel) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), isError = title.isBlank())
                OutlinedTextField(value = description, onValueChange = { if (it.length <= 500) description = it },
                    label = { Text(strings.descriptionLabel) }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = author, onValueChange = { if (it.length <= 120) author = it },
                    label = { Text(strings.authorLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = version, onValueChange = { if (it.length <= 64) version = it },
                    label = { Text(strings.versionLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (coverBytes != null) NotebookCover(coverBytes)
                    Column {
                        TextButton(onClick = onChooseCover) { Text(strings.chooseCover) }
                        if (coverBytes != null) TextButton(onClick = onRemoveCover) { Text(strings.removeCover) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(NotebookMetadata(title.trim(), description.trim(), author.trim(), version.trim()))
            }) { Text(strings.saveMetadata) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}
