package dev.pocket.notebook.feature.workspace.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.R
import dev.pocket.notebook.core.state.DocumentSaveState
import dev.pocket.notebook.domain.SaveOutcome
import dev.pocket.notebook.i18n.localizedString

@Composable
internal fun DocumentSaveStatus(state: DocumentSaveState, language: String, retry: () -> Unit) {
    val context = LocalContext.current
    val label =
        when (state) {
            is DocumentSaveState.Saving -> R.string.document_saving
            is DocumentSaveState.Failed -> R.string.document_save_failed
            is DocumentSaveState.Saved ->
                if (state.outcome == SaveOutcome.DRAFT) R.string.document_draft_saved
                else R.string.document_saved
            DocumentSaveState.Idle, is DocumentSaveState.Discarded -> R.string.document_saved
        }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            localizedString(context, language, label),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (state is DocumentSaveState.Failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state is DocumentSaveState.Failed)
            TextButton(retry) { Text(localizedString(context, language, R.string.retry)) }
    }
}
