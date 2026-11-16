package dev.pocket.notebook.data.collection

import dev.pocket.notebook.data.NotebookLockedException
import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.packing.*
import dev.pocket.notebook.data.security.NotebookCrypto
import dev.pocket.notebook.data.storage.Revision
import dev.pocket.notebook.data.storage.StorageAccess

/** One collection file owns member document packages and shared resources. */
internal class CollectionStore(private val storage: StorageAccess) {
    private val memo = linkedMapOf<String, Pair<String, PortableBundle.Collection>>()

    fun invalidate(id: String) {
        memo.remove(id)
    }

    fun clear() {
        memo.clear()
    }

    fun unlocked(node: VfsNodeEntity): Boolean =
        !node.encrypted ||
            storage.keyCache.get(node.id)?.let {
                it.fill(0)
                true
            } == true

    fun load(node: VfsNodeEntity): PortableBundle.Collection {
        require(node.kind == "COLLECTION")
        if (!unlocked(node)) throw NotebookLockedException()
        memo[node.id]
            ?.takeIf { it.first == node.contentRef }
            ?.let {
                return it.second
            }
        val bytes = storage.files.read(requireNotNull(node.contentRef))
        val plain =
            if (node.encrypted) {
                val key = storage.keyCache.get(node.id) ?: throw NotebookLockedException()
                try {
                    PortableBundle.open(bytes, key, "collection")
                } finally {
                    key.fill(0)
                }
            } else bytes
        val value = PortableBundle.decode(plain)
        if (memo.size >= 4) memo.remove(memo.keys.first())
        memo[node.id] = node.contentRef!! to value
        return value
    }

    suspend fun store(
        node: VfsNodeEntity,
        value: PortableBundle.Collection,
        tx: Revision,
        encrypted: Boolean = node.encrypted,
        keyOverride: ByteArray? = null,
        saltOverride: ByteArray? = null,
    ) {
        var bytes = PortableBundle.encode(value)
        if (encrypted) {
            val key =
                keyOverride?.copyOf()
                    ?: storage.keyCache.get(node.id)
                    ?: throw NotebookLockedException()
            try {
                val h =
                    if (node.encrypted)
                        PortableBundle.header(storage.files.read(node.contentRef!!), "collection")
                    else null
                bytes =
                    PortableBundle.seal(
                        bytes,
                        key,
                        saltOverride ?: h!!.salt,
                        "collection",
                        if (saltOverride != null) NotebookCrypto.ITERATIONS
                        else h?.iterations ?: NotebookCrypto.ITERATIONS,
                    )
            } finally {
                key.fill(0)
            }
        }
        val ref = tx.write(node.id, bytes)
        storage.dao.update(
            node.copy(
                kind = "COLLECTION",
                contentRef = ref,
                packageFormat = true,
                encrypted = encrypted,
                updated = storage.now(),
            )
        )
        tx.remove(node.contentRef)
        memo.remove(node.id)
    }

    suspend fun parent(node: VfsNodeEntity): VfsNodeEntity? =
        node.parentId?.let { storage.dao.get(it) }?.takeIf { it.kind == "COLLECTION" }

    fun memberKey(node: VfsNodeEntity) =
        node.contentRef?.takeIf { it.startsWith("member:") }?.removePrefix("member:") ?: node.id

    suspend fun index(node: VfsNodeEntity) {
        val value = load(node)
        val present = storage.dao.children(node.id).map(::memberKey).toSet()
        value.members
            .filterKeys { it !in present }
            .forEach { (key, bytes) ->
                val packet = NotebookPackage.unpack(bytes)
                val m = packet.metadata
                storage.dao.insert(
                    VfsNodeEntity(
                        storage.newId(),
                        node.id,
                        "NOTEBOOK",
                        (storage.dao.lastPosition(node.id) ?: -1) + 1,
                        m.title,
                        m.description,
                        m.author,
                        m.version,
                        contentRef = "member:$key",
                        packageFormat = true,
                        encrypted = packet.protected,
                        hasCover = packet.cover != null,
                    )
                )
            }
    }
}
