package dev.pocket.notebook.feature.library

import dev.pocket.notebook.feature.files.LocalPocketTools
import dev.pocket.notebook.feature.files.actions.LibraryFileBar
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.data.VfsItem
import dev.pocket.notebook.feature.library.state.LibraryViewModel
import dev.pocket.notebook.i18n.AppStrings

/**
 * Library screen: delegates sorting and gestures to ReorderableLibraryGrid; path and query are
 * owned by LibraryViewModel, not the short-lived NavHost destination.
 */
@Composable
fun NotebookLibraryScreen(
    state: LibraryViewModel,
    strings: AppStrings,
    locale: String,
    revision: Int,
    children: (String?) -> List<VfsItem>,
    path: (String?) -> List<VfsItem.Folder>,
    allFolders: () -> List<VfsItem.Folder>,
    selectedId: String?,
    loadCover: (String) -> ByteArray?,
    onOpen: (String) -> Unit,
    onCreate: (String?) -> Unit,
    onCreateFolder: (String, String?) -> Unit,
    onImport: (String?) -> Unit,
    onExport: (String) -> Unit,
    onDelete: (VfsItem) -> Unit,
    onSetOrder: (String?, List<String>) -> Unit,
    onMoveToFolder: (String, String?) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onEditMetadata: (String) -> Unit,
) {
    val tools = dev.pocket.notebook.feature.files.LocalPocketTools.current
    val folderId = state.folderId
    val toolRevision = tools.revision
    val query = state.query
    var editingFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingFolder by rememberSaveable { mutableStateOf(false) }
    var movingId by rememberSaveable { mutableStateOf<String?>(null) }
    val all =
        remember(folderId, revision, toolRevision) {
            runCatching { children(folderId) }.getOrNull().orEmpty()
        }
    val breadcrumbs =
        remember(folderId, revision, toolRevision) {
            runCatching { path(folderId) }.getOrNull().orEmpty()
        }

    LaunchedEffect(folderId, revision) {
        if (folderId != null && breadcrumbs.lastOrNull()?.id != folderId) {
            state.openFolder(null) // Folder was removed from another screen.
        }
    }
    BackHandler(folderId != null) { state.openFolder(breadcrumbs.lastOrNull()?.parentId) }
    val filtered =
        remember(all, query) {
            all.filter { item ->
                if (query.isBlank()) true
                else
                    when (item) {
                        is VfsItem.Folder -> item.title.contains(query, ignoreCase = true)
                        is VfsItem.Document ->
                            item.notebook.let { entry ->
                                entry.title.contains(query, true) ||
                                    entry.description.contains(query, true) ||
                                    entry.author.contains(query, true) ||
                                    entry.version.contains(query, true)
                            }
                    }
            }
        }
    val gridState =
        rememberSaveable(folderId, saver = LazyStaggeredGridState.Saver) {
            LazyStaggeredGridState()
        }
    val breadcrumbScroll = rememberScrollState()
    LaunchedEffect(folderId, breadcrumbScroll.maxValue) {
        breadcrumbScroll.animateScrollTo(breadcrumbScroll.maxValue)
    }
    val fabBottomPadding = 24.dp

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(breadcrumbScroll)
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    strings.rootFolder,
                    color =
                        if (folderId == null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { state.openFolder(null) },
                )
                breadcrumbs.forEach { folder ->
                    Text("›", color = MaterialTheme.colorScheme.outline)
                    Text(
                        folder.title,
                        color =
                            if (folder.id == folderId) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { state.openFolder(folder.id) },
                    )
                }
            }
            dev.pocket.notebook.feature.files.actions.LibraryFileBar(
                breadcrumbs.lastOrNull(),
                all,
                onCreate = { onCreate(folderId) },
                onImport = { onImport(folderId) },
                onCreateFolder = { creatingFolder = true },
            )
            LibrarySearchField(query, state::updateQuery, strings)
            val current = breadcrumbs.lastOrNull()
            if (current?.unlocked == false) {
                Text(tools.text(dev.pocket.notebook.R.string.fs_locked), Modifier.padding(16.dp))
            } else if (filtered.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        if (query.isBlank()) strings.folderEmpty else strings.noResults,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (query.isNotBlank()) Text(strings.noResultsSubtitle)
                }
            } else {
                ReorderableLibraryGrid(
                    folderId = folderId,
                    revision = revision,
                    nodes = filtered,
                    searchActive = query.isNotBlank(),
                    gridState = gridState,
                    bottomPadding = fabBottomPadding,
                    strings = strings,
                    locale = locale,
                    selectedId = selectedId,
                    loadCover = loadCover,
                    onOpenFolder = state::openFolder,
                    onOpenNotebook = onOpen,
                    onRenameFolder = { editingFolder = it },
                    onMoveFolder = { movingId = it },
                    onEditMetadata = onEditMetadata,
                    onMoveNotebook = { movingId = it },
                    onExport = onExport,
                    onDelete = onDelete,
                    onSetOrder = onSetOrder,
                )
            }
        }
    }
    if (creatingFolder)
        FolderNameDialog(strings, null, { creatingFolder = false }) { name ->
            onCreateFolder(name, folderId)
            creatingFolder = false
        }
    editingFolder?.let { id ->
        val folder = allFolders().firstOrNull { it.id == id }
        if (folder != null)
            FolderNameDialog(strings, folder.title, { editingFolder = null }) { name ->
                onRenameFolder(id, name)
                editingFolder = null
            }
        else LaunchedEffect(id) { editingFolder = null }
    }
    movingId?.let { id ->
        val item = all.firstOrNull { it.id == id }
        FolderMoveDialog(
            strings = strings,
            folders = allFolders(),
            itemId = id,
            currentParent = if (item is VfsItem.Folder) item.parentId else folderId,
            onDismiss = { movingId = null },
            onConfirm = { newParent ->
                onMoveToFolder(id, newParent)
                movingId = null
            },
        )
    }
}
