package dev.pocket.notebook.data.facade

import dev.pocket.notebook.data.*
import dev.pocket.notebook.data.catalog.LibraryCatalog
import dev.pocket.notebook.data.catalog.LibraryOperations
import dev.pocket.notebook.domain.LibraryCatalogAccess

internal class CatalogFacade(
    private val executor: BlockingRepositoryExecutor,
    private val catalog: LibraryCatalog,
    private val operations: LibraryOperations,
) : LibraryCatalogAccess {
    override fun children(parentId: String?): List<VfsItem> =
        executor.execute { catalog.children(parentId) }

    override fun folderPath(folderId: String?): List<VfsItem.Folder> =
        executor.execute { catalog.folderPath(folderId) }

    override fun allFolders(): List<VfsItem.Folder> = executor.execute { catalog.allFolders() }

    override fun createFolder(name: String, parentId: String?): VfsItem.Folder =
        executor.execute { catalog.createFolder(name, parentId) }

    override fun renameFolder(id: String, name: String) =
        executor.execute { catalog.renameFolder(id, name) }

    override fun move(id: String, targetId: String) =
        executor.execute { catalog.move(id, targetId) }

    override fun setSiblingOrder(parentId: String?, orderedIds: List<String>) =
        executor.execute { catalog.setSiblingOrder(parentId, orderedIds) }

    override fun moveToFolder(id: String, targetParent: String?) =
        executor.execute { operations.transfer(listOf(id), targetParent, true) }

    override fun delete(id: String) = executor.execute { operations.delete(listOf(id)) }

    override fun notebookIdsInBranches(ids: List<String>): Set<String> =
        executor.execute { operations.notebookIdsInBranches(ids) }

    override fun createCollection(name: String, parent: String?): VfsItem.Folder =
        executor.execute { catalog.createCollection(name, parent) }

    override fun convertContainer(id: String) = executor.execute { catalog.convertContainer(id) }

    override fun transfer(ids: List<String>, target: String?, cut: Boolean) =
        executor.execute { operations.transfer(ids, target, cut) }

    override fun deleteItems(ids: List<String>) = executor.execute { operations.delete(ids) }
}
