@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package dev.pocket.notebook.feature.files.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.R
import kotlinx.coroutines.*

internal enum class ResourceToolbarAction {
    NEW_FILE,
    NEW_FOLDER,
    IMPORT,
    SELECT_ALL,
    COPY,
    CUT,
    MOVE,
    DELETE,
    CLEAR_SELECTION,
    PASTE,
    CLEAR_CLIPBOARD,
    REFRESH,
    TOGGLE_SORT,
}

@Composable
internal fun ResourceToolbar(
    selectedCount: Int,
    hasVisibleEntries: Boolean,
    hasClipboard: Boolean,
    sizeSort: Boolean,
    text: (Int) -> String,
    onAction: (ResourceToolbarAction) -> Unit,
) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (selectedCount == 0) {
            ResourceAction(
                R.string.res_new_file,
                Icons.AutoMirrored.Filled.NoteAdd,
                tag = "resource-new-file",
            ) {
                onAction(ResourceToolbarAction.NEW_FILE)
            }
            ResourceAction(
                R.string.res_new_folder,
                Icons.Default.CreateNewFolder,
                tag = "resource-new-folder",
            ) {
                onAction(ResourceToolbarAction.NEW_FOLDER)
            }
            ResourceAction(
                R.string.res_add_file,
                dev.pocket.notebook.core.ui.FileTransferIcons.Import,
                tag = "resource-import",
            ) {
                onAction(ResourceToolbarAction.IMPORT)
            }
            if (hasVisibleEntries)
                ResourceAction(R.string.fs_select_all, Icons.Default.SelectAll) {
                    onAction(ResourceToolbarAction.SELECT_ALL)
                }
        } else {
            Text(selectedCount.toString(), Modifier.padding(12.dp))
            ResourceAction(R.string.fs_select_all, Icons.Default.SelectAll) {
                onAction(ResourceToolbarAction.SELECT_ALL)
            }
            ResourceAction(R.string.fs_copy, Icons.Default.ContentCopy) {
                onAction(ResourceToolbarAction.COPY)
            }
            ResourceAction(R.string.fs_cut, Icons.Default.ContentCut) {
                onAction(ResourceToolbarAction.CUT)
            }
            ResourceAction(R.string.fs_move, Icons.AutoMirrored.Filled.DriveFileMove) {
                onAction(ResourceToolbarAction.MOVE)
            }
            ResourceAction(R.string.fs_delete, Icons.Default.DeleteOutline) {
                onAction(ResourceToolbarAction.DELETE)
            }
            ResourceAction(R.string.res_clear_selection, Icons.Default.Deselect) {
                onAction(ResourceToolbarAction.CLEAR_SELECTION)
            }
        }
        if (hasClipboard) {
            ResourceAction(R.string.fs_paste, Icons.Default.ContentPaste) {
                onAction(ResourceToolbarAction.PASTE)
            }
            ResourceAction(R.string.fs_clear_clipboard, Icons.Default.ClearAll) {
                onAction(ResourceToolbarAction.CLEAR_CLIPBOARD)
            }
        }
        ResourceAction(R.string.res_refresh, Icons.Default.Refresh) {
            onAction(ResourceToolbarAction.REFRESH)
        }
        ResourceAction(
            if (sizeSort) R.string.res_sort_name else R.string.res_sort_size,
            Icons.AutoMirrored.Filled.Sort,
        ) {
            onAction(ResourceToolbarAction.TOGGLE_SORT)
        }
    }
}
