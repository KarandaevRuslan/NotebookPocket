package dev.pocket.notebook.data

import android.content.Context
import androidx.room.withTransaction
import dev.pocket.notebook.data.local.DocumentFileStore
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.packing.NotebookPackage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

/** One-time transactionally committed migration; preserves IDs for Android Keystore cache. */
internal class LegacyStorageMigration(
    private val context: Context,
    private val db: NotebookDatabase,
    private val store: DocumentFileStore,
) {
    private val dao = db.vfsDao()
    private val legacyFolder = File(context.filesDir, "notebooks")
    private val migrationPrefs =
        context.getSharedPreferences("room-vfs-migration", Context.MODE_PRIVATE)
    private val oldOrder =
        context.getSharedPreferences("notebook-library-order", Context.MODE_PRIVATE)
    private val idPattern = Regex("[a-f0-9-]{36}")

    fun migrate() {

        if (migrationPrefs.getBoolean("completed", false)) return
        synchronized(db) {
            if (migrationPrefs.getBoolean("completed", false)) return
            val files = legacyFolder.listFiles().orEmpty()
            val ids =
                files
                    .filter {
                        it.isFile &&
                            (it.extension == "html" || it.extension == "npkg") &&
                            idPattern.matches(it.nameWithoutExtension)
                    }
                    .map { it.nameWithoutExtension }
                    .distinct()
            val ordered =
                runCatching {
                        val json = JSONArray(oldOrder.getString("ids", "[]"))
                        (0 until json.length())
                            .map { json.getString(it) }
                            .filter { it in ids }
                            .distinct()
                    }
                    .getOrDefault(emptyList())
            val byOrder = ordered + ids.filterNot { it in ordered }.sorted()
            runBlocking(Dispatchers.IO) {
                db.withTransaction {
                    for ((position, id) in byOrder.withIndex()) {
                        if (dao.get(id) != null) continue
                        val pkgFile = File(legacyFolder, "$id.npkg")
                        val htmlFile = File(legacyFolder, "$id.html")
                        val hasPackage = pkgFile.isFile
                        val bytes = if (hasPackage) pkgFile.readBytes() else htmlFile.readBytes()
                        val packet = if (hasPackage) NotebookPackage.unpack(bytes) else null
                        val meta =
                            packet?.metadata
                                ?: NotebookMetadataCodec.read(bytes.toString(Charsets.UTF_8))
                        val cover =
                            if (hasPackage) null
                            else File(legacyFolder, "$id.cover").takeIf { it.isFile }?.readBytes()
                        val contentRef = store.write(id, bytes, if (hasPackage) "npkg" else "html")
                        val coverRef = cover?.let { store.write(id, it, "cover") }
                        dao.insertIgnore(
                            VfsNodeEntity(
                                id = id,
                                parentId = null,
                                kind = "NOTEBOOK",
                                position = position,
                                title = meta.title,
                                description = meta.description,
                                author = meta.author,
                                version = meta.version,
                                updated = (if (hasPackage) pkgFile else htmlFile).lastModified(),
                                contentRef = contentRef,
                                packageFormat = hasPackage,
                                coverRef = coverRef,
                                hasCover = if (hasPackage) packet?.cover != null else cover != null,
                                encrypted = packet?.protected == true,
                            )
                        )
                    }
                }
            }
            // File revisions and Room references are durable. Never delete legacy files if
            // migration fails.
            check(migrationPrefs.edit().putBoolean("completed", true).commit())
            files
                .filter {
                    it.isFile &&
                        (it.extension in setOf("html", "npkg", "cover", "tmp") ||
                            it.name.endsWith(".tmp")) &&
                        idPattern.matches(it.name.substringBefore('.'))
                }
                .forEach { it.delete() }
        }
    }
}
