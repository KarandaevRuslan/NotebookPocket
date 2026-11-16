package dev.pocket.notebook.app

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.pocket.notebook.app.dialog.*
import dev.pocket.notebook.app.document.rememberDocumentTransfer
import dev.pocket.notebook.app.navigation.NotebookNavHost
import dev.pocket.notebook.core.logging.NotebookLog as Log
import dev.pocket.notebook.core.state.NotebookAppState
import dev.pocket.notebook.data.security.SecurityAction
import dev.pocket.notebook.domain.NotebookGateway
import dev.pocket.notebook.feature.files.*
import dev.pocket.notebook.i18n.resolveLanguage
import dev.pocket.notebook.i18n.stringsFor
import dev.pocket.notebook.settings.AppPreferences
import dev.pocket.notebook.ui.theme.NotebookTheme
import dev.pocket.notebook.ui.theme.isDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Composition root. No repository implementation or WebView management in this layer. */
@Composable
fun NotebookPocketApp(repo: NotebookGateway, preferences: AppPreferences) {
    val context = LocalContext.current
    val state = remember(repo) { NotebookAppState(repo) }
    val language = resolveLanguage(preferences.language)
    // Replace only the untouched built-in demo. Never modify notebooks users edited.
    LaunchedEffect(repo) {
        if (
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { repo.migrateUntouchedLegacyDemo(language) }.getOrDefault(false)
            }
        )
            state.refresh()
    }
    val strings = remember(context, language) { stringsFor(context, language) }
    val dark = isDarkTheme(preferences.theme)
    var pendingImportFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val dialogs = rememberAppDialogState()
    val scope = rememberCoroutineScope()
    fun requestSecurity(id: String, action: SecurityAction) =
        dialogs.requestSecurity(repo, id, action)

    fun message(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
    val tools =
        remember(repo) {
            PocketTools(repo, context, scope, state::refresh, ::requestSecurity).apply {
                deleteItemsSafely = state::deleteItems
                beforeOperation = {
                    if (!state.writer.flush())
                        throw dev.pocket.notebook.domain.PendingDraftException()
                }
            }
        }
    tools.language = language
    val window = (context as? android.app.Activity)?.window
    val privateVisible =
        tools.privateVisible ||
            dialogs.pendingSecurityId != null ||
            tools.exportIds != null ||
            tools.importRequest != null ||
            dialogs.pendingMetadataId?.let {
                runCatching { repo.ownerProtected(it) }.getOrDefault(false)
            } == true ||
            tools.resourceOwner?.let {
                runCatching { repo.ownerProtected(it) }.getOrDefault(false)
            } == true
    DisposableEffect(window, privateVisible) {
        if (privateVisible) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { state.writer.requestFlush() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!tools.picker && (context as? android.app.Activity)?.isChangingConfigurations != true) {
            tools.lockEpoch++
            tools.confirmResourceTransfer = null
            tools.resourceOwner = null
            tools.resourceClipboard = null
            tools.exportIds = null
            tools.destination = null
            tools.confirmTransfer = null
            dialogs.pendingMetadataId = null
            dialogs.pendingCoverDraft = null
            dialogs.pendingSecurityId = null
            state.flushAndLock { tools.changed() }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { state.foreground() }
    val transfers =
        rememberDocumentTransfer(
            onImport = { uri -> tools.import(uri, pendingImportFolder) },
            onExport = { id, uri ->
                // This less-used transfer callback must observe the same save barrier
                // as PocketTools exports, including drafts and failed writes.
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            if (!state.writer.flush())
                                throw dev.pocket.notebook.domain.PendingDraftException()
                            state.export(id, uri)
                        }
                    }
                    result.onSuccess { message(strings.exported) }
                        .onFailure {
                            Log.e("NotebookApp", "Export failed", it)
                            message(strings.exportError)
                        }
                }
            },
            onPickerFinished = { tools.picker = false },
            onImage = { id, uri ->
                tools.picker = false
                runCatching {
                        val blob =
                            requireNotNull(context.contentResolver.openInputStream(uri)).use {
                                dev.pocket.notebook.data.packing.ArchiveFiles.read(
                                    it,
                                    dev.pocket.notebook.data.CoverImages.MAX_BYTES,
                                )
                            }
                        val mime = dev.pocket.notebook.data.CoverImages.mime(blob)
                        if (dialogs.pendingMetadataId == id)
                            dialogs.pendingCoverDraft = Triple(id, blob, mime)
                        else state.setCover(id, blob, mime)
                    }
                    .onFailure {
                        Log.e("NotebookApp", "Cover failed", it)
                        message(strings.coverError)
                    }
            },
        )

    NotebookTheme(dark) {
        CompositionLocalProvider(LocalPocketTools provides tools) {
            Surface(Modifier.fillMaxSize()) {
                NotebookNavHost(
                    entries = state.entries,
                    revision = state.revision,
                    children = state::children,
                    folderPath = state::path,
                    folders = state::folders,
                    strings = strings,
                    language = language,
                    dark = dark,
                    preferences = preferences,
                    read = state::load,
                    writer = state.writer,
                    onRefresh = state::refresh,
                    onCreate = { parentId -> state.create(language, parentId) },
                    onCreateFolder = { name, parentId ->
                        runCatching { state.createFolder(name, parentId) }
                            .onFailure {
                                Log.e("NotebookApp", "create folder", it)
                                message(strings.saveError)
                            }
                    },
                    onRenameFolder = { id, name ->
                        runCatching { state.renameFolder(id, name) }
                            .onFailure {
                                Log.e("NotebookApp", "rename folder", it)
                                message(strings.saveError)
                            }
                    },
                    onMoveToFolder = { id, destination ->
                        tools.transfer(listOf(id), destination, true)
                    },
                    onImport = { folderId ->
                        pendingImportFolder = folderId
                        tools.picker = true
                        transfers.import()
                    },
                    onExport = { id -> tools.exportIds = listOf(id) },
                    onDelete = { dialogs.pendingDeleteId = it.id },
                    onSetOrder = { folderId, orderedIds ->
                        runCatching { state.setSiblingOrder(folderId, orderedIds) }
                            .onFailure { error ->
                                Log.e("NotebookApp", "Reorder failed", error)
                                state.refresh()
                                message(strings.saveError)
                            }
                    },
                    onEditMetadata = { id ->
                        if (state.isUnlocked(id)) {
                            dialogs.pendingCoverDraft = null
                            dialogs.pendingMetadataId = id
                        } else requestSecurity(id, SecurityAction.UNLOCK)
                    },
                    onSecurityAction = ::requestSecurity,
                    onRequireUnlock = { id -> requestSecurity(id, SecurityAction.UNLOCK) },
                    loadCover = { id -> runCatching { state.readCover(id) }.getOrNull() },
                    openUnlockedId = dialogs.openAfterUnlock,
                    onConsumedOpen = { dialogs.openAfterUnlock = null },
                )
                PocketToolsHost()
                MetadataDialogHost(
                    state,
                    dialogs,
                    tools,
                    repo,
                    strings,
                    ::message,
                    transfers.pickImage,
                )
                SecurityDialogHost(state, dialogs, tools, repo, strings, ::message)
                DeleteDialogHost(state, dialogs, tools, repo, strings, ::message)
            }
        }
    }
}
