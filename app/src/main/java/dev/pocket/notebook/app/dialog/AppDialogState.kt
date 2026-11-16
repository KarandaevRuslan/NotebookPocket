package dev.pocket.notebook.app.dialog

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.pocket.notebook.data.security.SecurityAction
import dev.pocket.notebook.domain.NotebookSecurity

internal class AppDialogState(
    pendingDeleteId: MutableState<String?>,
    pendingMetadataId: MutableState<String?>,
    pendingCoverDraft: MutableState<Triple<String, ByteArray?, String?>?>,
    pendingSecurityRequestedId: MutableState<String?>,
    pendingSecurityId: MutableState<String?>,
    pendingAction: MutableState<SecurityAction>,
    openAfterUnlock: MutableState<String?>,
    securityBusy: MutableState<Boolean>,
    securityError: MutableState<String?>,
) {
    var pendingDeleteId by pendingDeleteId
    var pendingMetadataId by pendingMetadataId
    var pendingCoverDraft by pendingCoverDraft
    var pendingSecurityRequestedId by pendingSecurityRequestedId
    var pendingSecurityId by pendingSecurityId
    var pendingAction by pendingAction
    var openAfterUnlock by openAfterUnlock
    var securityBusy by securityBusy
    var securityError by securityError

    fun requestSecurity(repo: NotebookSecurity, id: String, action: SecurityAction) {
        pendingSecurityRequestedId = id
        pendingSecurityId = repo.securityOwner(id)
        pendingAction = action
        securityError = null
    }
}

@Composable
internal fun rememberAppDialogState(): AppDialogState {
    val pendingDeleteId = rememberSaveable { mutableStateOf<String?>(null) }
    val pendingMetadataId = rememberSaveable { mutableStateOf<String?>(null) }
    // Cover is staged together with title/author/version. Cancel discards it.
    val pendingCoverDraft = remember { mutableStateOf<Triple<String, ByteArray?, String?>?>(null) }
    val pendingSecurityRequestedId = rememberSaveable { mutableStateOf<String?>(null) }
    val pendingSecurityId = rememberSaveable { mutableStateOf<String?>(null) }
    val pendingAction = rememberSaveable { mutableStateOf(SecurityAction.UNLOCK) }
    val openAfterUnlock = rememberSaveable { mutableStateOf<String?>(null) }
    val securityBusy = remember { mutableStateOf(false) }
    val securityError = remember { mutableStateOf<String?>(null) }

    return remember {
        AppDialogState(
            pendingDeleteId,
            pendingMetadataId,
            pendingCoverDraft,
            pendingSecurityRequestedId,
            pendingSecurityId,
            pendingAction,
            openAfterUnlock,
            securityBusy,
            securityError,
        )
    }
}
