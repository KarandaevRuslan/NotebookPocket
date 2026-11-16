package dev.pocket.notebook.feature.library

import dev.pocket.notebook.feature.files.LocalPocketTools
import dev.pocket.notebook.feature.files.actions.LibraryFileMenu
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.core.ui.components.card.AppCard
import dev.pocket.notebook.core.ui.components.card.AppCardContent
import dev.pocket.notebook.core.ui.components.card.AppSwipeActionCard
import dev.pocket.notebook.core.ui.components.card.AppCardSwipeAction
import dev.pocket.notebook.data.VfsItem
import dev.pocket.notebook.i18n.AppStrings
import androidx.compose.material3.Text

/** Existing app card/swipe/drag primitives, applied to folders without creating a new design system. */
@Composable
internal fun FolderCard(
    folder: VfsItem.Folder, strings: AppStrings,
    onOpen: () -> Unit, onRename: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    AppSwipeActionCard(
        modifier = modifier.fillMaxWidth(),
        startToEndAction = AppCardSwipeAction(
            strings.moveToFolder, Icons.Default.DriveFileMove,
            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer
        ),
        endToStartAction = AppCardSwipeAction(
            strings.delete, Icons.Default.Delete,
            MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer
        ),
        onStartToEnd = onMove,
        onEndToStart = onDelete
    ) {
        AppCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
            AppCardContent(
                title = folder.title,
                subtitle = buildList { if(folder.collection)add(dev.pocket.notebook.feature.files.LocalPocketTools.current.text(dev.pocket.notebook.R.string.fs_collection));if(folder.encrypted)add(if(folder.unlocked)strings.encryptedLabel else strings.unlockDocument) }.joinToString(" · ").ifEmpty {null},
                leadingContent = { Icon(if(folder.collection) Icons.AutoMirrored.Filled.LibraryBooks else Icons.Default.Folder, contentDescription = null,
                    modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Column {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = strings.moreActions)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            dev.pocket.notebook.feature.files.actions.LibraryFileMenu(folder.id,folder){menu=false}
                            DropdownMenuItem(text = { Text(strings.renameFolder) }, onClick = { menu = false; onRename() })
                            DropdownMenuItem(text = { Text(strings.moveToFolder) }, onClick = { menu = false; onMove() })
                            DropdownMenuItem(text = { Text(strings.delete) }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
            )
        }
    }
}
