package dev.pocket.notebook.data.resource

import dev.pocket.notebook.data.ResourceInfo
import dev.pocket.notebook.data.ResourceTree
import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.storage.StorageAccess

internal class ResourceAccess(
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val resources: NotebookResources,
) {

    suspend fun resourceFiles(owner: String): List<ResourceInfo> = run {
        resources.tree(owner).files.map { ResourceInfo(it.key, it.value.size) }.sortedBy { it.path }
    }

    suspend fun resourceEntries(owner: String, directory: String = ""): List<ResourceInfo> = run {
        resources.entries(owner, directory)
    }

    suspend fun resourceEntry(owner: String, path: String): ResourceInfo = run {
        val tree = resources.tree(owner)
        require(tree.exists(path)) { "Resource not found" }
        ResourceInfo(path, tree.files[path]?.size ?: 0, path !in tree.files)
    }

    suspend fun readResource(owner: String, path: String): ByteArray = run {
        resources.file(owner, path)
    }

    suspend fun putResource(owner: String, path: String, bytes: ByteArray) = run {
        resources.create(owner, path, bytes, false)
    }

    suspend fun createResourceFolder(owner: String, path: String) = run {
        resources.create(owner, path, null, true)
    }

    suspend fun replaceResource(
        owner: String,
        path: String,
        bytes: ByteArray,
        expectedHash: String,
    ) = run { resources.replace(owner, path, bytes, expectedHash) }

    suspend fun deleteResource(owner: String, path: String) = deleteResources(owner, listOf(path))

    suspend fun deleteResources(owner: String, paths: List<String>) = run {
        resources.delete(owner, paths)
    }

    suspend fun transferResources(
        source: String,
        path: String,
        target: String,
        destination: String,
        cut: Boolean,
    ) = transferResources(source, listOf(path), target, destination, cut)

    suspend fun transferResources(
        source: String,
        paths: List<String>,
        target: String,
        destination: String,
        cut: Boolean,
    ) = run { resources.transfer(source, paths, target, destination, cut) }

    suspend fun renameResource(owner: String, path: String, newPath: String) = run {
        resources.rename(owner, path, newPath)
    }

    suspend fun ownerProtected(id: String): Boolean = run {
        val n = storage.required(id)
        n.encrypted || collections.parent(n)?.encrypted == true
    }

    suspend fun resourceOwner(id: String): String? = run {
        collections.parent(storage.notebook(id))?.id
    }

    suspend fun attachmentNames(id: String): List<String> = run {
        val n = storage.notebook(id)
        val shared =
            collections
                .parent(n)
                ?.let {
                    ResourceTree.decode(collections.load(it).resources).files.keys.map { p ->
                        "shared/$p"
                    }
                }
                .orEmpty()
        (ResourceTree.decode(resources.read(n)).files.keys + shared).distinct().sorted()
    }

    suspend fun attachment(id: String, path: String): ByteArray = run {
        dev.pocket.notebook.data.packing.ArchiveFiles.path(path)
        val n = storage.notebook(id)
        val personal = ResourceTree.decode(resources.read(n)).files
        personal[path]
            ?: if (path.startsWith("shared/")) {
                collections.parent(n)?.let {
                    ResourceTree.decode(collections.load(it).resources)
                        .files[path.removePrefix("shared/")]
                } ?: error("Resource not found")
            } else error("Resource not found")
    }
}
