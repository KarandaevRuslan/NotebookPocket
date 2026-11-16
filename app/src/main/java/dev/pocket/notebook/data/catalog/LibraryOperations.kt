package dev.pocket.notebook.data.catalog

import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.document.DocumentContent
import dev.pocket.notebook.data.resource.NotebookResources
import dev.pocket.notebook.data.storage.StorageAccess
import dev.pocket.notebook.data.storage.revision

/** Mixed selections, ancestor normalization, cycles and file commits share one transaction. */
internal class LibraryOperations(
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val content: DocumentContent,
    private val resources: NotebookResources,
) {
    private val traversal = TreeTraversal(storage)
    private val branchTransfer = BranchTransfer(storage, collections, content, resources)

    suspend fun roots(ids: List<String>) = traversal.roots(ids)

    /** Uses the raw VFS tree: catalog.children hides locked collection members. */
    suspend fun notebookIdsInBranches(ids: List<String>): Set<String> =
        roots(ids).flatMap { traversal.branch(it) }
            .filter { it.kind == "NOTEBOOK" }
            .mapTo(mutableSetOf()) { it.id }

    suspend fun requireExportable(ids: List<String>) {
        for (root in roots(ids)) for (node in traversal.branch(root)) if (
            content.hasPendingDraft(node)
        )
            throw dev.pocket.notebook.domain.PendingDraftException()
    }

    suspend fun transfer(ids: List<String>, target: String?, cut: Boolean) =
        storage.revision().run { tx ->
            val items = roots(ids)
            val dest = target?.let { storage.required(it) }
            require(dest == null || dest.kind in setOf("FOLDER", "COLLECTION"))
            require(dest?.kind != "COLLECTION" || items.all { it.kind == "NOTEBOOK" }) {
                "Collections accept documents only"
            }
            if (dest?.kind == "COLLECTION") collections.load(dest)
            var cursor = target
            var depth = 0
            val seen = mutableSetOf<String>()
            while (cursor != null) {
                require(seen.add(cursor) && ++depth <= 100)
                require(items.none { it.id == cursor }) { "Cannot move into a descendant" }
                cursor = storage.required(cursor).parentId
            }
            items.forEach { n ->
                if (cut && n.parentId == target) return@forEach
                val subtree = traversal.branch(n)
                if (!cut)
                    for (node in subtree) if (content.hasPendingDraft(node))
                        throw dev.pocket.notebook.domain.PendingDraftException()
                require(subtree.size + storage.dao.count() <= 10000)
                val byId = subtree.associateBy { it.id }
                val height =
                    subtree.maxOf { child ->
                        var p: String? = child.id
                        var h = 0
                        while (p != null && p in byId) {
                            h++
                            p = byId.getValue(p).parentId
                        }
                        h
                    }
                require(depth + height <= 100) { "Tree depth exceeds limit" }
                if (n.kind == "NOTEBOOK") branchTransfer.insertDocument(n, target, cut, tx)
                else if (cut)
                    storage.dao.update(
                        n.copy(
                            parentId = target,
                            position = (storage.dao.lastPosition(target) ?: -1) + 1,
                        )
                    )
                else branchTransfer.copyBranch(n, target, 0, depth, tx)
            }
            (items.map { it.parentId } + target).distinct().forEach { p ->
                storage.dao.children(p).forEachIndexed { i, n -> storage.dao.setPosition(n.id, i) }
            }
        }

    suspend fun delete(ids: List<String>) =
        storage.revision().run { tx ->
            roots(ids).forEach { n ->
                val parent = collections.parent(n)
                if (parent != null) {
                    val state = collections.load(parent)
                    collections.store(
                        parent,
                        state.copy(members = state.members - collections.memberKey(n)),
                        tx,
                    )
                }
                traversal.branch(n).asReversed().forEach { child ->
                    storage.dao.delete(child.id)
                    tx.remove(child.contentRef)
                    tx.remove(child.coverRef)
                    storage.keyCache.forget(child.id)
                }
                storage.dao.children(n.parentId).forEachIndexed { i, child ->
                    storage.dao.setPosition(child.id, i)
                }
            }
        }
}
