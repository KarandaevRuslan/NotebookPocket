package dev.pocket.notebook.data.resource

import dev.pocket.notebook.data.NotebookLockedException
import dev.pocket.notebook.data.NotebookMetadataCodec
import dev.pocket.notebook.data.ResourceTree
import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.document.DocumentContent
import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.packing.*
import dev.pocket.notebook.data.storage.Revision
import dev.pocket.notebook.data.storage.StorageAccess
import dev.pocket.notebook.data.storage.revision

/** Personal resources are inside the document package; shared resources inside its collection. */
internal class NotebookResources(
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val content: DocumentContent,
) {
    suspend fun read(n: VfsNodeEntity): Map<String, ByteArray> {
        if (n.kind == "COLLECTION") return collections.load(n).resources
        require(n.kind == "NOTEBOOK")
        if (!n.packageFormat) return emptyMap()
        val packet = content.packageFrom(n)
        val key =
            if (packet.protected) storage.keyCache.get(n.id) ?: throw NotebookLockedException()
            else null
        return try {
            NotebookPackage.contents(packet, key).second
        } finally {
            key?.fill(0)
        }
    }

    suspend fun pack(n: VfsNodeEntity, resources: Map<String, ByteArray>): ByteArray {
        val packet = if (n.packageFormat) content.packageFrom(n) else null
        val key =
            if (packet?.protected == true)
                storage.keyCache.get(n.id) ?: throw NotebookLockedException()
            else null
        return try {
            val source =
                if (packet != null) NotebookPackage.contents(packet, key).first
                else content.readNode(n)
            val meta = NotebookMetadataCodec.read(source)
            val cover = content.coverOf(n)
            NotebookPackage.pack(
                source,
                meta,
                cover,
                packet?.coverMime ?: cover?.let(content::detectMime),
                key,
                packet?.salt,
                packet?.iterations ?: dev.pocket.notebook.data.security.NotebookCrypto.ITERATIONS,
                resources,
            )
        } finally {
            key?.fill(0)
        }
    }

    suspend fun store(n: VfsNodeEntity, value: Map<String, ByteArray>, tx: Revision) {
        ArchiveFiles.resources(value)
        if (n.kind == "COLLECTION")
            collections.store(n, collections.load(n).copy(resources = value), tx)
        else {
            val bytes = pack(n, value)
            val packet = NotebookPackage.unpack(bytes)
            content.replace(n, bytes, true, packet.metadata, packet.cover, n.encrypted, tx)
        }
    }

    suspend fun tree(owner: String) = ResourceTree.decode(read(storage.required(owner)))

    suspend fun entries(owner: String, directory: String) = tree(owner).children(directory)

    suspend fun file(owner: String, path: String): ByteArray {
        ArchiveFiles.path(path)
        return requireNotNull(tree(owner).files[path]) { "Resource file not found" }.copyOf()
    }

    suspend fun create(owner: String, path: String, bytes: ByteArray?, folder: Boolean) {
        ResourceTree.newPath(path)
        if (!folder) require(bytes!!.size <= ArchiveFiles.MAX_FILE)
        storage.revision().run { tx ->
            val n = storage.required(owner)
            val old = ResourceTree.decode(read(n))
            val next = if (folder) old.addFolder(path) else old.addFile(path, bytes!!)
            store(n, next.encode(), tx)
        }
    }

    suspend fun replace(owner: String, path: String, bytes: ByteArray, expectedHash: String) {
        ResourceTree.newPath(path)
        require(bytes.size <= ArchiveFiles.MAX_FILE)
        storage.revision().run { tx ->
            val n = storage.required(owner)
            val old = ResourceTree.decode(read(n))
            val current = requireNotNull(old.files[path]) { "Resource changed or moved" }
            require(
                dev.pocket.notebook.data.security.NotebookCrypto.sha256(current) == expectedHash
            ) {
                "Resource changed or moved"
            }
            store(n, old.copy(files = old.files + (path to bytes)).encode(), tx)
        }
    }

    suspend fun delete(owner: String, paths: List<String>) =
        storage.revision().run { tx ->
            val n = storage.required(owner)
            store(n, ResourceTree.decode(read(n)).remove(paths).encode(), tx)
        }

    suspend fun rename(owner: String, path: String, target: String) =
        storage.revision().run { tx ->
            val n = storage.required(owner)
            store(n, ResourceTree.decode(read(n)).renamed(path, target).encode(), tx)
        }

    suspend fun transfer(
        source: String,
        paths: List<String>,
        target: String,
        destination: String,
        cut: Boolean,
    ) {
        storage.revision().run { tx ->
            val a = storage.required(source)
            val b = storage.required(target)
            val old = ResourceTree.decode(read(a))
            val dest = if (source == target) old else ResourceTree.decode(read(b))
            val (remaining, pasted) = old.paste(paths, dest, destination, source == target, cut)
            store(b, pasted.encode(), tx)
            if (source != target && cut) store(a, remaining.encode(), tx)
        }
    }

    suspend fun detached(n: VfsNodeEntity): ByteArray {
        val parent = collections.parent(n) ?: return content.portable(n)
        val shared = collections.load(parent).resources
        if (shared.isEmpty()) return content.portable(n)
        val personal = read(n)
        return pack(n, ResourceTree.detached(personal, shared))
    }
}
