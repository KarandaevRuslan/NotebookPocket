package dev.pocket.notebook.data

/** A mixed tree: folders and documents share the SAME sibling order. */
sealed interface VfsItem {
    val id: String
    val title: String

    data class Folder(
        override val id: String,
        override val title: String,
        val parentId: String?,
        val collection: Boolean = false,
        val encrypted: Boolean = false,
        val unlocked: Boolean = true,
    ) : VfsItem

    data class Document(val notebook: NotebookEntry) : VfsItem {
        override val id: String
            get() = notebook.id

        override val title: String
            get() = notebook.title
    }
}
