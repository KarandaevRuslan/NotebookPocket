@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.pocket.notebook.feature.files.actions

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.R
import dev.pocket.notebook.data.VfsItem
import dev.pocket.notebook.data.security.SecurityAction
import dev.pocket.notebook.feature.files.LocalPocketTools
import dev.pocket.notebook.feature.files.dialogs.FileNameForm

private data class LibraryIconAction(
    val label: String,
    val icon: ImageVector,
    val click: () -> Unit,
    val count: Int? = null,
)

@Composable
internal fun LibraryFileBar(
    current: VfsItem.Folder?,
    items: List<VfsItem>,
    onCreate: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onCreateFolder: (() -> Unit)? = null,
) {
    val t = LocalPocketTools.current
    var creating by remember { mutableStateOf(false) }
    var conversion by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val actions = mutableListOf<LibraryIconAction>()
    fun add(label: Int, icon: ImageVector, click: () -> Unit) {
        actions.add(
            LibraryIconAction(
                t.text(label),
                icon,
                click,
                when (label) {
                    R.string.fs_select_all -> t.selected.size
                    R.string.fs_paste -> t.clipboard?.ids?.size
                    else -> null
                },
            )
        )
    }
    if (t.selected.isNotEmpty()) {
        add(R.string.fs_select_all, Icons.Default.SelectAll) {
            t.selected = items.map { it.id }.toSet()
        }
        add(R.string.fs_copy, Icons.Default.ContentCopy) { t.copy(t.selected.toList(), false) }
        add(R.string.fs_cut, Icons.Default.ContentCut) { t.copy(t.selected.toList(), true) }
        add(R.string.fs_move, Icons.AutoMirrored.Filled.DriveFileMove) {
            t.destination = t.selected.toList()
        }
        add(R.string.fs_export, Icons.Default.FolderZip) { t.exportIds = t.selected.toList() }
        add(R.string.fs_delete, Icons.Default.DeleteOutline) { deleting = true }
        add(R.string.fs_cancel, Icons.Default.Deselect) { t.selected = emptySet() }
    } else {
        if (current?.unlocked != false) {
            if (onCreate != null)
                add(R.string.new_notebook, Icons.AutoMirrored.Filled.NoteAdd, onCreate)
            if (current?.collection != true) {
                if (onCreateFolder != null)
                    add(R.string.new_folder, Icons.Default.CreateNewFolder, onCreateFolder)
                add(R.string.fs_new_collection, Icons.AutoMirrored.Filled.LibraryBooks) {
                    creating = true
                }
            }
            if (onImport != null)
                add(
                    R.string.import_file,
                    dev.pocket.notebook.core.ui.FileTransferIcons.Import,
                    onImport,
                )
            if (current != null && (current.collection || items.none { it is VfsItem.Folder }))
                add(
                    if (current.collection) R.string.fs_to_folder else R.string.fs_to_collection,
                    Icons.Default.SwapHoriz,
                ) {
                    conversion = true
                }
            if (current?.collection == true) {
                add(R.string.fs_shared, Icons.Default.FolderOpen) { t.resourceOwner = current.id }
                add(
                    if (current.encrypted) R.string.fs_change_password
                    else R.string.fs_set_password,
                    Icons.Default.Password,
                ) {
                    t.security(
                        current.id,
                        if (current.encrypted) SecurityAction.CHANGE else SecurityAction.SET,
                    )
                }
            }
        } else
            add(R.string.fs_unlock, Icons.Default.LockOpen) {
                t.security(current.id, SecurityAction.UNLOCK)
            }
        if (current != null)
            add(R.string.fs_export, Icons.Default.FolderZip) { t.exportIds = listOf(current.id) }
        if (
            t.repo.list().any { it.encrypted && it.unlocked } ||
                t.repo.allFolders().any { it.encrypted && it.unlocked }
        )
            add(R.string.fs_lock, Icons.Default.Lock) {
                t.repo.lockAll()
                t.changed()
            }
    }
    t.clipboard?.let { clip ->
        if (current?.unlocked != false)
            add(R.string.fs_paste, Icons.Default.ContentPaste) {
                t.transfer(clip.ids, current?.id, clip.cut)
            }
        add(R.string.fs_clear_clipboard, Icons.Default.ClearAll) { t.clipboard = null }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Keep 48 dp touch targets. Use one row whenever it fits; wrap only when required.
            val gap =
                if (
                    48.dp * actions.size + 4.dp * (actions.size - 1).coerceAtLeast(0) <=
                        maxWidth - 8.dp
                )
                    4.dp
                else 0.dp
            FlowRow(
                Modifier.padding(4.dp).testTag("library-actions-grid"),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                actions.forEach { action ->
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(action.label) } },
                        state = rememberTooltipState(),
                    ) {
                        FilledTonalIconButton(
                            action.click,
                            Modifier.size(48.dp).semantics { contentDescription = action.label },
                        ) {
                            BadgedBox(
                                badge = { action.count?.let { Badge { Text(it.toString()) } } }
                            ) {
                                Icon(action.icon, null)
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating)
        FileNameForm(t.text(R.string.fs_new_collection), dismiss = { creating = false }) { name ->
            t.perform({ t.repo.createCollection(name, current?.id) }) { creating = false }
        }
    if (conversion && current != null)
        AlertDialog(
            onDismissRequest = { conversion = false },
            title = {
                Text(
                    t.text(
                        if (current.collection) R.string.fs_to_folder else R.string.fs_to_collection
                    )
                )
            },
            text = { Text(t.text(R.string.fs_conversion_help)) },
            confirmButton = {
                TextButton({
                    t.perform({ t.repo.convertContainer(current.id) }) { conversion = false }
                }) {
                    Text(t.text(R.string.fs_confirm))
                }
            },
            dismissButton = {
                TextButton({ conversion = false }) { Text(t.text(R.string.fs_cancel)) }
            },
        )
    if (deleting)
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(t.text(R.string.fs_delete)) },
            text = { Text(t.text(R.string.fs_delete_help)) },
            confirmButton = {
                TextButton({
                    val ids = t.selected.toList()
                    t.performDelete(ids) {
                        t.selected = emptySet()
                        deleting = false
                    }
                }) {
                    Text(t.text(R.string.fs_delete))
                }
            },
            dismissButton = {
                TextButton({ deleting = false }) { Text(t.text(R.string.fs_cancel)) }
            },
        )
}

@Composable
internal fun SelectionMark(id: String) {
    val t = LocalPocketTools.current
    if (t.selected.isNotEmpty())
        Row(Modifier.fillMaxWidth()) { Checkbox(id in t.selected, { t.select(id) }) }
}
