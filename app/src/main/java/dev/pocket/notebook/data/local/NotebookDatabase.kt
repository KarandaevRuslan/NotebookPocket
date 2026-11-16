package dev.pocket.notebook.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.pocket.notebook.data.packing.NotebookPackage

/** v2 migrates legacy BLOB content to app-private versioned files. */
@Database(entities = [VfsNodeEntity::class], version = 2, exportSchema = true)
abstract class NotebookDatabase : RoomDatabase() {
    abstract fun vfsDao(): VfsDao

    companion object {
        @Volatile private var INSTANCE: NotebookDatabase? = null

        internal fun migration1To2(context: Context): Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val files = DocumentFileStore(context)
                    db.execSQL(
                        """
                    CREATE TABLE IF NOT EXISTS `vfs_nodes_new` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `parentId` TEXT,
                        `kind` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `version` TEXT NOT NULL,
                        `updated` INTEGER NOT NULL,
                        `contentRef` TEXT,
                        `packageFormat` INTEGER NOT NULL,
                        `encrypted` INTEGER NOT NULL,
                        `coverRef` TEXT,
                        `hasCover` INTEGER NOT NULL
                    )
                """
                            .trimIndent()
                    )
                    // The Room migration runs inside SQLite's transaction. Files are written
                    // first, then their references are stored; legacy BLOB rows are dropped
                    // only after every payload has been copied successfully.
                    db.query(
                            "SELECT id, parentId, kind, position, title, description, author, version, updated, payload, packageFormat, cover FROM vfs_nodes"
                        )
                        .use { cursor ->
                            while (cursor.moveToNext()) {
                                val id = cursor.getString(0)
                                val kind = cursor.getString(2)
                                val isPackage = cursor.getInt(10) != 0
                                val payload = if (cursor.isNull(9)) null else cursor.getBlob(9)
                                val cover = if (cursor.isNull(11)) null else cursor.getBlob(11)
                                if (kind == "NOTEBOOK")
                                    check(payload != null) { "Notebook content is missing: $id" }
                                val fileRef =
                                    payload?.let {
                                        files.write(id, it, if (isPackage) "npkg" else "html")
                                    }
                                val coverRef =
                                    if (!isPackage) cover?.let { files.write(id, it, "cover") }
                                    else null
                                // A malformed archive must not cause data-loss during migration:
                                // copy its original bytes and allow a later explicit import/read
                                // error.
                                val packet =
                                    if (isPackage && payload != null)
                                        runCatching { NotebookPackage.unpack(payload) }.getOrNull()
                                    else null
                                val hasCover =
                                    if (isPackage) packet?.cover != null else cover != null
                                val encrypted = if (isPackage) packet?.protected ?: true else false
                                db.execSQL(
                                    """
                            INSERT INTO `vfs_nodes_new` (`id`, `parentId`, `kind`, `position`, `title`, `description`, `author`, `version`, `updated`, `contentRef`, `packageFormat`, `encrypted`, `coverRef`, `hasCover`)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """
                                        .trimIndent(),
                                    arrayOf(
                                        id,
                                        if (cursor.isNull(1)) null else cursor.getString(1),
                                        kind,
                                        cursor.getInt(3),
                                        cursor.getString(4),
                                        cursor.getString(5),
                                        cursor.getString(6),
                                        cursor.getString(7),
                                        cursor.getLong(8),
                                        fileRef,
                                        if (isPackage) 1 else 0,
                                        if (encrypted) 1 else 0,
                                        coverRef,
                                        if (hasCover) 1 else 0,
                                    ),
                                )
                            }
                        }
                    db.execSQL("DROP TABLE `vfs_nodes`")
                    db.execSQL("ALTER TABLE `vfs_nodes_new` RENAME TO `vfs_nodes`")
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_vfs_nodes_parentId_position` ON `vfs_nodes` (`parentId`, `position`)"
                    )
                }
            }

        fun get(context: Context): NotebookDatabase =
            INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                NotebookDatabase::class.java,
                                "notebook_pocket.db",
                            )
                            .addMigrations(migration1To2(context.applicationContext))
                            .build()
                            .also { INSTANCE = it }
                }
    }
}
