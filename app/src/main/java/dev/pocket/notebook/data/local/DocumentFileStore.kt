package dev.pocket.notebook.data.local

import android.content.Context
import dev.pocket.notebook.data.storage.DocumentFiles
import dev.pocket.notebook.data.storage.StoredRevision
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Versioned, app-private document files. Room stores references only. Writing a new revision never
 * overwrites the revision currently referenced by SQLite. If a SQL transaction fails, its old
 * references remain readable; an unreferenced revision can be removed safely. No user-controlled
 * filename or directory escapes.
 */
internal class DocumentFileStore(context: Context) : DocumentFiles {
    private val root = File(context.filesDir, "document-files").apply { mkdirs() }
    private val docId = Regex("[a-zA-Z0-9_-]{1,120}")
    private val refPattern = Regex("[a-zA-Z0-9_-]{1,120}/[a-f0-9-]{36}\\.(html|npkg|cover)")

    private fun directory(id: String): File {
        require(docId.matches(id)) { "Invalid document id" }
        return File(root, id)
    }

    override fun write(id: String, bytes: ByteArray, suffix: String): String {
        require(suffix in setOf("html", "npkg", "cover"))
        val dir = directory(id)
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create document directory" }
        val name = "${UUID.randomUUID()}.$suffix"
        val tmp = File(dir, "$name.tmp")
        val final = File(dir, name)
        try {
            FileOutputStream(tmp).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            check(tmp.renameTo(final)) { "Cannot atomically store document" }
        } finally {
            tmp.delete()
        }
        return "$id/$name"
    }

    override fun read(ref: String): ByteArray {
        require(refPattern.matches(ref)) { "Invalid file reference" }
        return File(root, ref).readBytes()
    }

    override fun deleteRef(ref: String?) {
        if (
            ref != null &&
                (refPattern.matches(ref) ||
                    (ref.endsWith(".tmp") && refPattern.matches(ref.removeSuffix(".tmp"))))
        ) {
            val file = File(root, ref)
            if (file.exists() && !file.delete())
                throw IOException("Cannot remove obsolete revision")
        }
    }

    override fun inventory(): List<StoredRevision> =
        root
            .listFiles()
            .orEmpty()
            .filter {
                it.isDirectory &&
                    docId.matches(it.name) &&
                    it.canonicalFile.parentFile == root.canonicalFile
            }
            .flatMap { directory ->
                directory.listFiles().orEmpty().mapNotNull { file ->
                    val ref = "${directory.name}/${file.name}"
                    val temporary = file.name.endsWith(".tmp")
                    val valid = refPattern.matches(if (temporary) ref.removeSuffix(".tmp") else ref)
                    if (
                        valid &&
                            file.isFile &&
                            file.canonicalFile.parentFile == directory.canonicalFile
                    )
                        StoredRevision(ref, file.lastModified(), temporary)
                    else null
                }
            }

    override fun deleteDocument(id: String) {
        // Only used after the database has committed deletion of this document.
        directory(id).deleteRecursively()
    }
}
