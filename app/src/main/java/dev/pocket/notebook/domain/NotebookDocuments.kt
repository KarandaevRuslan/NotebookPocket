package dev.pocket.notebook.domain

import dev.pocket.notebook.data.*

/**
 * Public NotebookDocuments contract. Calls serialize storage access; invoke IO work off the UI
 * thread.
 */
interface NotebookDocuments {
    fun list(): List<NotebookEntry>

    fun entry(id: String): NotebookEntry

    fun isUnlocked(id: String): Boolean

    fun isEncrypted(id: String): Boolean

    fun load(id: String): DocumentSnapshot

    fun read(id: String): String

    fun readCover(id: String): ByteArray?

    fun create(language: String, parentId: String? = null): NotebookEntry

    fun save(id: String, source: String): SaveOutcome

    fun updateMetadata(id: String, metadata: NotebookMetadata): NotebookEntry

    fun updateMetadataAndCover(
        id: String,
        metadata: NotebookMetadata,
        changeCover: Boolean,
        image: ByteArray?,
        mime: String?,
    ): NotebookEntry

    fun setCover(id: String, bytes: ByteArray?, mime: String?): NotebookEntry
}
