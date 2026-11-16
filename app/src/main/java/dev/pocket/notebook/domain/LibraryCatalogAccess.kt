package dev.pocket.notebook.domain

import dev.pocket.notebook.data.*

/**
 * Public LibraryCatalogAccess contract. Calls serialize storage access; invoke IO work off the UI
 * thread.
 */
interface LibraryCatalogAccess {
    fun children(parentId: String?): List<VfsItem>

    fun folderPath(folderId: String?): List<VfsItem.Folder>

    fun allFolders(): List<VfsItem.Folder>

    fun createFolder(name: String, parentId: String?): VfsItem.Folder

    fun renameFolder(id: String, name: String)

    fun move(id: String, targetId: String)

    fun setSiblingOrder(parentId: String?, orderedIds: List<String>)

    fun moveToFolder(id: String, targetParent: String?)

    fun delete(id: String)

    /** IDs of all notebooks affected by removing the given roots, including locked collections. */
    fun notebookIdsInBranches(ids: List<String>): Set<String>

    fun createCollection(name: String, parent: String?): VfsItem.Folder

    fun convertContainer(id: String)

    fun transfer(ids: List<String>, target: String?, cut: Boolean)

    fun deleteItems(ids: List<String>)
}
