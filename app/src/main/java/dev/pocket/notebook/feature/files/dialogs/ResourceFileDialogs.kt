@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pocket.notebook.feature.files.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.R
import dev.pocket.notebook.data.ResourceInfo
import dev.pocket.notebook.data.ResourceTree
import dev.pocket.notebook.feature.files.LocalPocketTools
import dev.pocket.notebook.feature.files.PocketTools
import dev.pocket.notebook.feature.files.ResourceClipboard
import dev.pocket.notebook.feature.files.components.ResourceAction
import dev.pocket.notebook.feature.files.components.ResourceBreadcrumbs
import dev.pocket.notebook.feature.files.resourceChild
import dev.pocket.notebook.feature.files.resourceDisplayPath
import dev.pocket.notebook.feature.files.state.ResourceNavigation
import kotlinx.coroutines.*

internal fun copyResourceValue(t: PocketTools, value: String) {
    (t.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("Notebook Pocket", value))
}

@Composable
internal fun ResourcePathDialog(initial: String, dismiss: () -> Unit, visit: (String) -> Unit) {
    val t = LocalPocketTools.current
    var path by remember(initial) { mutableStateOf(initial) }
    FileForm(t.text(R.string.res_go_path), dismiss) {
        OutlinedTextField(
            path,
            { path = it.take(1025) },
            label = { Text(t.text(R.string.res_path)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("resource-path-input"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { visit(path) }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ visit(path) }) { Text(t.text(R.string.res_open)) }
            TextButton({ copyResourceValue(t, path) }) { Text(t.text(R.string.res_copy_path)) }
        }
    }
}

@Composable
internal fun ResourceDestinationDialog(
    initialOwner: String,
    shared: String?,
    initialTarget: String,
    initialDirectory: String,
    source: ResourceClipboard,
    dismiss: () -> Unit,
    choose: (String, String) -> Unit,
) {
    val t = LocalPocketTools.current
    val scope = rememberCoroutineScope()
    var owner by remember { mutableStateOf(initialTarget) }
    val nav =
        remember(owner) {
            ResourceNavigation().apply { if (owner == initialTarget) go(initialDirectory) }
        }
    var folders by remember(owner) { mutableStateOf<List<ResourceInfo>>(emptyList()) }
    var error by remember(owner) { mutableStateOf<String?>(null) }
    var loading by remember(owner) { mutableStateOf(true) }
    var create by remember { mutableStateOf(false) }
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(owner, nav.path, t.revision) {
        loading = true
        error = null
        try {
            folders =
                withContext(Dispatchers.IO) {
                    t.repo.resourceEntries(owner, nav.path).filter { it.directory }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = t.errorText(e)
        } finally {
            loading = false
        }
    }
    val allowed =
        owner != source.owner ||
            source.paths.none {
                ResourceTree.within(nav.path, it) || nav.path == it.substringBeforeLast('/', "")
            }
    FileForm(t.text(R.string.res_choose_destination), dismiss) {
        if (shared != null && shared != initialOwner)
            Row {
                FilterChip(
                    owner == initialOwner,
                    { owner = initialOwner },
                    label = { Text(t.text(R.string.fs_personal)) },
                )
                FilterChip(
                    owner == shared,
                    { owner = shared },
                    label = { Text(t.text(R.string.fs_shared)) },
                )
            }
        ResourceBreadcrumbs(nav) { go = true }
        Row {
            ResourceAction(R.string.res_new_folder, Icons.Default.CreateNewFolder) { create = true }
            Text(resourceDisplayPath(nav.path), Modifier.padding(12.dp))
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f)) {
            if (loading) item { CircularProgressIndicator() }
            items(folders, key = { it.path }) { folder ->
                ListItem(
                    headlineContent = { Text(folder.path.substringAfterLast('/')) },
                    leadingContent = { Icon(Icons.Default.Folder, null) },
                    modifier = Modifier.clickable { nav.go(folder.path) },
                )
            }
        }
        Button(
            { choose(owner, nav.path) },
            enabled = allowed && !loading && error == null,
            modifier = Modifier.fillMaxWidth().testTag("resource-move-here"),
        ) {
            Text(t.text(R.string.res_move_here))
        }
    }
    if (create)
        FileNameForm(t.text(R.string.res_new_folder), dismiss = { create = false }) { name ->
            val path = resourceChild(nav.path, name)
            t.perform({ t.repo.createResourceFolder(owner, path) }) {
                create = false
                nav.go(path)
            }
        }
    if (go)
        ResourcePathDialog(resourceDisplayPath(nav.path), { go = false }) { path ->
            val destination = path.trim().removePrefix("/")
            val targetOwner = owner
            val epoch = t.lockEpoch
            scope.launch {
                try {
                    val entry =
                        withContext(Dispatchers.IO) {
                            t.repo.resourceEntry(targetOwner, destination)
                        }
                    if (owner != targetOwner || epoch != t.lockEpoch) return@launch
                    require(entry.directory)
                    nav.go(destination)
                    go = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    t.message(t.errorText(e))
                }
            }
        }
}
