package dev.pocket.notebook.data.collection

import dev.pocket.notebook.data.document.DocumentContent
import dev.pocket.notebook.data.packing.PortableBundle
import dev.pocket.notebook.data.storage.StorageAccess
import dev.pocket.notebook.data.storage.revision

internal class CollectionConversion(
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val content: DocumentContent,
) {
    suspend fun convert(id: String) =
        storage.revision().run { tx ->
            val n = storage.required(id)
            val children = storage.dao.children(id)
            if (n.kind == "FOLDER") {
                require(children.all { it.kind == "NOTEBOOK" }) {
                    "Only a folder without nested folders or collections can be converted"
                }
                val members = children.associate { it.id to content.portable(it) }
                collections.store(n, PortableBundle.Collection(members), tx, encrypted = false)
                children.forEach { child ->
                    storage.dao.update(
                        child.copy(
                            contentRef = "member:${child.id}",
                            packageFormat = true,
                            coverRef = null,
                        )
                    )
                    tx.remove(child.contentRef)
                    tx.remove(child.coverRef)
                }
            } else {
                require(n.kind == "COLLECTION")
                val contents = collections.load(n)
                require(contents.resources.isEmpty()) {
                    "Remove or move all shared resources before converting this collection to a folder"
                }
                children.forEach { child ->
                    val ref =
                        tx.write(child.id, contents.members.getValue(collections.memberKey(child)))
                    storage.dao.update(child.copy(contentRef = ref))
                }
                storage.dao.update(
                    n.copy(
                        kind = "FOLDER",
                        encrypted = false,
                        packageFormat = false,
                        contentRef = null,
                    )
                )
                tx.remove(n.contentRef)
            }
            storage.keyCache.forget(id)
            collections.invalidate(id)
        }
}
