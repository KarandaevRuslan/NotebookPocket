package dev.pocket.notebook.data.draft

import android.util.AtomicFile
import dev.pocket.notebook.data.packing.ArchiveFiles
import dev.pocket.notebook.data.packing.NotebookPackage
import java.io.File

/** Atomic, authenticated checkpoints, independent of whether HTML is a valid portable package. */
internal class DraftStore(private val root: File, private val cipher: DraftCipher) {
    private val idPattern = Regex("[a-zA-Z0-9_-]{1,120}")

    private fun file(id: String): AtomicFile {
        require(idPattern.matches(id)) { "Invalid draft id" }
        check(root.isDirectory || root.mkdirs()) { "Cannot create draft directory" }
        return AtomicFile(File(root, "$id.draft"))
    }

    @Synchronized
    fun write(id: String, source: String) {
        val plain = source.toByteArray(Charsets.UTF_8)
        require(plain.size <= NotebookPackage.MAX_SOURCE) { "Draft exceeds source limit" }
        val sealed =
            try {
                cipher.seal(id, plain)
            } finally {
                plain.fill(0)
            }
        val target = file(id)
        val stream = target.startWrite()
        try {
            stream.write(sealed)
            stream.fd.sync()
            target.finishWrite(stream)
            check(
                target
                    .openRead()
                    .use { ArchiveFiles.read(it, NotebookPackage.MAX_SOURCE + 1024) }
                    .contentEquals(sealed)
            ) {
                "Draft checkpoint was not committed"
            }
        } catch (error: Throwable) {
            target.failWrite(stream)
            throw error
        }
    }

    @Synchronized
    fun read(id: String): String? {
        val target = file(id)
        if (!target.baseFile.exists() && !File(target.baseFile.path + ".bak").exists()) return null
        val sealed =
            target.openRead().use { ArchiveFiles.read(it, NotebookPackage.MAX_SOURCE + 1024) }
        val plain = cipher.open(id, sealed)
        return try {
            plain.toString(Charsets.UTF_8)
        } finally {
            plain.fill(0)
        }
    }

    @Synchronized
    fun exists(id: String): Boolean {
        val base = file(id).baseFile
        return base.exists() || File(base.path + ".bak").exists()
    }

    @Synchronized
    fun remove(id: String) {
        file(id).delete()
    }

    /** Deleted document checkpoints are also cleanup debt; never remove a live draft. */
    @Synchronized
    fun recover(liveIds: Set<String>, now: Long, graceMillis: Long = 60 * 60 * 1000L) {
        root.listFiles().orEmpty().forEach { candidate ->
            val id = candidate.name.removeSuffix(".bak").removeSuffix(".new").removeSuffix(".draft")
            if (
                idPattern.matches(id) &&
                    id !in liveIds &&
                    candidate.lastModified() <= now - graceMillis
            )
                runCatching { file(id).delete() }
        }
    }
}
