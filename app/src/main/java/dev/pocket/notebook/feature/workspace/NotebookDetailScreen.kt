package dev.pocket.notebook.feature.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.data.security.SecurityAction
import dev.pocket.notebook.feature.files.ResourceManager
import dev.pocket.notebook.feature.workspace.components.ResetPreviewDialog
import dev.pocket.notebook.feature.workspace.components.WorkspaceToolbar
import dev.pocket.notebook.feature.workspace.state.WorkspaceViewModel
import dev.pocket.notebook.i18n.AppStrings
import dev.pocket.notebook.settings.EditorPreferences
import dev.pocket.notebook.web.NotebookEditorWebView
import dev.pocket.notebook.web.NotebookPreviewWebView

/** Workspace owns source state; web implementations stay in the web package. */
@Composable
fun NotebookDetailRoute(
    id: String,
    title: String,
    encrypted: Boolean,
    workspace: WorkspaceViewModel,
    dark: Boolean,
    language: String,
    strings: AppStrings,
    editorPreferences: EditorPreferences,
    writer: dev.pocket.notebook.core.state.DocumentWriteCoordinator,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onEditMetadata: () -> Unit,
    onSecurityAction: (SecurityAction) -> Unit,
) {
    val source = workspace.source
    val mode = workspace.mode
    var resetDialog by remember(id) { mutableStateOf(false) }
    var previewResetRevision by remember(id) { mutableIntStateOf(0) }

    // The editor is one live Acode instance for this notebook. Move it between
    // adaptive containers; keep it (without focus) after switching to Preview.
    // Recreating AndroidView here discards selection, folds and undo history.
    var editorOpened by remember(id) { mutableStateOf(false) }
    var previewOpened by remember(id) { mutableStateOf(false) }
    val editorContent: @Composable (Modifier, Boolean) -> Unit = { childModifier, active ->
        NotebookEditorWebView(
            id = id,
            initialSource = source,
            externalRevision = workspace.externalSourceRevision,
            dark = dark,
            language = language,
            editorPreferences = editorPreferences,
            onChanged = { workspace.updateSource(it, writer, id) },
            selectionAnchor = workspace.selectionAnchor,
            selectionHead = workspace.selectionHead,
            editorScrollTop = workspace.editorScrollTop,
            onCursorSelection = workspace::saveEditorViewport,
            searchStateJson = workspace.searchStateJson,
            onSearchStateChange = workspace::updateSearchState,
            fullscreen = fullscreen,
            onFullscreenChange = onFullscreenChange,
            active = active,
            modifier = childModifier,
        )
    }
    val previewContent: @Composable (Modifier, Boolean) -> Unit = { childModifier, active ->
        NotebookPreviewWebView(
            id = id,
            source = source,
            dark = dark,
            language = language,
            encrypted = encrypted,
            resetRevision = previewResetRevision,
            modifier = childModifier,
            active = active,
        )
    }
    val latestEditorContent = rememberUpdatedState(editorContent)
    val latestPreviewContent = rememberUpdatedState(previewContent)
    val movableEditor =
        remember(id) {
            movableContentOf<Modifier, Boolean> { childModifier, active ->
                latestEditorContent.value(childModifier, active)
            }
        }
    val movablePreview =
        remember(id) {
            movableContentOf<Modifier, Boolean> { childModifier, active ->
                latestPreviewContent.value(childModifier, active)
            }
        }
    BackHandler(enabled = mode == 2 && !fullscreen) { workspace.selectMode(1) }
    BackHandler(enabled = fullscreen) { onFullscreenChange(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dualPane = !fullscreen && mode == 0 && maxWidth >= 1000.dp && maxHeight >= 650.dp
        val displayMode = if (fullscreen) 0 else mode
        val editorActive = dualPane || displayMode == 0
        val previewActive = dualPane || displayMode == 1
        LaunchedEffect(editorActive, previewActive) {
            if (editorActive) editorOpened = true
            if (previewActive) previewOpened = true
        }
        Column(Modifier.fillMaxSize()) {
            dev.pocket.notebook.feature.workspace.components.DocumentSaveStatus(
                workspace.saveState,
                language,
            ) {
                writer.retry(id)
            }
            if (!fullscreen)
                WorkspaceToolbar(
                    title = title,
                    encrypted = encrypted,
                    mode = mode,
                    onModeChange = workspace::selectMode,
                    dualPane = dualPane,
                    strings = strings,
                    onExport = onExport,
                    onEditMetadata = onEditMetadata,
                    onResetPreview = { resetDialog = true },
                    onSecurityAction = onSecurityAction,
                )
            if (dualPane) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    movablePreview(Modifier.weight(1f).fillMaxHeight(), true)
                    movableEditor(Modifier.weight(1f).fillMaxHeight(), true)
                }
            } else {
                Box(
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (editorActive || editorOpened) {
                        movableEditor(
                            if (editorActive) Modifier.fillMaxSize()
                            else
                                Modifier.requiredSize(0.dp).graphicsLayer {
                                    alpha = 0f
                                    clip = true
                                },
                            editorActive,
                        )
                    }
                    if (previewActive || previewOpened)
                        movablePreview(
                            if (previewActive) Modifier.fillMaxSize()
                            else
                                Modifier.requiredSize(0.dp).graphicsLayer {
                                    alpha = 0f
                                    clip = true
                                },
                            previewActive,
                        )
                    if (displayMode == 2)
                        dev.pocket.notebook.feature.files.ResourceManager(id, embedded = true) {
                            workspace.selectMode(1)
                        }
                }
            }
        }
    }
    if (resetDialog) {
        ResetPreviewDialog(
            strings = strings,
            onConfirm = {
                resetDialog = false
                previewResetRevision++
            },
            onDismiss = { resetDialog = false },
        )
    }
}
