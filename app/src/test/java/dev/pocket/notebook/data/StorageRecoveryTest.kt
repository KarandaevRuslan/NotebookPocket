package dev.pocket.notebook.data

import androidx.room.Room
import dev.pocket.notebook.data.local.*
import dev.pocket.notebook.data.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StorageRecoveryTest {
    @Test
    fun recoveryKeepsLiveAndRecentFilesAndRetriesFailedCleanup() = runBlocking {
        val db =
            Room.inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    NotebookDatabase::class.java,
                )
                .build()
        try {
            db.vfsDao()
                .insert(VfsNodeEntity("id", null, "NOTEBOOK", 0, "Fixture", contentRef = "live"))
            db.vfsDao()
                .insert(
                    VfsNodeEntity("missing", null, "NOTEBOOK", 1, "Missing", contentRef = "missing")
                )
            val files =
                object : DocumentFiles {
                    var fail = true
                    val values =
                        mutableListOf(
                            StoredRevision("live", 0, false),
                            StoredRevision("orphan", 0, false),
                            StoredRevision("unfinished.tmp", 0, true),
                            StoredRevision("recent", 999, false),
                        )

                    override fun inventory() = values.toList()

                    override fun write(id: String, bytes: ByteArray, suffix: String) =
                        error("unused")

                    override fun read(ref: String) = error("unused")

                    override fun deleteDocument(id: String) = Unit

                    override fun deleteRef(ref: String?) {
                        if (ref == "orphan" && fail) error("delete failed")
                        values.removeAll { it.ref == ref }
                    }
                }
            val first = StorageRecovery(files, db.vfsDao()).run(1000, 100)
            assertEquals(setOf("missing"), first.missingReferences)
            assertEquals(1, first.removedOrphans)
            assertEquals(1, first.deferredDeletes)
            files.fail = false
            val second = StorageRecovery(files, db.vfsDao()).run(1000, 100)
            assertEquals(1, second.removedOrphans)
            assertEquals(setOf("live", "recent"), files.values.map { it.ref }.toSet())
            assertNotNull(db.vfsDao().get("missing"))
        } finally {
            db.close()
        }
    }
}
