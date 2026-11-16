package dev.pocket.notebook.data

import android.content.Context
import androidx.room.Room
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.platform.app.InstrumentationRegistry
import dev.pocket.notebook.data.local.DocumentFileStore
import dev.pocket.notebook.data.local.NotebookDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class StorageV2MigrationTest {
    @Test fun legacySqliteBlobsMoveToExternalFilesAndTreeIsPreserved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "vfs-upgrade-${UUID.randomUUID()}.db"
        val id = UUID.randomUUID().toString()
        val folderId = UUID.randomUUID().toString()
        val html = "<notebook><title>Migration test</title></notebook>".toByteArray()
        val cover = byteArrayOf(1, 2, 3, 4)
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL("""
                CREATE TABLE vfs_nodes (
                    id TEXT NOT NULL PRIMARY KEY, parentId TEXT, kind TEXT NOT NULL,
                    position INTEGER NOT NULL, title TEXT NOT NULL,
                    description TEXT NOT NULL, author TEXT NOT NULL,
                    version TEXT NOT NULL, updated INTEGER NOT NULL,
                    payload BLOB, packageFormat INTEGER NOT NULL, cover BLOB
                )
            """.trimIndent())
            sqlite.execSQL("CREATE INDEX index_vfs_nodes_parentId_position ON vfs_nodes(parentId, position)")
            sqlite.execSQL("INSERT INTO vfs_nodes VALUES(?, NULL, 'FOLDER', 0, 'Folder', '', '', '', 10, NULL, 0, NULL)", arrayOf(folderId))
            sqlite.execSQL("INSERT INTO vfs_nodes VALUES(?, ?, 'NOTEBOOK', 0, 'Migration test', '', '', '', 20, ?, 0, ?)", arrayOf(id, folderId, html, cover))
            sqlite.version = 1
        }
        val db = Room.databaseBuilder(context, NotebookDatabase::class.java, dbName)
            .addMigrations(NotebookDatabase.migration1To2(context)).build()
        try {
            val node = db.vfsDao().get(id)
            assertNotNull(node)
            val migrated = requireNotNull(node)
            assertEquals(folderId, migrated.parentId)
            assertEquals("Migration test", migrated.title)
            assertTrue(migrated.hasCover)
            val files = DocumentFileStore(context)
            assertArrayEquals(html, files.read(requireNotNull(migrated.contentRef)))
            assertArrayEquals(cover, files.read(requireNotNull(migrated.coverRef)))
            val columns = mutableListOf<String>()
            db.openHelper.readableDatabase.query("PRAGMA table_info(vfs_nodes)").use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(1)
            }
            assertFalse(columns.contains("payload"))
            assertFalse(columns.contains("cover"))
            assertNotNull(db.vfsDao().get(folderId))
        } finally {
            db.close()
            context.deleteDatabase(dbName)
            DocumentFileStore(context).deleteDocument(id)
        }
    }
}
