package dev.pocket.notebook.data

import androidx.room.Room
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.storage.DocumentFiles
import dev.pocket.notebook.data.storage.Revision
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RevisionFailureTest {
    private lateinit var db: NotebookDatabase
    private val files = FakeFiles()

    @Before
    fun open() {
        db =
            Room.inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    NotebookDatabase::class.java,
                )
                .build()
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun failedSqlCommitRemovesOnlyStagedBytes() = runBlocking {
        val error =
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    Revision(db, files).run { tx ->
                        val ref = tx.write("id", byteArrayOf(1))
                        db.vfsDao().insert(node(ref))
                        tx.remove("old")
                        error("rollback")
                    }
                }
            }
        assertEquals("rollback", error.message)
        assertNull(db.vfsDao().get("id"))
        assertEquals(listOf("new"), files.deleted)
    }

    @Test
    fun postCommitCleanupFailureCannotDeleteCommittedBytes() = runBlocking {
        files.failDelete = "old"
        Revision(db, files).run { tx ->
            db.vfsDao().insert(node(tx.write("id", byteArrayOf(1))))
            tx.remove("old")
        }
        assertEquals("new", db.vfsDao().get("id")!!.contentRef)
        assertTrue(files.contents.containsKey("new"))
        assertFalse("new" in files.deleted)
    }

    private fun node(ref: String) =
        VfsNodeEntity("id", null, "NOTEBOOK", 0, "Test", contentRef = ref)

    private class FakeFiles : DocumentFiles {
        val contents = mutableMapOf("old" to byteArrayOf(0))
        val deleted = mutableListOf<String>()
        var failDelete: String? = null

        override fun write(id: String, bytes: ByteArray, suffix: String): String =
            "new".also { contents[it] = bytes }

        override fun read(ref: String) = contents.getValue(ref)

        override fun deleteRef(ref: String?) {
            if (ref == failDelete) error("cleanup failed")
            if (ref != null) {
                deleted.add(ref)
                contents.remove(ref)
            }
        }

        override fun deleteDocument(id: String) = Unit
    }
}
