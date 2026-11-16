package dev.pocket.notebook.app.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.pocket.notebook.app.dialog.*
import dev.pocket.notebook.core.logging.NotebookLog as Log
import dev.pocket.notebook.core.state.NotebookAppState
import dev.pocket.notebook.domain.NotebookGateway
import dev.pocket.notebook.feature.files.*
import dev.pocket.notebook.i18n.AppStrings

@Composable
internal fun DeleteDialogHost(
    state: NotebookAppState,
    dialogs: AppDialogState,
    tools: PocketTools,
    repo: NotebookGateway,
    strings: AppStrings,
    message: (String) -> Unit,
    pickImage: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    dialogs.pendingDeleteId?.let { id ->
        val document = state.entries.firstOrNull { it.id == id }
        val folder = state.folders().firstOrNull { it.id == id }
        val title = document?.title ?: folder?.title
        if (title != null) {
            AlertDialog(
                onDismissRequest = { if (!deleting) dialogs.pendingDeleteId = null },
                title = { Text(strings.deleteTitle) },
                text = {
                    Text(
                        "$title\n${if (folder == null) strings.deleteDescription else strings.folderDeleteHint}"
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !deleting,
                        onClick = {
                            if (!deleting) {
                                deleting = true
                                scope.launch {
                                    try {
                                        state.delete(id)
                                    } catch (error: Exception) {
                                        Log.e("NotebookApp", "delete failed", error)
                                        message(strings.saveError)
                                    } finally {
                                        dialogs.pendingDeleteId = null
                                        deleting = false
                                    }
                                }
                            }
                        }
                    ) {
                        Text(strings.delete)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogs.pendingDeleteId = null }, enabled = !deleting) {
                        Text(strings.cancel)
                    }
                },
            )
        }
    }
}
