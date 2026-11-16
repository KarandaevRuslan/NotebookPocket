package dev.pocket.notebook.feature.library

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.data.VfsItem
import dev.pocket.notebook.i18n.AppStrings

@Composable
internal fun FolderNameDialog(
    strings: AppStrings, initial: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit
) {
    var name by rememberSaveable(initial) { mutableStateOf(initial.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) strings.newFolder else strings.renameFolder) },
        text = { OutlinedTextField(name, { name = it.take(120) }, label = { Text(strings.folderName) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(strings.confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}

private data class FolderOption(val id: String?, val label: String, val depth: Int)

/** Presents root and every existing folder, with hierarchical indentation. */
@Composable
internal fun FolderMoveDialog(
    strings: AppStrings, folders: List<VfsItem.Folder>, itemId: String,
    currentParent: String?, onDismiss: () -> Unit, onConfirm: (String?) -> Unit
) {
    val options = remember(folders, itemId, strings.rootFolder) {
        val byParent = folders.groupBy { it.parentId }
        val list = mutableListOf(FolderOption(null, strings.rootFolder, 0))
        fun addChildren(parent: String?, depth: Int, visited: MutableSet<String>) {
            for (folder in byParent[parent].orEmpty()) {
                if (folder.id == itemId || !visited.add(folder.id) || !folder.unlocked || folder.collection && folders.any {it.id==itemId}) continue
                list.add(FolderOption(folder.id, folder.title, depth))
                addChildren(folder.id, depth + 1, visited)
            }
        }
        addChildren(null, 1, mutableSetOf())
        list
    }
    var selected by rememberSaveable(itemId) { mutableStateOf(currentParent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.moveToFolder) },
        text = {
            LazyColumn {
                items(options, key = { it.id ?: "ROOT" }) { option ->
                    Row(Modifier.clickable { selected = option.id }.padding(start = (option.depth * 12).dp)) {
                        RadioButton(selected = selected == option.id, onClick = { selected = option.id })
                        Text(option.label, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(strings.confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}
