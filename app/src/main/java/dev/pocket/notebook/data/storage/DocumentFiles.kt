package dev.pocket.notebook.data.storage

/** Versioned byte storage. Implementations must never overwrite a live revision. */
internal interface DocumentFiles {
    fun write(id: String, bytes: ByteArray, suffix: String): String

    fun read(ref: String): ByteArray

    fun deleteRef(ref: String?)

    fun inventory(): List<StoredRevision> = emptyList()

    fun deleteDocument(id: String)
}

internal data class StoredRevision(val ref: String, val modified: Long, val temporary: Boolean)
