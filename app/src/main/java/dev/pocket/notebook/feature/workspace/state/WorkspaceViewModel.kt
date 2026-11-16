package dev.pocket.notebook.feature.workspace.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dev.pocket.notebook.core.state.DocumentSaveState
import dev.pocket.notebook.core.state.DocumentWriteCoordinator
import dev.pocket.notebook.domain.SaveOutcome

/**
 * One ViewModel per notebook route, independent of orientation and WebView lifetime. Preview
 * (mode 1) is the default; the chosen mode survives rotation via SavedStateHandle. Source is
 * durable in the repository, not a Bundle.
 */
class WorkspaceViewModel(
    private val savedState: SavedStateHandle,
    initialSource: String,
    initialDraft: Boolean = false,
    initialSaveState: DocumentSaveState? = null,
) : ViewModel() {
    var mode by mutableIntStateOf(savedState["mode"] ?: 1)
        private set

    var source by mutableStateOf(initialSource)
        private set

    /** Incremented only for changes made outside CodeMirror (project metadata). */
    var externalSourceRevision by mutableIntStateOf(0)
        private set

    // Saved independently of the HTML source so orientation changes restore
    // the CodeMirror range and scroll position without recreating an undo stack.
    val selectionAnchor: Int
        get() = savedState["editor_selection_anchor"] ?: 0

    val selectionHead: Int
        get() = savedState["editor_selection_head"] ?: 0

    val editorScrollTop: Int
        get() = savedState["editor_scroll_top"] ?: 0

    /**
     * Ephemeral find/replace UI state. Kept only in the route-scoped ViewModel: search terms may
     * contain sensitive text from password-protected notebooks, and must not be written to
     * localStorage, Room or persistent preferences. This survives Activity rotation while the
     * workspace route remains alive.
     */
    var searchStateJson: String by mutableStateOf("")
        private set

    fun updateSearchState(json: String) {
        if (json.length <= 8192 && json != searchStateJson) searchStateJson = json
    }

    fun saveEditorViewport(anchor: Int, head: Int, scrollTop: Int) {
        if (selectionAnchor != anchor) savedState["editor_selection_anchor"] = anchor
        if (selectionHead != head) savedState["editor_selection_head"] = head
        if (editorScrollTop != scrollTop) savedState["editor_scroll_top"] = scrollTop
    }

    override fun onCleared() {
        source = ""
        searchStateJson = ""
        super.onCleared()
    }

    fun selectMode(value: Int) {
        mode = value.coerceIn(0, 2)
        savedState["mode"] = mode
    }

    var saveState: DocumentSaveState by
        mutableStateOf(
            initialSaveState?.takeUnless { it is DocumentSaveState.Idle }
                ?: DocumentSaveState.Saved(
                    0,
                    if (initialDraft) SaveOutcome.DRAFT else SaveOutcome.COMMITTED,
                )
        )
        private set

    private var editRevision = 0L

    fun acknowledge(state: DocumentSaveState) {
        if (state !is DocumentSaveState.Idle && state.revision >= editRevision) saveState = state
    }

    fun updateSource(value: String, writer: DocumentWriteCoordinator, id: String) {
        if (source == value) return
        source = value
        editRevision = writer.submit(id, value)
        saveState = DocumentSaveState.Saving(editRevision)
    }

    fun applyExternalSource(value: String, writer: DocumentWriteCoordinator, id: String) {
        if (source == value) return
        updateSource(value, writer, id)
        externalSourceRevision++
    }
}
