package dev.pocket.notebook.feature.workspace.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.pocket.notebook.i18n.AppStrings

/** Reset affects saved Preview inputs/progress, never the document source. */
@Composable
fun ResetPreviewDialog(strings: AppStrings, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.resetPreviewTitle) },
        text = { Text(strings.resetPreviewDescription) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(strings.resetPreview) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
