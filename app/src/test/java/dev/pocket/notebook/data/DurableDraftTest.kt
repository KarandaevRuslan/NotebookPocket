package dev.pocket.notebook.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import dev.pocket.notebook.data.draft.*
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.data.security.NotebookCrypto
import dev.pocket.notebook.domain.SaveOutcome
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DurableDraftTest {
    private lateinit var context: Context
    private lateinit var db: NotebookDatabase
    private lateinit var repo: NotebookRepository
    private val original =
        "<notebook><title>Draft fixture</title><script id=\"1\" type=\"module\">display(1)</script></notebook>"

    @Before
    fun open() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, NotebookDatabase::class.java).build()
        repo = NotebookRepository(context, db, TestDraftCipher)
    }

    @After
    fun close() {
        repo.lockAll()
        db.close()
    }

    private fun document(parent: String? = null): String {
        val file = File(context.cacheDir, "draft-fixture.html").apply { writeText(original) }
        return repo.importDocument(Uri.fromFile(file), parent).id
    }

    @Test
    fun incompleteNotebookAndTitleSurviveRepositoryRecreation() {
        val id = document()
        for (source in listOf("<notebook><title", "<noteb", "")) {
            assertEquals(SaveOutcome.DRAFT, repo.save(id, source))
            repo = NotebookRepository(context, db, TestDraftCipher)
            assertEquals(source, repo.load(id).source)
            assertTrue(repo.load(id).fromDraft)
        }
        assertEquals(SaveOutcome.COMMITTED, repo.save(id, original))
        assertFalse(repo.load(id).fromDraft)
    }

    @Test
    fun protectedCollectionDraftRecoversOnlyAfterUnlockAndRetainsLatestAttachments() {
        val parent = repo.createCollection("Protected", null).id
        val id = document(parent)
        repo.putResource(id, "data.txt", "before".toByteArray())
        repo.putResource(parent, "shared.txt", "shared".toByteArray())
        repo.setPassword(parent, "a strong password".toCharArray())
        val incomplete = "<notebook><title>PRIVATE_DRAFT_RECOVERY"
        assertEquals(SaveOutcome.DRAFT, repo.save(id, incomplete))
        val raw = File(context.noBackupFilesDir, "notebook-drafts/$id.draft").readBytes()
        assertFalse(raw.toString(Charsets.ISO_8859_1).contains("PRIVATE_DRAFT_RECOVERY"))
        repo.lockAll()
        repo = NotebookRepository(context, db, TestDraftCipher)
        assertThrows(NotebookLockedException::class.java) { repo.load(id) }
        repo.unlock(parent, "a strong password".toCharArray())
        assertEquals(incomplete, repo.read(id))
        repo.replaceResource(
            id,
            "data.txt",
            "after".toByteArray(),
            NotebookCrypto.sha256("before".toByteArray()),
        )
        assertEquals(SaveOutcome.COMMITTED, repo.save(id, original))
        repo.transfer(listOf(id), null, true)
        assertEquals("after", repo.readResource(id, "data.txt").toString(Charsets.UTF_8))
        assertEquals("shared", repo.readResource(id, "shared/shared.txt").toString(Charsets.UTF_8))
    }

    @Test
    fun incompleteDraftCannotBeSilentlyCopiedOrExportedAsOlderContent() {
        val id = document()
        repo.save(id, "<notebook><tit")
        assertThrows(IllegalStateException::class.java) {
            repo.exportItems(listOf(id), Uri.fromFile(File(context.cacheDir, "draft.zip")))
        }
        assertThrows(IllegalStateException::class.java) { repo.transfer(listOf(id), null, false) }
        assertEquals("<notebook><tit", repo.read(id))
    }

    @Test
    fun checkpointFailureLeavesPreviousDocumentReadableAndIsNotAcknowledged() {
        val id = document()
        val failing =
            object : DraftCipher {
                override fun seal(documentId: String, plaintext: ByteArray): ByteArray =
                    throw IOException("No space left on device")

                override fun open(documentId: String, ciphertext: ByteArray) =
                    TestDraftCipher.open(documentId, ciphertext)
            }
        val failureRepo = NotebookRepository(context, db, failing)
        assertThrows(IOException::class.java) {
            failureRepo.save(id, original.replace("display(1)", "display(2)"))
        }
        assertEquals(original, repo.read(id))
    }

    @Test
    fun failedPackageWriteStillRecoversTheDurableCheckpoint() = runBlocking {
        val id = document()
        val local = dev.pocket.notebook.data.storage.LocalStorageAccess(context, db)
        val failingStorage =
            object : dev.pocket.notebook.data.storage.StorageAccess by local {
                override val files =
                    object : dev.pocket.notebook.data.storage.DocumentFiles by local.files {
                        override fun write(id: String, bytes: ByteArray, suffix: String): String =
                            throw IOException("disk full after checkpoint")
                    }
            }
        val drafts = DraftStore(File(context.noBackupFilesDir, "notebook-drafts"), TestDraftCipher)
        val collections = dev.pocket.notebook.data.collection.CollectionStore(failingStorage)
        val content =
            dev.pocket.notebook.data.document.DocumentContent(failingStorage, collections, drafts)
        val editor =
            dev.pocket.notebook.data.document.DocumentEditor(
                context,
                failingStorage,
                collections,
                content,
                VfsTreeStore(db, db.vfsDao()),
                drafts,
            )
        val changed = original.replace("display(1)", "display(2)")
        assertThrows(IOException::class.java) { runBlocking { editor.save(id, changed) } }
        repo = NotebookRepository(context, db, TestDraftCipher)
        assertEquals(changed, repo.load(id).source)
        assertTrue(repo.load(id).fromDraft)
    }

    @Test
    fun missingLiveRevisionIsAnErrorAndNeverBecomesAnEmptyDocument() = runBlocking {
        val id = document()
        val node = db.vfsDao().get(id)!!
        File(context.filesDir, "document-files/${node.contentRef}").delete()
        assertThrows(Exception::class.java) { repo.load(id) }
        assertEquals(node.contentRef, db.vfsDao().get(id)!!.contentRef)
    }
}
