package dev.pocket.notebook.data

import androidx.room.withTransaction
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.data.local.VfsDao
import dev.pocket.notebook.data.local.VfsNodeEntity
import java.util.UUID

/** Structural operations, separate from document content/encryption. */
internal class VfsTreeStore(private val db: NotebookDatabase, private val dao: VfsDao) {
    suspend fun requireFolder(id: String?) {
        if (id != null)
            require(dao.get(id)?.kind == "FOLDER") { "Parent must be an existing folder" }
    }

    suspend fun requireContainer(id: String?) {
        if (id != null) require(dao.get(id)?.kind in setOf("FOLDER", "COLLECTION"))
    }

    suspend fun shiftSiblings(parentId: String?) {
        dao.shiftPositions(parentId)
    }

    suspend fun children(parentId: String?): List<VfsNodeEntity> {
        requireContainer(parentId)
        return dao.children(parentId)
    }

    suspend fun folderPath(id: String?): List<VfsNodeEntity> {
        val path = mutableListOf<VfsNodeEntity>()
        var cursor = id
        val seen = mutableSetOf<String>()
        while (cursor != null) {
            require(seen.add(cursor) && seen.size <= 100) { "Corrupt folder hierarchy" }
            val folder = dao.get(cursor) ?: error("Folder not found")
            require(folder.kind in setOf("FOLDER", "COLLECTION"))
            path.add(folder)
            cursor = folder.parentId
        }
        return path.asReversed()
    }

    suspend fun createFolder(name: String, parentId: String?): VfsNodeEntity =
        db.withTransaction {
            val title = name.trim()
            require(title.isNotEmpty() && title.length <= 120)
            requireFolder(parentId)
            require(folderPath(parentId).size < 100 && dao.count() < 10000)
            shiftSiblings(parentId)
            VfsNodeEntity(UUID.randomUUID().toString(), parentId, "FOLDER", 0, title).also {
                dao.insert(it)
            }
        }

    suspend fun renameFolder(id: String, title: String) {
        val clean = title.trim()
        require(clean.isNotEmpty() && clean.length <= 120)
        val folder = dao.get(id) ?: error("Folder not found")
        require(folder.kind in setOf("FOLDER", "COLLECTION"))
        dao.update(folder.copy(title = clean, updated = System.currentTimeMillis()))
    }

    private suspend fun compact(parent: String?) {
        dao.children(parent).forEachIndexed { i, node ->
            if (node.position != i) dao.setPosition(node.id, i)
        }
    }

    /**
     * Persist one complete sibling order in a single transaction. Reject stale, incomplete,
     * duplicated, or cross-folder input instead of corrupting the tree.
     */
    suspend fun setOrder(parentId: String?, orderedIds: List<String>) =
        db.withTransaction {
            requireContainer(parentId)
            val actual = dao.children(parentId)
            require(orderedIds.size == actual.size && orderedIds.toSet().size == orderedIds.size) {
                "Order must list each sibling exactly once"
            }
            require(orderedIds.toSet() == actual.map { it.id }.toSet()) {
                "Order contains missing or foreign nodes"
            }
            orderedIds.forEachIndexed { index, id ->
                if (actual.first { it.id == id }.position != index) dao.setPosition(id, index)
            }
        }

    /**
     * Legacy two-id operation: destination is an index, not always insert-before. For example [A,
     * B] + move(A, B) must become [B, A].
     */
    suspend fun reorder(id: String, targetId: String) =
        db.withTransaction {
            if (id == targetId) return@withTransaction
            val source = dao.get(id) ?: error("Node not found")
            val target = dao.get(targetId) ?: error("Target not found")
            require(source.parentId == target.parentId) { "Cannot reorder across folders" }
            val current = dao.children(source.parentId)
            val ordered = NotebookOrder.moved(current.map { it.id }, id, targetId)
            ordered.forEachIndexed { index, nodeId ->
                if (current.first { it.id == nodeId }.position != index)
                    dao.setPosition(nodeId, index)
            }
        }

    suspend fun reparent(id: String, targetParent: String?) =
        db.withTransaction {
            val node = dao.get(id) ?: error("Node not found")
            requireFolder(targetParent)
            var cursor = targetParent
            val seen = mutableSetOf<String>()
            while (cursor != null) {
                require(cursor != id && seen.add(cursor)) { "Folder cycle" }
                cursor = dao.get(cursor)?.parentId
            }
            if (node.parentId == targetParent) return@withTransaction
            val previous = node.parentId
            dao.setParentAndPosition(
                node.id,
                targetParent,
                (dao.lastPosition(targetParent) ?: -1) + 1,
            )
            compact(previous)
        }

    /** Returns removed document IDs for Keystore cache cleanup after successful commit. */
    suspend fun deleteRecursively(id: String): List<String> =
        db.withTransaction {
            val deletedDocs = mutableListOf<String>()
            val visited = mutableSetOf<String>()
            suspend fun deleteNode(current: VfsNodeEntity) {
                require(visited.add(current.id)) { "Folder cycle" }
                for (child in dao.children(current.id)) deleteNode(child)
                dao.delete(current.id)
                if (current.kind == "NOTEBOOK") deletedDocs.add(current.id)
            }
            val start = dao.get(id) ?: error("Node not found")
            deleteNode(start)
            compact(start.parentId)
            deletedDocs
        }
}
