@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.pocket.notebook.feature.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pocket.notebook.R
import dev.pocket.notebook.data.*
import dev.pocket.notebook.feature.files.dialogs.FileForm

@Composable
private fun PasswordFields(
    repeat: Boolean,
    value: String,
    onChange: (String) -> Unit,
    confirmation: String,
    onConfirmation: (String) -> Unit,
) {
    val t = LocalPocketTools.current
    Text(t.text(R.string.fs_password_help))
    OutlinedTextField(
        value,
        onChange,
        label = { Text(t.text(R.string.fs_password)) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (repeat)
        OutlinedTextField(
            confirmation,
            onConfirmation,
            label = { Text(t.text(R.string.fs_repeat_password)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
}

@Composable
internal fun PocketToolsHost() {
    val t = LocalPocketTools.current
    var stagedExport by remember { mutableStateOf<Pair<List<String>, CharArray?>?>(null) }
    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            t.picker = false
            val pending = stagedExport
            stagedExport = null
            if (pending != null) {
                if (uri != null)
                    t.perform({
                        try {
                            t.repo.exportItems(pending.first, uri, pending.second)
                        } finally {
                            pending.second?.fill('\u0000')
                        }
                    })
                else pending.second?.fill('\u0000')
            }
        }
    DisposableEffect(Unit) { onDispose { stagedExport?.second?.fill('\u0000') } }
    t.resourceOwner?.let { owner -> ResourceManager(owner) { t.resourceOwner = null } }
    t.exportIds?.let { ids ->
        var protect by remember(ids) { mutableStateOf(false) }
        var password by remember(ids) { mutableStateOf("") }
        var confirm by remember(ids) { mutableStateOf("") }
        FileForm(t.text(R.string.fs_export), { t.exportIds = null }) {
            Text(t.text(R.string.fs_export_help))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(protect, { protect = it })
                Text(t.text(R.string.fs_with_password))
            }
            if (protect)
                PasswordFields(true, password, { password = it }, confirm, { confirm = it })
            Button(
                {
                    stagedExport = ids to if (protect) password.toCharArray() else null
                    password = ""
                    confirm = ""
                    t.exportIds = null
                    t.picker = true
                    picker.launch("pocket-library.zip")
                },
                enabled = !protect || password.length >= 8 && password == confirm,
            ) {
                Text(t.text(R.string.fs_export))
            }
        }
    }
    t.importRequest?.let { request ->
        var password by remember(request) { mutableStateOf("") }
        FileForm(t.text(R.string.fs_archive_password), { t.importRequest = null }) {
            PasswordFields(false, password, { password = it }, "", {})
            Button(
                {
                    val key = password.toCharArray()
                    password = ""
                    t.import(request.first, request.second, key)
                },
                enabled = password.isNotEmpty() && !t.busy,
            ) {
                Text(t.text(R.string.fs_unlock))
            }
        }
    }
    t.destination?.let { ids ->
        val folders = t.repo.allFolders()
        val containerSelection = folders.any { it.id in ids }
        FileForm(t.text(R.string.fs_destination), { t.destination = null }) {
            LazyColumn(Modifier.weight(1f)) {
                item {
                    TextButton({ t.transfer(ids, null, true) }) { Text(t.text(R.string.fs_root)) }
                }
                items(
                    folders.filter { it.id !in ids && (!it.collection || !containerSelection) }
                ) { f ->
                    TextButton({ t.transfer(ids, f.id, true) }, enabled = f.unlocked) {
                        Text((if (f.collection) "▣ " else "") + folderPath(f.id, folders))
                    }
                }
            }
        }
    }
    t.confirmTransfer?.let { request ->
        AlertDialog(
            onDismissRequest = { t.confirmTransfer = null },
            title = { Text(t.text(R.string.fs_move)) },
            text = { Text(t.text(R.string.fs_declassify)) },
            confirmButton = {
                TextButton({ t.transfer(request.first, request.second, request.third, true) }) {
                    Text(t.text(R.string.fs_confirm))
                }
            },
            dismissButton = {
                TextButton({ t.confirmTransfer = null }) { Text(t.text(R.string.fs_cancel)) }
            },
        )
    }
    t.confirmResourceTransfer?.let { request ->
        AlertDialog(
            onDismissRequest = { t.confirmResourceTransfer = null },
            title = { Text(t.text(R.string.fs_paste)) },
            text = { Text(t.text(R.string.fs_resource_declassify)) },
            confirmButton = {
                TextButton({ t.transferResource(request, true) }) {
                    Text(t.text(R.string.fs_confirm))
                }
            },
            dismissButton = {
                TextButton({ t.confirmResourceTransfer = null }) {
                    Text(t.text(R.string.fs_cancel))
                }
            },
        )
    }
    if (t.busy)
        Dialog(
            {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Surface(shape = MaterialTheme.shapes.large) {
                Box(Modifier.padding(32.dp)) { CircularProgressIndicator() }
            }
        }
}

private fun folderPath(id: String, folders: List<VfsItem.Folder>): String {
    val names = mutableListOf<String>()
    var n = folders.firstOrNull { it.id == id }
    val seen = mutableSetOf<String>()
    while (n != null && seen.add(n.id)) {
        names.add(n.title)
        n = folders.firstOrNull { it.id == n!!.parentId }
    }
    return names.asReversed().joinToString(" / ")
}
