package dev.pocket.notebook.data.catalog

import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.storage.StorageAccess

/** Selection normalization and bounded traversal shared by archive and library operations. */
internal class TreeTraversal(private val storage: StorageAccess) {
    suspend fun roots(ids: List<String>): List<VfsNodeEntity> {
        require(ids.isNotEmpty() && ids.size <= 5000)
        val selected = ids.distinct().toSet()
        return selected
            .map { storage.required(it) }
            .filter { n ->
                var cursor = n.parentId
                var covered = false
                val seen = mutableSetOf<String>()
                while (cursor != null) {
                    require(seen.add(cursor))
                    if (cursor in selected) covered = true
                    cursor = storage.required(cursor).parentId
                }
                !covered
            }
    }

    suspend fun branch(n: VfsNodeEntity): List<VfsNodeEntity> {
        val result = mutableListOf<VfsNodeEntity>()
        val seen = mutableSetOf<String>()
        suspend fun visit(node: VfsNodeEntity, depth: Int) {
            require(depth <= 100 && seen.add(node.id) && seen.size <= 5000)
            result.add(node)
            storage.dao.children(node.id).forEach { visit(it, depth + 1) }
        }
        visit(n, 0)
        return result
    }
}
