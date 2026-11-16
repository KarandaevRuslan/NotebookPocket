@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package dev.pocket.notebook.feature.files.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.pocket.notebook.R
import dev.pocket.notebook.data.ResourceInfo
import dev.pocket.notebook.feature.files.resourceReference
import dev.pocket.notebook.feature.files.resourceSize
import dev.pocket.notebook.feature.files.viewer.resourceImagePath
import kotlinx.coroutines.*

internal enum class ResourceRowAction {
    OPEN,
    EDIT,
    COPY_REFERENCE,
    COPY_PATH,
    SELECT,
    COPY,
    CUT,
    MOVE,
    RENAME,
    DELETE,
}

@Composable
internal fun ResourceRow(
    entry: ResourceInfo,
    isSelected: Boolean,
    selectionMode: Boolean,
    shared: Boolean,
    language: String,
    text: (Int) -> String,
    onAction: (ResourceRowAction) -> Unit,
) {
    var menu by remember(entry.path) { mutableStateOf(false) }
    val choose = { onAction(ResourceRowAction.SELECT) }
    val open = { onAction(ResourceRowAction.OPEN) }
    Card(Modifier.fillMaxWidth()) {
        ListItem(
            modifier =
                Modifier.combinedClickable(
                    onClick = { if (selectionMode) choose() else open() },
                    onLongClick = choose,
                ),
            headlineContent = {
                Text(
                    entry.path.substringAfterLast('/'),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                if (selectionMode) Checkbox(isSelected, { choose() })
                else
                    Icon(
                        if (entry.directory) Icons.Default.Folder
                        else if (resourceImagePath(entry.path)) Icons.Default.Image
                        else Icons.AutoMirrored.Filled.InsertDriveFile,
                        null,
                    )
            },
            supportingContent = {
                Column {
                    Text(
                        (if (entry.directory) text(R.string.res_folder) + " · " else "") +
                            resourceSize(entry.size, language),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!entry.directory)
                        Text(
                            resourceReference(entry.path, shared),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                }
            },
            trailingContent = {
                Box {
                    IconButton({ menu = true }) {
                        Icon(Icons.Default.MoreVert, text(R.string.more_actions))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(text(R.string.res_open)) },
                            onClick = {
                                menu = false
                                open()
                            },
                        )
                        if (!entry.directory) {
                            DropdownMenuItem(
                                text = { Text(text(R.string.res_edit)) },
                                onClick = {
                                    menu = false
                                    onAction(ResourceRowAction.EDIT)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text(R.string.res_copy_reference)) },
                                onClick = {
                                    menu = false
                                    onAction(ResourceRowAction.COPY_REFERENCE)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(text(R.string.res_copy_path)) },
                            onClick = {
                                menu = false
                                onAction(ResourceRowAction.COPY_PATH)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text(R.string.fs_select)) },
                            onClick = {
                                menu = false
                                choose()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text(R.string.fs_copy)) },
                            onClick = {
                                menu = false
                                onAction(ResourceRowAction.COPY)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text(R.string.fs_cut)) },
                            onClick = {
                                menu = false
                                onAction(ResourceRowAction.CUT)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text(R.string.fs_move)) },
                            onClick = {
                                menu = false
                                onAction(ResourceRowAction.MOVE)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text(R.string.fs_rename)) },
                            onClick = {
                                menu = false
                                onAction(ResourceRowAction.RENAME)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text(R.string.fs_delete)) },
                            onClick = {
                                menu = false
                                onAction(ResourceRowAction.DELETE)
                            },
                        )
                    }
                }
            },
        )
    }
}
