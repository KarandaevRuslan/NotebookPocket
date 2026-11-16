package dev.pocket.notebook.data.catalog

import dev.pocket.notebook.data.VfsItem
import dev.pocket.notebook.data.VfsTreeStore
import dev.pocket.notebook.data.collection.CollectionConversion
import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.document.DocumentContent
import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.storage.StorageAccess

internal class LibraryCatalog(
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val content: DocumentContent,
    private val treeStore: VfsTreeStore,
    private val conversion: CollectionConversion,
) {

    suspend fun children(parentId: String?): List<VfsItem> = run {
        val current = parentId?.let { storage.required(it) }
        if (current?.kind == "COLLECTION" && !collections.unlocked(current)) return@run emptyList()
        treeStore.children(parentId).map { node ->
            if (node.kind != "NOTEBOOK") folderEntry(node)
            else VfsItem.Document(content.toEntry(node))
        }
    }

    suspend fun folderPath(folderId: String?): List<VfsItem.Folder> = run {
        treeStore.folderPath(folderId).map { folderEntry(it) }
    }

    suspend fun allFolders(): List<VfsItem.Folder> = run {
        storage.dao.allFolders().map { folderEntry(it) }
    }

    suspend fun createFolder(name: String, parentId: String?): VfsItem.Folder = run {
        treeStore.createFolder(name, parentId).let { VfsItem.Folder(it.id, it.title, it.parentId) }
    }

    suspend fun renameFolder(id: String, name: String) {
        run { treeStore.renameFolder(id, name) }
    }

    suspend fun move(id: String, targetId: String) {
        run { treeStore.reorder(id, targetId) }
    }

    suspend fun setSiblingOrder(parentId: String?, orderedIds: List<String>) {
        run { treeStore.setOrder(parentId, orderedIds) }
    }

    private suspend fun folderEntry(n: VfsNodeEntity) =
        VfsItem.Folder(
            n.id,
            n.title,
            n.parentId,
            n.kind == "COLLECTION",
            n.encrypted,
            collections.unlocked(n),
        )

    suspend fun createCollection(name: String, parent: String?): VfsItem.Folder = run {
        val f = treeStore.createFolder(name, parent)
        conversion.convert(f.id)
        folderEntry(storage.required(f.id))
    }

    suspend fun convertContainer(id: String) {
        conversion.convert(id)
    }
}
