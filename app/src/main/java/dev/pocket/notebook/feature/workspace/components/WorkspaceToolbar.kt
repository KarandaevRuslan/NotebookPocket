package dev.pocket.notebook.feature.workspace.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.data.security.SecurityAction
import dev.pocket.notebook.i18n.AppStrings

/**
 * Compact toolbar. The active document is always visible; actions are behind one Material3 menu.
 */
@Composable
fun WorkspaceToolbar(
    title: String,
    encrypted: Boolean,
    mode: Int,
    onModeChange: (Int) -> Unit,
    dualPane: Boolean,
    strings: AppStrings,
    onExport: () -> Unit,
    onEditMetadata: () -> Unit,
    onResetPreview: () -> Unit,
    onSecurityAction: (SecurityAction) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(tonalElevation = 2.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Compact mode has short, properly localized labels. Wider screens
            // give the Russian “Просмотр” enough room rather than clipping it.
            val compactModePicker = maxWidth < 600.dp
            val wide = maxWidth >= 600.dp
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TextButton(
                        onClick = onEditMetadata,
                        modifier = Modifier.weight(1f),
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Text(
                            title,
                            modifier = Modifier.padding(start = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (wide) {
                        Box(Modifier.width(330.dp)) {
                            WorkspaceModePicker(
                                mode = mode,
                                onModeChange = onModeChange,
                                strings = strings,
                                compact = compactModePicker,
                            )
                        }
                    }
                    if (dualPane || mode == 1) {
                        IconButton(onClick = onResetPreview) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = strings.resetPreview,
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = strings.moreActions)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (encrypted) {
                                DropdownMenuItem(
                                    text = { Text(strings.changePassword) },
                                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                                    onClick = {
                                        menuExpanded = false
                                        onSecurityAction(SecurityAction.CHANGE)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.removePassword) },
                                    onClick = {
                                        menuExpanded = false
                                        onSecurityAction(SecurityAction.REMOVE)
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(strings.setPassword) },
                                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                                    onClick = {
                                        menuExpanded = false
                                        onSecurityAction(SecurityAction.SET)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(strings.exportFile) },
                                leadingIcon = {
                                    Icon(dev.pocket.notebook.core.ui.FileTransferIcons.Export, null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onExport()
                                },
                            )
                        }
                    }
                }
                if (!wide)
                    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        WorkspaceModePicker(mode, onModeChange, strings, true)
                    }
            }
        }
    }
}
