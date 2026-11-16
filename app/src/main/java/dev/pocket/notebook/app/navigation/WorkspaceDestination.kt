package dev.pocket.notebook.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.pocket.notebook.R
import dev.pocket.notebook.core.logging.NotebookLog as Log
import dev.pocket.notebook.core.state.DocumentWriteCoordinator
import dev.pocket.notebook.core.state.DocumentSaveState
import dev.pocket.notebook.data.NotebookEntry
import dev.pocket.notebook.data.NotebookMetadata
import dev.pocket.notebook.data.NotebookMetadataCodec
import dev.pocket.notebook.data.security.SecurityAction
import dev.pocket.notebook.domain.DocumentSnapshot
import dev.pocket.notebook.feature.workspace.NotebookDetailRoute
import dev.pocket.notebook.feature.workspace.state.WorkspaceViewModel
import dev.pocket.notebook.i18n.AppStrings
import dev.pocket.notebook.i18n.localizedString
import dev.pocket.notebook.settings.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owns a route-scoped workspace and applies metadata changes to its live editor source. */
@Composable
internal fun WorkspaceDestination(
    notebook: NotebookEntry,
    read: (String) -> DocumentSnapshot,
    writer: DocumentWriteCoordinator,
    dark: Boolean,
    language: String,
    strings: AppStrings,
    preferences: AppPreferences,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onCommitted: () -> Unit,
    onExport: (String) -> Unit,
    onEditMetadata: (String) -> Unit,
    onSecurityAction: (String, SecurityAction) -> Unit,
) {
    DisposableEffect(notebook.id, writer) {
        // Leaving the workspace must not wait for the next debounce tick.
        onDispose { writer.requestFlush() }
    }
    var attempt by remember(notebook.id) { mutableIntStateOf(0) }
    val loaded by
        produceState<Result<DocumentSnapshot>?>(null, notebook.id, attempt) {
            value =
                try {
                    Result.success(
                        withContext(Dispatchers.IO) {
                            writer.loadLatest(notebook.id) { read(notebook.id) }
                        }
                    )
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    Result.failure(error)
                }
        }
    val snapshot = loaded?.getOrNull()
    if (snapshot == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loaded == null) CircularProgressIndicator()
            else
                Column(Modifier.padding(24.dp)) {
                    Text(
                        localizedString(
                            LocalContext.current,
                            language,
                            R.string.document_read_failed,
                        )
                    )
                    TextButton({ attempt++ }) { Text(strings.retry) }
                }
        }
        return
    }
    LoadedWorkspaceDestination(
        notebook,
        snapshot,
        writer,
        dark,
        language,
        strings,
        preferences,
        fullscreen,
        onFullscreenChange,
        onCommitted,
        onExport,
        onEditMetadata,
        onSecurityAction,
    )
}

@Composable
private fun LoadedWorkspaceDestination(
    notebook: NotebookEntry,
    snapshot: DocumentSnapshot,
    writer: DocumentWriteCoordinator,
    dark: Boolean,
    language: String,
    strings: AppStrings,
    preferences: AppPreferences,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onCommitted: () -> Unit,
    onExport: (String) -> Unit,
    onEditMetadata: (String) -> Unit,
    onSecurityAction: (String, SecurityAction) -> Unit,
) {
    val workspace: WorkspaceViewModel =
        viewModel(
            key = "workspace:${notebook.id}",
            factory =
                viewModelFactory {
                    initializer {
                        WorkspaceViewModel(
                            createSavedStateHandle(),
                            snapshot.source,
                            snapshot.fromDraft,
                            writer.status(notebook.id).value,
                        )
                    }
                },
        )
    val currentNotebook by rememberUpdatedState(notebook)
    val refreshAfterCommit by rememberUpdatedState(onCommitted)
    LaunchedEffect(writer, notebook.id) {
        writer.status(notebook.id).collect { status ->
            workspace.acknowledge(status)
            // Only the current, durable revision may update library metadata.
            // Never overwrite an incomplete or newer draft from a stale DB entry.
            if (
                status is DocumentSaveState.Saved &&
                    status.outcome == dev.pocket.notebook.domain.SaveOutcome.COMMITTED &&
                    workspace.saveState == status
            ) {
                val sourceMeta = NotebookMetadataCodec.read(workspace.source)
                val entry = currentNotebook
                if (
                    sourceMeta.title != entry.title ||
                        sourceMeta.description != entry.description ||
                        sourceMeta.author != entry.author ||
                        sourceMeta.version != entry.version
                ) refreshAfterCommit()
            }
        }
    }
    val metadata =
        NotebookMetadata(notebook.title, notebook.description, notebook.author, notebook.version)
    var previousMetadata by remember(notebook.id) { mutableStateOf(metadata) }
    LaunchedEffect(metadata) {
        // A recovered incomplete draft must not be "repaired" from stale public metadata on entry.
        if (previousMetadata != metadata) {
            runCatching { NotebookMetadataCodec.update(workspace.source, metadata) }
                .onSuccess { workspace.applyExternalSource(it, writer, notebook.id) }
                .onFailure { Log.e("NotebookNav", "metadata sync failed", it) }
            previousMetadata = metadata
        }
    }
    NotebookDetailRoute(
        id = notebook.id,
        title = notebook.title.ifBlank { strings.untitled },
        encrypted = notebook.encrypted,
        workspace = workspace,
        dark = dark,
        language = language,
        strings = strings,
        editorPreferences = preferences.editor,
        writer = writer,
        fullscreen = fullscreen,
        onFullscreenChange = { enabled -> onFullscreenChange(enabled) },
        onExport = { onExport(notebook.id) },
        onEditMetadata = { onEditMetadata(notebook.id) },
        onSecurityAction = { action -> onSecurityAction(notebook.id, action) },
    )
}
