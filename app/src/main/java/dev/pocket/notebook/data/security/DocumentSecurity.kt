package dev.pocket.notebook.data.security

import dev.pocket.notebook.data.*

import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.document.DocumentContent
import dev.pocket.notebook.data.packing.NotebookPackage
import dev.pocket.notebook.data.resource.NotebookResources
import dev.pocket.notebook.data.security.NotebookCrypto
import dev.pocket.notebook.data.storage.StorageAccess
import dev.pocket.notebook.data.storage.revision

internal class DocumentSecurity(
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val content: DocumentContent,
    private val resources: NotebookResources,
) {

    suspend fun securityOwner(id: String): String = run {
        val node = storage.required(id)
        val parent = collections.parent(node)
        if (parent?.encrypted == true && (!collections.unlocked(parent) || !node.encrypted))
            parent.id
        else id
    }

    suspend fun setPassword(id: String, password: CharArray) {
        try {
            setPasswordInternal(id, password)
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun unlock(id: String, password: CharArray) {
        try {
            unlockInternal(id, password)
        } finally {
            password.fill('\u0000')
        }
    }

    private suspend fun setPasswordInternal(requestedId: String, password: CharArray) {
        val id = securityOwner(requestedId)
        if (storage.required(id).kind == "COLLECTION") {
            try {
                require(password.size >= SecurityPolicy.MIN_PASSWORD_LENGTH)
                run {
                    val node = storage.required(id)
                    val value = collections.load(node)
                    val salt = NotebookCrypto.randomBytes(NotebookCrypto.SALT_LENGTH)
                    val key = NotebookCrypto.derive(password, salt)
                    try {
                        storage.revision().run { tx ->
                            collections.store(node, value, tx, true, key, salt)
                        }
                        storage.keyCache.put(id, key)
                    } finally {
                        key.fill(0)
                    }
                }
            } finally {
                password.fill('\u0000')
            }
            return
        }
        require(password.size >= SecurityPolicy.MIN_PASSWORD_LENGTH) {
            "Password must contain at least 8 characters"
        }
        run {
            val node = storage.notebook(id)
            val source = content.readNode(node)
            val meta = NotebookMetadataCodec.read(source)
            val image = content.coverOf(node)
            val salt = NotebookCrypto.randomBytes(NotebookCrypto.SALT_LENGTH)
            val key = NotebookCrypto.derive(password, salt)
            try {
                val encrypted =
                    NotebookPackage.pack(
                        source,
                        meta,
                        image,
                        image?.let(content::detectMime),
                        key,
                        salt,
                        resources = resources.read(node),
                    )
                content.persist(node, encrypted, true, meta, image, encrypted = true)
                storage.keyCache.put(id, key)
            } finally {
                key.fill(0)
            }
        }
    }

    suspend fun removePassword(requestedId: String) {
        val id = securityOwner(requestedId)
        if (storage.required(id).kind == "COLLECTION") {
            run {
                val node = storage.required(id)
                val value = collections.load(node)
                storage.revision().run { tx -> collections.store(node, value, tx, false) }
                storage.keyCache.forget(id)
            }
            return
        }
        run {
            val node = storage.notebook(id)
            require(node.packageFormat && content.packageFrom(node).protected)
            val source = content.readNode(node)
            val image = content.coverOf(node)
            val meta = NotebookMetadataCodec.read(source)
            content.persist(
                node,
                NotebookPackage.pack(
                    source,
                    meta,
                    image,
                    image?.let(content::detectMime),
                    resources = resources.read(node),
                ),
                true,
                meta,
                image,
                encrypted = false,
            )
            storage.keyCache.forget(id)
        }
    }

    private suspend fun unlockInternal(requestedId: String, password: CharArray) {
        val id = securityOwner(requestedId)
        if (storage.required(id).kind == "COLLECTION") {
            try {
                run {
                    val node = storage.required(id)
                    val bytes = storage.files.read(node.contentRef!!)
                    val h =
                        dev.pocket.notebook.data.packing.PortableBundle.header(bytes, "collection")
                    val key = NotebookCrypto.derive(password, h.salt, h.iterations)
                    try {
                        dev.pocket.notebook.data.packing.PortableBundle.decode(
                            dev.pocket.notebook.data.packing.PortableBundle.open(
                                bytes,
                                key,
                                "collection",
                            )
                        )
                        storage.keyCache.put(id, key)
                        try {
                            storage.revision().run { collections.index(node) }
                        } catch (t: Throwable) {
                            storage.keyCache.forget(id)
                            throw t
                        }
                    } finally {
                        key.fill(0)
                    }
                }
            } finally {
                password.fill('\u0000')
            }
            return
        }
        run {
            val packet = content.packageFrom(storage.notebook(id))
            require(packet.protected)
            val key =
                NotebookCrypto.derive(password, requireNotNull(packet.salt), packet.iterations)
            try {
                NotebookPackage.decrypt(packet, key) // Manifest AAD and cover digest are verified.
                storage.keyCache.put(id, key)
            } finally {
                key.fill(0)
            }
        }
    }
}
