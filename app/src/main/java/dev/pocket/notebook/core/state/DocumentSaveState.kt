package dev.pocket.notebook.core.state

import dev.pocket.notebook.domain.SaveOutcome

sealed interface DocumentSaveState {
    val revision: Long

    data object Idle : DocumentSaveState {
        override val revision = 0L
    }

    data class Saving(override val revision: Long) : DocumentSaveState

    data class Saved(override val revision: Long, val outcome: SaveOutcome) : DocumentSaveState

    data class Failed(override val revision: Long) : DocumentSaveState

    /** Terminal acknowledgement for an edit intentionally abandoned with a deleted document. */
    data class Discarded(override val revision: Long) : DocumentSaveState
}
