package dev.pocket.notebook.data.facade

import dev.pocket.notebook.data.*
import dev.pocket.notebook.data.document.DocumentContent
import dev.pocket.notebook.data.document.DocumentEditor
import dev.pocket.notebook.data.storage.StorageAccess
import dev.pocket.notebook.domain.NotebookDocuments

internal class DocumentFacade(
    private val executor: BlockingRepositoryExecutor,
    private val storage: StorageAccess,
    private val content: DocumentContent,
    private val editor: DocumentEditor,
) : NotebookDocuments {
    override fun list(): List<NotebookEntry> =
        executor.execute { storage.dao.allNotebooks().map { content.toEntry(it) } }

    override fun entry(id: String): NotebookEntry =
        executor.execute { content.toEntry(storage.notebook(id)) }

    override fun isUnlocked(id: String): Boolean =
        executor.execute { content.toEntry(storage.notebook(id)).unlocked }

    override fun isEncrypted(id: String): Boolean =
        executor.execute { content.toEntry(storage.notebook(id)).encrypted }

    override fun load(id: String): dev.pocket.notebook.domain.DocumentSnapshot =
        executor.execute { editor.load(id) }

    override fun read(id: String): String = load(id).source

    override fun readCover(id: String): ByteArray? =
        executor.execute { content.coverOf(storage.notebook(id)) }

    override fun create(language: String, parentId: String?): NotebookEntry =
        executor.execute { editor.create(language, parentId) }

    override fun save(id: String, source: String) = executor.execute { editor.save(id, source) }

    override fun updateMetadata(id: String, metadata: NotebookMetadata): NotebookEntry =
        executor.execute { editor.updateMetadata(id, metadata) }

    override fun updateMetadataAndCover(
        id: String,
        metadata: NotebookMetadata,
        changeCover: Boolean,
        image: ByteArray?,
        mime: String?,
    ): NotebookEntry =
        executor.execute { editor.updateMetadataAndCover(id, metadata, changeCover, image, mime) }

    override fun setCover(id: String, bytes: ByteArray?, mime: String?): NotebookEntry =
        executor.execute {
            val e = content.toEntry(storage.notebook(id))
            editor.updateMetadataAndCover(
                id,
                NotebookMetadata(e.title, e.description, e.author, e.version),
                true,
                bytes,
                mime,
            )
        }
}
