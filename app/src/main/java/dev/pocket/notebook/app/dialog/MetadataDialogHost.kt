package dev.pocket.notebook.app.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.pocket.notebook.app.dialog.*
import dev.pocket.notebook.core.state.NotebookAppState
import dev.pocket.notebook.domain.NotebookGateway
import dev.pocket.notebook.feature.files.*
import dev.pocket.notebook.feature.library.NotebookMetadataDialog
import dev.pocket.notebook.i18n.AppStrings

@Composable
internal fun MetadataDialogHost(
    state: NotebookAppState,
    dialogs: AppDialogState,
    tools: PocketTools,
    repo: NotebookGateway,
    strings: AppStrings,
    message: (String) -> Unit,
    pickImage: (String) -> Unit = {},
) {
    dialogs.pendingMetadataId?.let { id ->
        state.entries
            .firstOrNull { it.id == id }
            ?.let { notebook ->
                NotebookMetadataDialog(
                    notebook = notebook,
                    strings = strings,
                    coverBytes =
                        if (dialogs.pendingCoverDraft?.first == id)
                            dialogs.pendingCoverDraft?.second
                        else
                            remember(id, notebook.updated) {
                                if (notebook.hasCover) state.readCover(id) else null
                            },
                    onChooseCover = {
                        tools.picker = true
                        pickImage(id)
                    },
                    onRemoveCover = { dialogs.pendingCoverDraft = Triple(id, null, null) },
                    onDismiss = {
                        if (!tools.busy) {
                            dialogs.pendingMetadataId = null
                            dialogs.pendingCoverDraft = null
                        }
                    },
                    onSave = { metadata ->
                        val draft = dialogs.pendingCoverDraft?.takeIf { it.first == id }
                        tools.perform({
                            repo.updateMetadataAndCover(
                                id,
                                metadata,
                                draft != null,
                                draft?.second,
                                draft?.third,
                            )
                        }) {
                            dialogs.pendingMetadataId = null
                            dialogs.pendingCoverDraft = null
                        }
                    },
                )
            }
    }
}
