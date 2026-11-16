package dev.pocket.notebook.data.transfer

import dev.pocket.notebook.data.security.SecurityPolicy
import android.content.Context
import android.net.Uri
import dev.pocket.notebook.data.*
import dev.pocket.notebook.data.packing.*
import dev.pocket.notebook.data.security.NotebookCrypto
import org.json.JSONObject

/** Android document-provider I/O and archive-password lifetime are isolated at this boundary. */
internal class LibraryTransfer(
    private val context: Context,
    private val archive: LibraryArchive,
    private val importer: DocumentImporter,
) {
    suspend fun importDocument(uri: Uri, parentId: String?): NotebookEntry {
        val bytes =
            requireNotNull(context.contentResolver.openInputStream(uri)).use {
                ArchiveFiles.read(it, NotebookPackage.MAX_ARCHIVE)
            }
        return importer.importBytes(bytes, parentId)
    }

    suspend fun export(ids: List<String>, uri: Uri, password: CharArray?) {
        try {
            var data = archive.encode(ids)
            if (password != null) {
                require(password.size >= SecurityPolicy.MIN_PASSWORD_LENGTH)
                val salt = NotebookCrypto.randomBytes(NotebookCrypto.SALT_LENGTH)
                val key = NotebookCrypto.derive(password, salt)
                try {
                    data =
                        ArchiveFiles.pack(
                            mapOf(
                                "protected.npx" to PortableBundle.seal(data, key, salt, "library")
                            )
                        )
                } finally {
                    key.fill(0)
                }
            }
            requireNotNull(context.contentResolver.openOutputStream(uri, "w")).use {
                it.write(data)
            }
        } finally {
            password?.fill('\u0000')
        }
    }

    suspend fun import(uri: Uri, parent: String?, password: CharArray?): List<String> {
        try {
            val bytes =
                requireNotNull(context.contentResolver.openInputStream(uri)).use {
                    ArchiveFiles.read(it, ArchiveFiles.MAX_TOTAL)
                }
            if (bytes.size < 2 || bytes[0] != 80.toByte() || bytes[1] != 75.toByte())
                return listOf(importer.importBytes(bytes, parent).id)
            var data = bytes
            var contents = ArchiveFiles.unpack(data)
            if (contents.keys == setOf("protected.npx")) {
                if (password == null) throw ArchivePasswordRequired()
                val sealed = contents.getValue("protected.npx")
                val h = PortableBundle.header(sealed, "library")
                val key = NotebookCrypto.derive(password, h.salt, h.iterations)
                try {
                    data = PortableBundle.open(sealed, key, "library")
                    contents = ArchiveFiles.unpack(data)
                } finally {
                    key.fill(0)
                }
            }
            val manifest = contents["manifest.json"] ?: error("Missing manifest")
            require(manifest.size <= 2 * 1024 * 1024)
            if (
                JSONObject(manifest.toString(Charsets.UTF_8)).optString("format") !=
                    "pocket-library"
            )
                return listOf(importer.importBytes(bytes, parent).id)
            return archive.decode(data, parent)
        } finally {
            password?.fill('\u0000')
        }
    }
}
