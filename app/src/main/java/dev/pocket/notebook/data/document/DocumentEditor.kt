package dev.pocket.notebook.data.document

import android.content.Context
import dev.pocket.notebook.data.NotebookEntry
import dev.pocket.notebook.data.NotebookLockedException
import dev.pocket.notebook.data.NotebookMetadata
import dev.pocket.notebook.data.NotebookMetadataCodec
import dev.pocket.notebook.data.VfsTreeStore
import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.draft.DraftStore
import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.packing.NotebookPackage
import dev.pocket.notebook.data.storage.StorageAccess
import dev.pocket.notebook.data.storage.revision
import dev.pocket.notebook.domain.DocumentSnapshot
import dev.pocket.notebook.domain.SaveOutcome

internal class DocumentEditor(
    private val context: Context,
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val content: DocumentContent,
    private val treeStore: VfsTreeStore,
    private val drafts: DraftStore,
) {
    /** Parents are directories, root is null; new items are placed before siblings. */
    suspend fun create(language: String, parentId: String? = null): NotebookEntry {
        return run {
            val source = dev.pocket.notebook.data.demo.PancakeDemo.source(context, language)
            val meta = NotebookMetadataCodec.read(source)
            val id = storage.newId()
            val bytes =
                NotebookPackage.pack(
                    source,
                    meta,
                    null,
                    null,
                    resources = dev.pocket.notebook.data.demo.PancakeDemo.resources(context),
                )
            val node =
                storage.revision().run { tx ->
                    val parent = parentId?.let { storage.required(it) }
                    require(parent == null || parent.kind in setOf("FOLDER", "COLLECTION"))
                    if (parent?.kind == "COLLECTION") collections.load(parent)
                    treeStore.shiftSiblings(parentId)
                    val ref =
                        if (parent?.kind == "COLLECTION") "member:$id" else tx.write(id, bytes)
                    VfsNodeEntity(
                            id,
                            parentId,
                            "NOTEBOOK",
                            0,
                            meta.title,
                            meta.description,
                            meta.author,
                            meta.version,
                            contentRef = ref,
                            packageFormat = true,
                        )
                        .also {
                            storage.dao.insert(it)
                            if (parent?.kind == "COLLECTION") {
                                val state = collections.load(parent)
                                collections.store(
                                    parent,
                                    state.copy(members = state.members + (id to bytes)),
                                    tx,
                                )
                            }
                        }
                }
            content.toEntry(node)
        }
    }

    private suspend fun saveNode(node: VfsNodeEntity, source: String): SaveOutcome {
        require(source.toByteArray(Charsets.UTF_8).size <= NotebookPackage.MAX_SOURCE)
        if (!content.toEntry(node).unlocked) throw NotebookLockedException()
        // Checkpoint first: invalid metadata and a failed ZIP/SQL commit must never lose an edit.
        drafts.write(node.id, source)
        // During Undo/Redo CodeMirror can transiently remove <title> or
        // <notebook>. Those drafts must still be saved instead of failing.
        val meta =
            NotebookMetadataCodec.forDraft(
                source,
                NotebookMetadata(node.title, node.description, node.author, node.version),
            )
        // The portable package format additionally validates that the manifest
        // metadata matches its HTML on every decrypt/import. Never write an
        // inconsistent encrypted/ZIP package during a temporary invalid edit.
        // Its encrypted checkpoint remains durable until the source becomes valid again.
        if (NotebookMetadataCodec.read(source) != meta) return SaveOutcome.DRAFT
        val packet = if (node.packageFormat) content.packageFrom(node) else null
        val bytes =
            content.encode(
                node,
                source,
                meta,
                packet?.cover ?: content.coverOf(node),
                packet?.coverMime,
            )
        content.persist(
            node,
            bytes,
            node.packageFormat,
            meta,
            content.coverOf(node),
            node.encrypted,
        )
        drafts.remove(node.id)
        return SaveOutcome.COMMITTED
    }

    suspend fun load(id: String): DocumentSnapshot {
        val node = storage.notebook(id)
        if (!content.toEntry(node).unlocked) throw NotebookLockedException()
        val draft = drafts.read(id)
        return DocumentSnapshot(draft ?: content.readNode(node), draft != null)
    }

    suspend fun save(id: String, source: String): SaveOutcome =
        saveNode(storage.notebook(id), source)

    suspend fun updateMetadata(id: String, metadata: NotebookMetadata): NotebookEntry =
        updateMetadataAndCover(id, metadata, false, null, null)

    suspend fun updateMetadataAndCover(
        id: String,
        metadata: NotebookMetadata,
        changeCover: Boolean,
        image: ByteArray?,
        mime: String?,
    ): NotebookEntry = run {
        if (drafts.exists(id)) throw dev.pocket.notebook.domain.PendingDraftException()
        NotebookMetadataCodec.validate(metadata)
        if (changeCover) NotebookPackage.validateCover(image, mime)
        val node = storage.notebook(id)
        val source = NotebookMetadataCodec.update(content.readNode(node), metadata)
        val packet = if (node.packageFormat) content.packageFrom(node) else null
        val oldCover = content.coverOf(node)
        val newCover = if (changeCover) image else oldCover
        val coverMime =
            if (changeCover) mime else packet?.coverMime ?: newCover?.let(content::detectMime)
        val bytes = content.encode(node, source, metadata, newCover, coverMime)
        content.toEntry(
            content.persist(node, bytes, node.packageFormat, metadata, newCover, node.encrypted)
        )
    }
}
