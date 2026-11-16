package dev.pocket.notebook.domain

import android.net.Uri
import dev.pocket.notebook.data.*

/**
 * Public NotebookTransfer contract. Calls serialize storage access; invoke IO work off the UI
 * thread.
 */
interface NotebookTransfer {
    fun importDocument(uri: Uri, parentId: String? = null): NotebookEntry

    fun exportDocument(id: String, uri: Uri)

    fun exportItems(ids: List<String>, uri: Uri, password: CharArray? = null)

    fun importAny(uri: Uri, parent: String?, password: CharArray? = null): List<String>
}
