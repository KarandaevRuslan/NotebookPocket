package dev.pocket.notebook.data.transfer

import dev.pocket.notebook.data.NotebookEntry
import dev.pocket.notebook.data.NotebookMetadataCodec
import dev.pocket.notebook.data.VfsTreeStore
import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.document.DocumentContent
import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.packing.NotebookPackage
import dev.pocket.notebook.data.storage.StorageAccess
import dev.pocket.notebook.data.storage.revision

internal class DocumentImporter(
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val content: DocumentContent,
    private val treeStore: VfsTreeStore,
) {

    suspend fun importBytes(bytes: ByteArray, parentId: String? = null): NotebookEntry {
        require(bytes.size <= NotebookPackage.MAX_ARCHIVE) { "Archive is too large" }
        val packaged = bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        val packet = if (packaged) NotebookPackage.unpack(bytes) else null
        val meta =
            packet?.metadata
                ?: bytes.toString(Charsets.UTF_8).let { source ->
                    require(
                        Regex("<notebook(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(source)
                    )
                    NotebookMetadataCodec.read(source).also(NotebookMetadataCodec::validate)
                }
        return run {
            val id = storage.newId()
            val result =
                storage.revision().run { tx ->
                    val parent = parentId?.let { storage.required(it) }
                    require(parent == null || parent.kind in setOf("FOLDER", "COLLECTION"))
                    if (parent?.kind == "COLLECTION") collections.load(parent)
                    val data =
                        if (parent?.kind == "COLLECTION" && !packaged)
                            NotebookPackage.pack(bytes.toString(Charsets.UTF_8), meta, null, null)
                        else bytes
                    val ref =
                        if (parent?.kind == "COLLECTION") "member:$id"
                        else tx.write(id, data, if (packaged) "npkg" else "html")
                    treeStore.shiftSiblings(parentId)
                    val node =
                        VfsNodeEntity(
                            id,
                            parentId,
                            "NOTEBOOK",
                            0,
                            meta.title,
                            meta.description,
                            meta.author,
                            meta.version,
                            contentRef = ref,
                            packageFormat = packaged || parent?.kind == "COLLECTION",
                            hasCover = packet?.cover != null,
                            encrypted = packet?.protected == true,
                        )
                    storage.dao.insert(node)
                    if (parent?.kind == "COLLECTION") {
                        val state = collections.load(parent)
                        collections.store(
                            parent,
                            state.copy(members = state.members + (id to data)),
                            tx,
                        )
                    }
                    node
                }
            content.toEntry(result)
        }
    }
}
