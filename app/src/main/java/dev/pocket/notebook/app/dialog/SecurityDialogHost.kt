package dev.pocket.notebook.app.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import dev.pocket.notebook.app.dialog.*
import dev.pocket.notebook.core.logging.NotebookLog as Log
import dev.pocket.notebook.core.state.NotebookAppState
import dev.pocket.notebook.data.security.SecurityAction
import dev.pocket.notebook.domain.NotebookGateway
import dev.pocket.notebook.feature.files.*
import dev.pocket.notebook.feature.library.NotebookSecurityDialog
import dev.pocket.notebook.i18n.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SecurityDialogHost(
    state: NotebookAppState,
    dialogs: AppDialogState,
    tools: PocketTools,
    repo: NotebookGateway,
    strings: AppStrings,
    message: (String) -> Unit,
    pickImage: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    dialogs.pendingSecurityId?.let { id ->
        NotebookSecurityDialog(
            id = id,
            action = dialogs.pendingAction,
            strings = strings,
            targetLabel =
                state
                    .folders()
                    .firstOrNull { it.id == id && it.collection }
                    ?.let {
                        tools.text(dev.pocket.notebook.R.string.fs_collection) + " · " + it.title
                    },
            busy = dialogs.securityBusy,
            error = dialogs.securityError,
            onDismiss = {
                dialogs.pendingSecurityId = null
                dialogs.securityError = null
            },
            onSubmit = { secret ->
                val requestedId = dialogs.pendingSecurityRequestedId ?: id
                val operation = dialogs.pendingAction
                val operationEpoch = tools.lockEpoch
                dialogs.securityBusy = true
                scope.launch {
                    val outcome = runCatching {
                        withContext(Dispatchers.IO) {
                            try {
                                if (operation != SecurityAction.UNLOCK) tools.beforeOperation()
                                when (operation) {
                                    SecurityAction.UNLOCK -> repo.unlock(id, secret)
                                    SecurityAction.SET,
                                    SecurityAction.CHANGE -> repo.setPassword(id, secret)
                                    SecurityAction.REMOVE -> repo.removePassword(id)
                                }
                            } finally {
                                secret.fill('\u0000')
                            }
                        }
                    }
                    dialogs.securityBusy = false
                    if (operationEpoch != tools.lockEpoch) {
                        repo.lockAll()
                        state.refresh()
                        return@launch
                    }
                    outcome
                        .onSuccess {
                            state.refresh()
                            if (operation == SecurityAction.UNLOCK)
                                dialogs.openAfterUnlock = requestedId
                            dialogs.pendingSecurityId = null
                            dialogs.securityError = null
                        }
                        .onFailure {
                            Log.w("NotebookApp", "Password operation failed", it)
                            dialogs.securityError =
                                if (it is dev.pocket.notebook.domain.PendingDraftException)
                                    tools.errorText(it)
                                else strings.passwordError
                        }
                }
            },
        )
    }
}
