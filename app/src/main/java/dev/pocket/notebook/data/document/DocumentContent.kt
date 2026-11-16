package dev.pocket.notebook.data.document

import dev.pocket.notebook.data.CoverImages
import dev.pocket.notebook.data.NotebookEntry
import dev.pocket.notebook.data.NotebookLockedException
import dev.pocket.notebook.data.NotebookMetadata
import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.packing.NotebookPackage
import dev.pocket.notebook.data.storage.StorageAccess
import dev.pocket.notebook.data.storage.revision

internal class DocumentContent(
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val drafts: dev.pocket.notebook.data.draft.DraftStore,
) {
    internal suspend fun payload(node: VfsNodeEntity): ByteArray {
        val parent = collections.parent(node)
        return if (parent != null)
            collections.load(parent).members.getValue(collections.memberKey(node))
        else storage.files.read(requireNotNull(node.contentRef))
    }

    suspend fun hasPendingDraft(node: VfsNodeEntity): Boolean =
        node.kind == "NOTEBOOK" && drafts.exists(node.id) && drafts.read(node.id) != readNode(node)

    internal suspend fun portable(node: VfsNodeEntity): ByteArray =
        if (node.packageFormat) payload(node)
        else
            NotebookPackage.pack(
                readNode(node),
                NotebookMetadata(node.title, node.description, node.author, node.version),
                coverOf(node),
                coverOf(node)?.let(::detectMime),
            )

    internal suspend fun packageFrom(node: VfsNodeEntity) = NotebookPackage.unpack(payload(node))

    suspend fun toEntry(node: VfsNodeEntity): NotebookEntry {
        // Listing cards never loads or decrypts document HTML/ZIP. Room holds public metadata.
        val parent = collections.parent(node)
        val unlocked =
            (parent == null || collections.unlocked(parent)) &&
                (!node.encrypted ||
                    (storage.keyCache.get(node.id)?.let { key ->
                        try {
                            true
                        } finally {
                            key.fill(0)
                        }
                    } ?: false))
        return NotebookEntry(
            node.id,
            node.title,
            node.updated,
            node.description,
            node.author,
            node.version,
            node.encrypted || parent?.encrypted == true,
            unlocked,
            node.hasCover,
        )
    }

    internal suspend fun readNode(node: VfsNodeEntity): String {
        val bytes = payload(node)
        if (!node.packageFormat) return bytes.toString(Charsets.UTF_8)
        val packet = NotebookPackage.unpack(bytes)
        if (!packet.protected) return NotebookPackage.contents(packet).first
        val key = storage.keyCache.get(node.id) ?: throw NotebookLockedException()
        return try {
            NotebookPackage.decrypt(packet, key)
        } finally {
            key.fill(0)
        }
    }

    internal suspend fun coverOf(node: VfsNodeEntity): ByteArray? =
        if (node.packageFormat) {
            packageFrom(node).cover
        } else node.coverRef?.let(storage.files::read)

    /** Repack an existing document without dropping resources or changing encryption parameters. */
    suspend fun encode(
        node: VfsNodeEntity,
        source: String,
        metadata: NotebookMetadata,
        cover: ByteArray?,
        coverMime: String?,
    ): ByteArray {
        if (!node.packageFormat) return source.toByteArray(Charsets.UTF_8)
        val packet = packageFrom(node)
        val key =
            if (packet.protected) storage.keyCache.get(node.id) ?: throw NotebookLockedException()
            else null
        return try {
            NotebookPackage.pack(
                source,
                metadata,
                cover,
                coverMime,
                key,
                packet.salt,
                packet.iterations,
                NotebookPackage.contents(packet, key).second,
            )
        } finally {
            key?.fill(0)
        }
    }

    /** Switch references atomically in Room, then delete the superseded revision(s). */
    suspend fun persist(
        old: VfsNodeEntity,
        bytes: ByteArray,
        packaged: Boolean,
        meta: NotebookMetadata,
        cover: ByteArray? = null,
        encrypted: Boolean = false,
    ): VfsNodeEntity {
        return storage.revision().run { tx ->
            replace(old, bytes, packaged, meta, cover, encrypted, tx)
        }
    }

    /** Shared write path for document edits and resource mutations in an existing transaction. */
    suspend fun replace(
        old: VfsNodeEntity,
        bytes: ByteArray,
        packaged: Boolean,
        meta: NotebookMetadata,
        cover: ByteArray?,
        encrypted: Boolean,
        tx: dev.pocket.notebook.data.storage.Revision,
    ): VfsNodeEntity {
        val parent = collections.parent(old)
        val newPayload =
            if (parent == null) tx.write(old.id, bytes, if (packaged) "npkg" else "html")
            else old.contentRef!!
        var newCover: String? = null
        if (!packaged && cover != null) newCover = tx.write(old.id, cover, "cover")
        val updated =
            old.copy(
                contentRef = newPayload,
                packageFormat = packaged,
                encrypted = encrypted,
                coverRef = newCover,
                hasCover = cover != null,
                title = meta.title,
                description = meta.description,
                author = meta.author,
                version = meta.version,
                updated = storage.now(),
            )
        if (parent != null) {
            require(packaged)
            val state = collections.load(parent)
            collections.store(
                parent,
                state.copy(members = state.members + (collections.memberKey(old) to bytes)),
                tx,
            )
        } else tx.remove(old.contentRef)
        storage.dao.update(updated)
        tx.remove(old.coverRef)
        return updated
    }

    internal fun detectMime(bytes: ByteArray): String = CoverImages.mime(bytes)
}
