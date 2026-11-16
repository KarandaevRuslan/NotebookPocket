package dev.pocket.notebook.data

/** Room holds folder structure, ordering, metadata, and references, not HTML/ZIP/image bytes. */
data class NotebookEntry(
    val id: String,
    val title: String,
    val updated: Long,
    val description: String = "",
    val author: String = "",
    val version: String = "",
    val encrypted: Boolean = false,
    val unlocked: Boolean = true,
    val hasCover: Boolean = false,
)

class NotebookLockedException : IllegalStateException("Document is locked")
