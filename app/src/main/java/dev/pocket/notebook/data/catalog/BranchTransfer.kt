package dev.pocket.notebook.data.catalog

import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.document.DocumentContent
import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.resource.NotebookResources
import dev.pocket.notebook.data.storage.Revision
import dev.pocket.notebook.data.storage.StorageAccess

/** Transfers bytes and node references inside the caller-owned revision. */
internal class BranchTransfer(
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val content: DocumentContent,
    private val resources: NotebookResources,
) {
    suspend fun insertDocument(
        source: VfsNodeEntity,
        parent: String?,
        move: Boolean,
        tx: Revision,
    ) {
        val sourceParent = collections.parent(source)
        val bytes =
            if (sourceParent?.id == parent) content.portable(source) else resources.detached(source)
        val id = if (move) source.id else storage.newId()
        val destParent = parent?.let { storage.required(it) }?.takeIf { it.kind == "COLLECTION" }
        val key = storage.newId()
        val ref = if (destParent != null) "member:$key" else tx.write(id, bytes)
        val value =
            source.copy(
                id = id,
                parentId = parent,
                position = (storage.dao.lastPosition(parent) ?: -1) + 1,
                contentRef = ref,
                packageFormat = true,
                coverRef = null,
            )
        if (move) storage.dao.update(value) else storage.dao.insert(value)
        if (destParent != null) {
            val state = collections.load(destParent)
            collections.store(destParent, state.copy(members = state.members + (key to bytes)), tx)
        }
        if (move) {
            if (sourceParent != null) {
                val current = storage.required(sourceParent.id)
                val state = collections.load(current)
                collections.store(
                    current,
                    state.copy(members = state.members - collections.memberKey(source)),
                    tx,
                )
            } else tx.remove(source.contentRef)
            tx.remove(source.coverRef)
        }
    }

    suspend fun copyBranch(
        n: VfsNodeEntity,
        parent: String?,
        level: Int,
        depth: Int,
        tx: Revision,
    ) {
        require(depth + level <= 100)
        if (n.kind == "NOTEBOOK") {
            insertDocument(n, parent, false, tx)
            return
        }
        val id = storage.newId()
        val ref = n.contentRef?.let { tx.write(id, storage.files.read(it)) }
        storage.dao.insert(
            n.copy(
                id = id,
                parentId = parent,
                position = (storage.dao.lastPosition(parent) ?: -1) + 1,
                contentRef = ref,
            )
        )
        if (n.kind == "COLLECTION") {
            storage.dao.children(n.id).forEach { child ->
                storage.dao.insert(child.copy(id = storage.newId(), parentId = id))
            }
        } else storage.dao.children(n.id).forEach { copyBranch(it, id, level + 1, depth, tx) }
    }
}
