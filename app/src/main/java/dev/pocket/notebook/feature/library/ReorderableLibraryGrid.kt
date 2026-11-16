package dev.pocket.notebook.feature.library

import dev.pocket.notebook.feature.files.actions.SelectionMark
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import dev.pocket.notebook.feature.files.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.data.VfsItem
import dev.pocket.notebook.i18n.AppStrings
import dev.pocket.notebook.core.ui.components.card.AppCardDefaults
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

/** Grid interaction only. It never accesses Room or the nav controller.
 * Reorder is local during a drag; a full sibling permutation is committed once on release.
 * Staggered columns do not reserve empty vertical space below shorter folder cards.
 * Reorderable handles item offsets, animations and edge auto-scrolling.
 */
@Composable
internal fun ReorderableLibraryGrid(
    folderId: String?,
    revision: Int,
    nodes: List<VfsItem>,
    searchActive: Boolean,
    gridState: LazyStaggeredGridState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    strings: AppStrings,
    locale: String,
    selectedId: String?,
    loadCover: (String) -> ByteArray?,
    onOpenFolder: (String) -> Unit,
    onOpenNotebook: (String) -> Unit,
    onRenameFolder: (String) -> Unit,
    onMoveFolder: (String) -> Unit,
    onEditMetadata: (String) -> Unit,
    onMoveNotebook: (String) -> Unit,
    onExport: (String) -> Unit,
    onDelete: (VfsItem) -> Unit,
    onSetOrder: (String?, List<String>) -> Unit
) {
    val tools=LocalPocketTools.current
    val displayed = remember(folderId) { mutableStateListOf<VfsItem>().apply { addAll(nodes) } }
    var dragging by remember(folderId) { mutableStateOf(false) }
    var dirty by remember(folderId) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // Search and Room revision changes must not move cards out from under the finger.
    LaunchedEffect(folderId, revision, nodes, searchActive) {
        if (!dragging) {
            displayed.clear()
            displayed.addAll(nodes)
        }
    }
    val reorderState = rememberReorderableLazyStaggeredGridState(gridState) { from, to ->
        if (!searchActive && from.index in displayed.indices && to.index in displayed.indices &&
            from.index != to.index) {
            displayed.add(to.index, displayed.removeAt(from.index))
            dirty = true
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Adaptive(minSize = 240.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp
    ) {
        items(displayed, key = { it.id }) { item ->
            ReorderableItem(reorderState, key = item.id, enabled = !searchActive) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "dragElevation")
                val dragModifier = if (!searchActive) {
                    with(this) {
                        Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                dragging = true
                                dirty = false
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                // Persist after movement completes, never on every hover event.
                                if (dirty) onSetOrder(folderId, displayed.map { it.id })
                                dirty = false
                                dragging = false
                            }
                        )
                    }
                } else Modifier
                Surface(modifier = Modifier.fillMaxWidth(), shape = AppCardDefaults.shape, shadowElevation = elevation) {
                    Column(dragModifier.fillMaxWidth()) {
                        SelectionMark(item.id)
                        when (item) {
                            is VfsItem.Folder -> FolderCard(
                                folder = item,
                                strings = strings,
                                onOpen = { if(tools.selected.isNotEmpty())tools.select(item.id) else if(!item.unlocked)tools.security(item.id,dev.pocket.notebook.data.security.SecurityAction.UNLOCK) else onOpenFolder(item.id) },
                                onRename = { onRenameFolder(item.id) },
                                onMove = { onMoveFolder(item.id) },
                                onDelete = { onDelete(item) }
                            )
                            is VfsItem.Document -> NotebookCard(
                                note = item.notebook,
                                coverBytes = remember(item.id, item.notebook.updated) {
                                    if (item.notebook.hasCover) loadCover(item.id) else null
                                },
                                strings = strings,
                                locale = locale,
                                selected = selectedId == item.id,
                                onOpen = { if(tools.selected.isNotEmpty())tools.select(it) else onOpenNotebook(it) },
                                onEditMetadata = onEditMetadata,
                                onMoveToFolder = onMoveNotebook,
                                onExport = onExport,
                                onDelete = { onDelete(VfsItem.Document(it)) }
                            )
                        }
                    }
                }
            }
        }
    }
}
