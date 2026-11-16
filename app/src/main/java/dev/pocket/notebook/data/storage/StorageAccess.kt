package dev.pocket.notebook.data.storage

import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.data.local.VfsDao
import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.security.DeviceKeyCache

/** Infrastructure boundary. Services never depend on the public repository or launch coroutines. */
internal interface StorageAccess {
    val db: NotebookDatabase
    val dao: VfsDao
    val files: DocumentFiles
    val keyCache: DeviceKeyCache

    fun now(): Long

    fun newId(): String

    suspend fun required(id: String): VfsNodeEntity = dao.get(id) ?: error("Node not found")

    suspend fun notebook(id: String): VfsNodeEntity =
        required(id).also { require(it.kind == "NOTEBOOK") { "Not a document" } }
}

internal fun StorageAccess.revision() = Revision(db, files)
