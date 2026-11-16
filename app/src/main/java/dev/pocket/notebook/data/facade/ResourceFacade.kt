package dev.pocket.notebook.data.facade

import dev.pocket.notebook.data.*
import dev.pocket.notebook.data.resource.ResourceAccess
import dev.pocket.notebook.domain.NotebookResourceAccess

internal class ResourceFacade(
    private val executor: BlockingRepositoryExecutor,
    private val resourceAccess: ResourceAccess,
) : NotebookResourceAccess {
    override fun resourceFiles(owner: String): List<ResourceInfo> =
        executor.execute { resourceAccess.resourceFiles(owner) }

    override fun resourceEntries(owner: String, directory: String): List<ResourceInfo> =
        executor.execute { resourceAccess.resourceEntries(owner, directory) }

    override fun resourceEntry(owner: String, path: String): ResourceInfo =
        executor.execute { resourceAccess.resourceEntry(owner, path) }

    override fun readResource(owner: String, path: String): ByteArray =
        executor.execute { resourceAccess.readResource(owner, path) }

    override fun putResource(owner: String, path: String, bytes: ByteArray) =
        executor.execute { resourceAccess.putResource(owner, path, bytes) }

    override fun createResourceFolder(owner: String, path: String) =
        executor.execute { resourceAccess.createResourceFolder(owner, path) }

    override fun replaceResource(
        owner: String,
        path: String,
        bytes: ByteArray,
        expectedHash: String,
    ) = executor.execute { resourceAccess.replaceResource(owner, path, bytes, expectedHash) }

    override fun deleteResource(owner: String, path: String) =
        executor.execute { resourceAccess.deleteResource(owner, path) }

    override fun deleteResources(owner: String, paths: List<String>) =
        executor.execute { resourceAccess.deleteResources(owner, paths) }

    override fun transferResources(
        source: String,
        path: String,
        target: String,
        destination: String,
        cut: Boolean,
    ) =
        executor.execute {
            resourceAccess.transferResources(source, path, target, destination, cut)
        }

    override fun transferResources(
        source: String,
        paths: List<String>,
        target: String,
        destination: String,
        cut: Boolean,
    ) =
        executor.execute {
            resourceAccess.transferResources(source, paths, target, destination, cut)
        }

    override fun renameResource(owner: String, path: String, newPath: String) =
        executor.execute { resourceAccess.renameResource(owner, path, newPath) }

    override fun ownerProtected(id: String): Boolean =
        executor.execute { resourceAccess.ownerProtected(id) }

    override fun resourceOwner(id: String): String? =
        executor.execute { resourceAccess.resourceOwner(id) }

    override fun attachmentNames(id: String): List<String> =
        executor.execute { resourceAccess.attachmentNames(id) }

    override fun attachment(id: String, path: String): ByteArray =
        executor.execute { resourceAccess.attachment(id, path) }
}
