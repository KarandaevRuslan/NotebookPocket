@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package dev.pocket.notebook.feature.files.dialogs

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.pocket.notebook.R
import dev.pocket.notebook.data.packing.ArchiveFiles
import dev.pocket.notebook.feature.files.LocalPocketTools
import dev.pocket.notebook.feature.files.ResourceClipboard
import dev.pocket.notebook.feature.files.ResourceTransfer
import dev.pocket.notebook.feature.files.resourceChild
import dev.pocket.notebook.feature.files.resourceDisplayPath
import dev.pocket.notebook.feature.files.state.ResourceNavigation
import dev.pocket.notebook.feature.files.state.ResourceOpen
import dev.pocket.notebook.feature.files.viewer.ResourceFileDialog
import kotlinx.coroutines.*

internal class ResourceDialogs {
    var creation by mutableStateOf<Boolean?>(null)
    var rename by mutableStateOf<String?>(null)
    var deleting by mutableStateOf<List<String>?>(null)
    var moving by mutableStateOf<ResourceClipboard?>(null)
    var opened by mutableStateOf<ResourceOpen?>(null)
    var goPath by mutableStateOf(false)
    var imported by mutableStateOf<Pair<Uri, String>?>(null)
}

@Composable
internal fun ResourceManagerDialogs(
    owner: String,
    initialOwner: String,
    shared: String?,
    nav: ResourceNavigation,
    dialogs: ResourceDialogs,
    visit: (String) -> Unit,
    clearSelection: () -> Unit,
) {
    val t = LocalPocketTools.current
    dialogs.creation?.let { folder ->
        FileNameForm(
            t.text(if (folder) R.string.res_new_folder else R.string.res_new_file),
            dismiss = { dialogs.creation = null },
        ) { name ->
            val target = resourceChild(nav.path, name)
            t.perform({
                if (folder) t.repo.createResourceFolder(owner, target)
                else t.repo.putResource(owner, target, byteArrayOf())
            }) {
                dialogs.creation = null
                if (folder) nav.go(target) else dialogs.opened = ResourceOpen(owner, target, true)
            }
        }
    }
    dialogs.imported?.let { (uri, name) ->
        FileNameForm(t.text(R.string.fs_resource_path), name, { dialogs.imported = null }) { chosen
            ->
            val path = resourceChild(nav.path, chosen)
            t.perform({
                val bytes =
                    requireNotNull(t.context.contentResolver.openInputStream(uri)).use {
                        ArchiveFiles.read(it, ArchiveFiles.MAX_FILE)
                    }
                t.repo.putResource(owner, path, bytes)
            }) {
                dialogs.imported = null
            }
        }
    }
    dialogs.rename?.let { path ->
        FileNameForm(
            t.text(R.string.fs_rename),
            path.substringAfterLast('/'),
            { dialogs.rename = null },
        ) { name ->
            t.perform({
                t.repo.renameResource(
                    owner,
                    path,
                    resourceChild(path.substringBeforeLast('/', ""), name),
                )
            }) {
                dialogs.rename = null
            }
        }
    }
    dialogs.deleting?.let { paths ->
        AlertDialog(
            onDismissRequest = { dialogs.deleting = null },
            title = { Text(t.text(R.string.fs_delete)) },
            text = { Text(t.text(R.string.res_confirm_delete)) },
            confirmButton = {
                TextButton({
                    t.perform({ t.repo.deleteResources(owner, paths) }) {
                        dialogs.deleting = null
                        clearSelection()
                    }
                }) {
                    Text(t.text(R.string.fs_delete))
                }
            },
            dismissButton = {
                TextButton({ dialogs.deleting = null }) { Text(t.text(R.string.fs_cancel)) }
            },
        )
    }
    if (dialogs.goPath)
        ResourcePathDialog(resourceDisplayPath(nav.path), { dialogs.goPath = false }, visit)
    dialogs.moving?.let { clip ->
        ResourceDestinationDialog(
            initialOwner,
            shared,
            owner,
            nav.path,
            clip,
            { dialogs.moving = null },
        ) { target, directory ->
            dialogs.moving = null
            t.transferResource(ResourceTransfer(clip, target, directory))
        }
    }
    dialogs.opened?.let { file -> ResourceFileDialog(file) { dialogs.opened = null } }
}
