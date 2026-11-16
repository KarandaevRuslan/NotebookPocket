package dev.pocket.notebook.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.data.packing.ArchiveFiles
import dev.pocket.notebook.data.security.NotebookCrypto
import java.io.File
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ResourceWorkspaceTest {
    private lateinit var context: Context
    private lateinit var db: NotebookDatabase
    private lateinit var repo: NotebookRepository

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, NotebookDatabase::class.java).build()
        repo = NotebookRepository(context, db, TestDraftCipher)
    }

    @After
    fun close() {
        repo.lockAll()
        db.close()
    }

    private fun notebook(parent: String? = null): String {
        val f = File(context.cacheDir, "resource-${java.util.UUID.randomUUID()}.html")
        f.writeText(
            "<notebook><title>Fixture</title><script type=\"module\" id=\"1\">display(1)</script></notebook>"
        )
        return repo.importDocument(Uri.fromFile(f), parent).id
    }

    private fun paths(owner: String, dir: String = "") =
        repo.resourceEntries(owner, dir).map { it.path }

    private fun roundtrip(id: String): String {
        val f = File(context.cacheDir, "export-${java.util.UUID.randomUUID()}.zip")
        repo.exportItems(listOf(id), Uri.fromFile(f))
        return repo.importAny(Uri.fromFile(f), null).single()
    }

    @Test
    fun emptyDirectoriesSurviveRestartAndNotebookArchiveWithoutBecomingAttachments() {
        val n = notebook()
        repo.createResourceFolder(n, "empty/nested")
        repo.putResource(n, "data/value.txt", "value".toByteArray())
        repo = NotebookRepository(context, db, TestDraftCipher)
        assertEquals(listOf("empty/nested"), paths(n, "empty"))
        assertTrue(repo.resourceEntries(n, "empty").single().directory)
        assertEquals(setOf("data/value.txt"), repo.attachmentNames(n).toSet())
        assertThrows(Exception::class.java) { repo.attachment(n, ResourceTree.META) }
        val copy = roundtrip(n)
        assertEquals(paths(n), paths(copy))
        assertTrue(repo.resourceEntry(copy, "empty/nested").directory)
        assertTrue(paths(copy, "empty/nested").isEmpty())
    }

    @Test
    fun sharedEmptyFoldersTravelWithCollectionAndDetachedMember() {
        val c = repo.createCollection("Shared", null).id
        val n = notebook(c)
        repo.createResourceFolder(c, "empty/shared")
        repo.createResourceFolder(n, "personal")
        val c2 = roundtrip(c)
        assertTrue(repo.resourceEntry(c2, "empty/shared").directory)
        assertThrows(IllegalArgumentException::class.java) { repo.convertContainer(c) }
        val exported = roundtrip(n)
        assertTrue(repo.resourceEntry(exported, "shared/empty/shared").directory)
        val code = repo.read(n)
        repo.transfer(listOf(n), null, true)
        assertEquals(code, repo.read(n))
        assertTrue(repo.resourceEntry(n, "shared/empty/shared").directory)
        assertTrue(repo.resourceEntry(n, "personal").directory)
    }

    @Test
    fun subtreeBatchCopyCutRenameAndDeletePreserveEmptyFolders() {
        val a = notebook()
        val b = notebook()
        repo.createResourceFolder(a, "folder/empty")
        repo.putResource(a, "folder/text.txt", "x".toByteArray())
        repo.putResource(a, "root.txt", "r".toByteArray())
        repo.transferResources(a, listOf("folder", "folder/text.txt", "root.txt"), b, "", false)
        assertEquals(setOf("folder", "root.txt"), paths(b).toSet())
        assertTrue(repo.resourceEntry(b, "folder/empty").directory)
        repo.createResourceFolder(a, "target")
        repo.transferResources(a, listOf("folder", "root.txt"), a, "target", true)
        assertEquals(listOf("target"), paths(a))
        assertTrue(repo.resourceEntry(a, "target/folder/empty").directory)
        repo.renameResource(a, "target/folder", "target/renamed")
        repo.deleteResources(a, listOf("target/renamed", "target/root.txt"))
        assertEquals(listOf("target"), paths(a))
        assertTrue(paths(a, "target").isEmpty())
        repo.transferResources(b, listOf("folder", "root.txt"), a, "target", true)
        assertTrue(paths(b).isEmpty())
        assertEquals("x", repo.readResource(a, "target/folder/text.txt").decodeToString())
    }

    @Test
    fun allConflictsAndCyclesRollbackWholeBatches() {
        val a = notebook()
        val b = notebook()
        repo.putResource(a, "first.txt", byteArrayOf(1))
        repo.putResource(a, "last.txt", byteArrayOf(2))
        repo.putResource(b, "last.txt", byteArrayOf(3))
        assertThrows(IllegalArgumentException::class.java) {
            repo.transferResources(a, listOf("first.txt", "last.txt"), b, "", true)
        }
        assertEquals(listOf("last.txt"), paths(b))
        assertEquals(setOf("first.txt", "last.txt"), paths(a).toSet())
        repo.createResourceFolder(a, "parent/child")
        assertThrows(IllegalArgumentException::class.java) {
            repo.transferResources(a, listOf("parent"), a, "parent/child", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.renameResource(a, "parent", "parent/child/x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.createResourceFolder(a, "first.txt/bad")
        }
        assertTrue(repo.resourceEntry(a, "parent/child").directory)
        assertArrayEquals(byteArrayOf(1), repo.readResource(a, "first.txt"))
    }

    @Test
    fun deletingLastFileKeepsItsParentAndReplaceDoesNotResurrectMovedFiles() {
        val n = notebook()
        val source = repo.read(n)
        repo.putResource(n, "data/a.txt", "before".toByteArray())
        val hash = NotebookCrypto.sha256(repo.readResource(n, "data/a.txt"))
        repo.replaceResource(n, "data/a.txt", "after".toByteArray(), hash)
        assertEquals(source, repo.read(n))
        assertThrows(IllegalArgumentException::class.java) {
            repo.replaceResource(n, "data/a.txt", "stale".toByteArray(), hash)
        }
        assertEquals("after", repo.readResource(n, "data/a.txt").decodeToString())
        repo.renameResource(n, "data/a.txt", "data/b.txt")
        assertThrows(IllegalArgumentException::class.java) {
            repo.replaceResource(n, "data/a.txt", "resurrect".toByteArray(), hash)
        }
        repo.deleteResource(n, "data/b.txt")
        assertTrue(repo.resourceEntry(n, "data").directory)
        assertTrue(paths(n, "data").isEmpty())
        assertEquals(source, repo.read(n))
    }

    @Test
    fun encryptedResourceEditingAndFolderExportKeepPasswords() {
        val n = notebook()
        repo.putResource(n, "secret.txt", "one".toByteArray())
        repo.createResourceFolder(n, "empty")
        repo.setPassword(n, "secret password".toCharArray())
        val hash = NotebookCrypto.sha256(repo.readResource(n, "secret.txt"))
        repo.replaceResource(n, "secret.txt", "two".toByteArray(), hash)
        repo.lockAll()
        assertThrows(NotebookLockedException::class.java) { repo.resourceEntries(n) }
        assertThrows(NotebookLockedException::class.java) {
            repo.replaceResource(n, "secret.txt", byteArrayOf(0), hash)
        }
        val copy = roundtrip(n)
        repo.unlock(copy, "secret password".toCharArray())
        assertTrue(repo.resourceEntry(copy, "empty").directory)
        assertEquals("two", repo.readResource(copy, "secret.txt").decodeToString())
    }

    @Test
    fun metadataRejectsTraversalAndFileFolderCollisionsButLegacyFilesStillRead() {
        val old = mapOf("dir/a.txt" to byteArrayOf(1))
        assertEquals(listOf("dir"), ResourceTree.decode(old).children("").map { it.path })
        fun record(dir: String) =
            JSONObject()
                .put("format", "dev.pocket.notebook.resources")
                .put("version", 1)
                .put("directories", org.json.JSONArray(listOf(dir)))
                .toString()
                .toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveFiles.resources(mapOf(ResourceTree.META to record("../escape")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveFiles.resources(
                mapOf(ResourceTree.META to record("file/sub"), "file" to byteArrayOf(1))
            )
        }
        val legacy = ResourceTree.decode(mapOf(ResourceTree.META to "legacy text".toByteArray()))
        assertEquals("legacy text", legacy.files[ResourceTree.META]!!.decodeToString())
        assertThrows(IllegalArgumentException::class.java) { legacy.encode() }
        assertEquals(
            "legacy text",
            legacy
                .renamed(ResourceTree.META, "legacy.txt")
                .encode()["legacy.txt"]!!
                .decodeToString(),
        )
    }

    @Test
    fun textCodecsPreserveBomEncodingAndLineEndingsAndRejectBinary() {
        val original = "Привет\r\nworld\r\n"
        for ((charset, bom) in
            listOf(
                Charsets.UTF_8 to byteArrayOf(),
                Charsets.UTF_8 to byteArrayOf(-17, -69, -65),
                Charsets.UTF_16LE to byteArrayOf(-1, -2),
                Charsets.UTF_16BE to byteArrayOf(-2, -1),
            )) {
            val bytes = bom + original.toByteArray(charset)
            val text = ResourceText.decode(bytes)!!
            assertEquals("Привет\nworld\n", text.text)
            assertArrayEquals(bytes, text.encode(text.text))
            assertArrayEquals(bom + "changed\r\n".toByteArray(charset), text.encode("changed\n"))
        }
        assertNull(ResourceText.decode(byteArrayOf(0, 1, 2)))
        assertNull(ResourceText.decode(byteArrayOf(-1, 0)))
        assertNull(ResourceText.decode(ByteArray(ResourceText.MAX_BYTES + 1) { 65 }))
        assertEquals(
            "a\rb",
            ResourceText.decode("a\rb".toByteArray())!!.encode("a\nb").decodeToString(),
        )
    }
}
