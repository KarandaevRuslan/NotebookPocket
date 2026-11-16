@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package dev.pocket.notebook.feature.files

import android.content.ClipData
import android.content.ClipboardManager
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.R
import dev.pocket.notebook.data.ResourceInfo
import dev.pocket.notebook.feature.files.components.ResourceBreadcrumbs
import dev.pocket.notebook.feature.files.components.ResourceRow
import dev.pocket.notebook.feature.files.components.ResourceScopeSelector
import dev.pocket.notebook.feature.files.components.ResourceRowAction
import dev.pocket.notebook.feature.files.components.ResourceToolbar
import dev.pocket.notebook.feature.files.components.ResourceToolbarAction
import dev.pocket.notebook.feature.files.dialogs.FileForm
import dev.pocket.notebook.feature.files.dialogs.ResourceDialogs
import dev.pocket.notebook.feature.files.dialogs.ResourceManagerDialogs
import dev.pocket.notebook.feature.files.state.ResourceNavigation
import dev.pocket.notebook.feature.files.state.ResourceOpen
import kotlinx.coroutines.*

@Composable
internal fun ResourceManager(initialOwner: String, embedded: Boolean = false, dismiss: () -> Unit) {
    val t = LocalPocketTools.current
    val dialogs = remember { ResourceDialogs() }
    val scope = rememberCoroutineScope()
    var owner by remember(initialOwner) { mutableStateOf(initialOwner) }
    val nav = remember(owner) { ResourceNavigation() }
    var entries by remember(owner) { mutableStateOf<List<ResourceInfo>>(emptyList()) }
    var loading by remember(owner) { mutableStateOf(true) }
    var failure by remember(owner) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var query by remember(owner, nav.path) { mutableStateOf("") }
    var sizeSort by remember { mutableStateOf(false) }
    var selected by remember(owner, nav.path) { mutableStateOf<Set<String>>(emptySet()) }
    val shared =
        remember(initialOwner, t.revision) {
            runCatching {
                    if (t.repo.allFolders().any { it.id == initialOwner && it.collection })
                        initialOwner
                    else t.repo.resourceOwner(initialOwner)
                }
                .getOrNull()
        }
    fun copy(value: String) {
        (t.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Notebook Pocket", value))
    }
    LaunchedEffect(owner, nav.path, t.revision, reload) {
        loading = true
        failure = null
        try {
            entries = withContext(Dispatchers.IO) { t.repo.resourceEntries(owner, nav.path) }
            selected = selected.intersect(entries.map { it.path }.toSet())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            entries = emptyList()
            failure = t.errorText(e)
        } finally {
            loading = false
        }
    }
    BackHandler(enabled = selected.isNotEmpty() || nav.path.isNotEmpty()) {
        if (selected.isNotEmpty()) selected = emptySet() else nav.up()
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            t.picker = false
            if (uri != null) {
                val name =
                    t.context.contentResolver
                        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "resource"
                dialogs.imported = uri to name.replace('/', '_').replace('\\', '_')
            }
        }
    fun visit(path: String) {
        val targetOwner = owner
        val epoch = t.lockEpoch
        scope.launch {
            try {
                val normalized = path.trim().removePrefix("/")
                val entry =
                    withContext(Dispatchers.IO) { t.repo.resourceEntry(targetOwner, normalized) }
                if (owner != targetOwner || epoch != t.lockEpoch) return@launch
                if (entry.directory) nav.go(normalized)
                else dialogs.opened = ResourceOpen(owner, normalized)
                dialogs.goPath = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                t.message(t.errorText(e))
            }
        }
    }
    val visible =
        entries
            .filter { it.path.substringAfterLast('/').contains(query, true) }
            .let { list ->
                if (sizeSort)
                    list.sortedWith(
                        compareByDescending<ResourceInfo> { it.directory }
                            .thenByDescending { it.size }
                            .thenBy { it.path.lowercase() }
                    )
                else list
            }
    val content: @Composable ColumnScope.() -> Unit = {
        if (shared != null && shared != initialOwner)
            ResourceScopeSelector(
                notebookLabel = t.text(R.string.fs_personal),
                sharedLabel = t.text(R.string.fs_shared),
                selectedShared = owner == shared,
                onSelectNotebook = { owner = initialOwner },
                onSelectShared = { owner = shared },
            )
        ResourceBreadcrumbs(nav) { dialogs.goPath = true }
        ResourceToolbar(
            selected.size,
            visible.isNotEmpty(),
            t.resourceClipboard != null,
            sizeSort,
            t::text,
        ) { action ->
            when (action) {
                ResourceToolbarAction.NEW_FILE -> dialogs.creation = false
                ResourceToolbarAction.NEW_FOLDER -> dialogs.creation = true
                ResourceToolbarAction.IMPORT -> {
                    t.picker = true
                    picker.launch(arrayOf("*/*"))
                }
                ResourceToolbarAction.SELECT_ALL -> selected = visible.map { it.path }.toSet()
                ResourceToolbarAction.COPY,
                ResourceToolbarAction.CUT -> {
                    t.resourceClipboard =
                        ResourceClipboard(
                            owner,
                            selected.toList(),
                            action == ResourceToolbarAction.CUT,
                        )
                    selected = emptySet()
                }
                ResourceToolbarAction.MOVE ->
                    dialogs.moving = ResourceClipboard(owner, selected.toList(), true)
                ResourceToolbarAction.DELETE -> dialogs.deleting = selected.toList()
                ResourceToolbarAction.CLEAR_SELECTION -> selected = emptySet()
                ResourceToolbarAction.PASTE ->
                    t.resourceClipboard?.let {
                        t.transferResource(ResourceTransfer(it, owner, nav.path))
                    }
                ResourceToolbarAction.CLEAR_CLIPBOARD -> t.resourceClipboard = null
                ResourceToolbarAction.REFRESH -> reload++
                ResourceToolbarAction.TOGGLE_SORT -> sizeSort = !sizeSort
            }
        }
        OutlinedTextField(
            query,
            { query = it },
            singleLine = true,
            placeholder = { Text(t.text(R.string.res_search)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().testTag("resource-search"),
        )
        when {
            loading ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            failure != null ->
                Column(Modifier.weight(1f).padding(16.dp)) {
                    Text(failure!!, color = MaterialTheme.colorScheme.error)
                    Row {
                        TextButton({ reload++ }) { Text(t.text(R.string.res_retry)) }
                        TextButton({ nav.go("") }) { Text(t.text(R.string.fs_root)) }
                    }
                }
            else ->
                LazyColumn(
                    Modifier.weight(1f).testTag("resource-files"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (visible.isEmpty())
                        item { Text(t.text(R.string.res_empty), Modifier.padding(16.dp)) }
                    items(visible, key = { it.path }) { entry ->
                        ResourceRow(
                            entry,
                            entry.path in selected,
                            selected.isNotEmpty(),
                            owner == shared,
                            t.language,
                            t::text,
                        ) { action ->
                            when (action) {
                                ResourceRowAction.OPEN ->
                                    if (entry.directory) nav.go(entry.path)
                                    else dialogs.opened = ResourceOpen(owner, entry.path)
                                ResourceRowAction.EDIT ->
                                    dialogs.opened = ResourceOpen(owner, entry.path, true)
                                ResourceRowAction.SELECT ->
                                    selected =
                                        if (entry.path in selected) selected - entry.path
                                        else selected + entry.path
                                ResourceRowAction.COPY_REFERENCE ->
                                    copy(resourceReference(entry.path, owner == shared))
                                ResourceRowAction.COPY_PATH -> copy(resourceDisplayPath(entry.path))
                                ResourceRowAction.COPY ->
                                    t.resourceClipboard =
                                        ResourceClipboard(owner, entry.path, false)
                                ResourceRowAction.CUT ->
                                    t.resourceClipboard = ResourceClipboard(owner, entry.path, true)
                                ResourceRowAction.MOVE ->
                                    dialogs.moving = ResourceClipboard(owner, entry.path, true)
                                ResourceRowAction.RENAME -> dialogs.rename = entry.path
                                ResourceRowAction.DELETE -> dialogs.deleting = listOf(entry.path)
                            }
                        }
                    }
                }
        }
    }
    if (embedded)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    else
        FileForm(
            t.text(if (owner == shared) R.string.fs_shared else R.string.fs_resources),
            dismiss,
            content,
        )
    ResourceManagerDialogs(owner, initialOwner, shared, nav, dialogs, ::visit) {
        selected = emptySet()
    }
}
