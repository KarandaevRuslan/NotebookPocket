package dev.pocket.notebook.data.storage

import androidx.room.withTransaction
import dev.pocket.notebook.data.local.NotebookDatabase

/** Files are staged before the Room commit; obsolete revisions are removed afterwards. */
internal class Revision(private val db: NotebookDatabase, private val files: DocumentFiles) {
    private val staged = mutableListOf<String>()
    private val obsolete = mutableSetOf<String>()

    fun write(id: String, bytes: ByteArray, suffix: String = "npkg") =
        files.write(id, bytes, suffix).also { staged.add(it) }

    fun remove(ref: String?) {
        if (ref != null) obsolete.add(ref)
    }

    suspend fun <T> run(block: suspend (Revision) -> T): T {
        val result =
            try {
                db.withTransaction { block(this) }
            } catch (t: Throwable) {
                staged.forEach { ref ->
                    try {
                        files.deleteRef(ref)
                    } catch (cleanup: Throwable) {
                        t.addSuppressed(cleanup)
                    }
                }
                throw t
            }
        // This is outside the rollback region: the DB already references staged files.
        obsolete.forEach { ref ->
            runCatching { files.deleteRef(ref) }
                .onFailure {
                    dev.pocket.notebook.core.logging.NotebookLog.w(
                        "NotebookStorage",
                        "Obsolete revision cleanup deferred",
                        it,
                    )
                }
        }
        return result
    }
}
