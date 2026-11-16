@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.pocket.notebook.feature.files.actions

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.pocket.notebook.R
import dev.pocket.notebook.data.VfsItem
import dev.pocket.notebook.data.security.SecurityAction
import dev.pocket.notebook.feature.files.LocalPocketTools

@Composable
internal fun LibraryFileMenu(id: String, container: VfsItem.Folder?, dismiss: () -> Unit) {
    val t = LocalPocketTools.current
    fun action(block: () -> Unit) {
        dismiss()
        block()
    }
    DropdownMenuItem(
        text = { Text(t.text(R.string.fs_select)) },
        onClick = { action { t.select(id) } },
    )
    DropdownMenuItem(
        text = { Text(t.text(R.string.fs_copy)) },
        onClick = { action { t.copy(listOf(id), false) } },
    )
    DropdownMenuItem(
        text = { Text(t.text(R.string.fs_cut)) },
        onClick = { action { t.copy(listOf(id), true) } },
    )
    DropdownMenuItem(
        text = { Text(t.text(R.string.fs_export)) },
        onClick = { action { t.exportIds = listOf(id) } },
    )
    if (
        container == null && t.repo.isUnlocked(id) ||
            container?.collection == true && container.unlocked
    )
        DropdownMenuItem(
            text = { Text(t.text(R.string.fs_resources)) },
            onClick = { action { t.resourceOwner = id } },
        )
    if (container?.collection == true) {
        if (!container.unlocked)
            DropdownMenuItem(
                text = { Text(t.text(R.string.fs_unlock)) },
                onClick = { action { t.security(id, SecurityAction.UNLOCK) } },
            )
        else {
            DropdownMenuItem(
                text = {
                    Text(
                        t.text(
                            if (container.encrypted) R.string.fs_change_password
                            else R.string.fs_set_password
                        )
                    )
                },
                onClick = {
                    action {
                        t.security(
                            id,
                            if (container.encrypted) SecurityAction.CHANGE else SecurityAction.SET,
                        )
                    }
                },
            )
            if (container.encrypted)
                DropdownMenuItem(
                    text = { Text(t.text(R.string.fs_remove_password)) },
                    onClick = { action { t.security(id, SecurityAction.REMOVE) } },
                )
        }
    }
}
