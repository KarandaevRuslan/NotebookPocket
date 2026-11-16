package dev.pocket.notebook.data

import dev.pocket.notebook.data.ArchivePasswordRequired
import android.content.Context
import android.net.Uri
import androidx.room.Room
import dev.pocket.notebook.data.local.*
import dev.pocket.notebook.data.packing.*
import java.io.File
import java.util.zip.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CollectionsResourcesTest {
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

    private fun collection(name: String = "Collection") = repo.createCollection(name, null).id

    private fun notebook(parent: String? = null, name: String = "Recipe"): String {
        val html = file("fixture-${java.util.UUID.randomUUID()}.html")
        html.writeText(
            "<notebook><title>Recipe</title><script type=\"module\" id=\"1\">display(1)</script></notebook>"
        )
        val n = repo.importDocument(Uri.fromFile(html), parent)
        repo.updateMetadata(n.id, NotebookMetadata(name, "", "", ""))
        return n.id
    }

    private fun file(name: String) = File(context.cacheDir, name)

    private fun count() = runBlocking { db.vfsDao().count() }

    @Test
    fun newNotebooksRemainStandaloneAndMembershipIsExplicit() {
        val id = notebook()
        assertTrue(repo.allFolders().isEmpty())
        assertEquals(id, repo.children(null).single().id)
        val f = repo.createFolder("Folder", null).id
        val other = notebook(f)
        assertEquals(other, repo.children(f).single().id)
        assertTrue(repo.allFolders().none { it.collection })
        val c = collection()
        repo.transfer(listOf(id), c, true)
        assertEquals(id, repo.children(c).single().id)
        assertEquals(other, repo.children(f).single().id)
    }

    @Test
    fun deletionScopeIncludesHiddenDocumentsInLockedCollections() {
        val folder = repo.createFolder("Parent", null).id
        val plain = notebook(folder)
        val coll = repo.createCollection("Protected", folder).id
        val privateDoc = notebook(coll)
        val outside = notebook()
        repo.setPassword(coll, "collection password".toCharArray())
        repo.lockAll()
        assertTrue(repo.children(coll).isEmpty())
        assertEquals(setOf(privateDoc), repo.notebookIdsInBranches(listOf(coll)))
        assertEquals(setOf(plain, privateDoc), repo.notebookIdsInBranches(listOf(folder)))
        assertEquals(setOf(outside), repo.notebookIdsInBranches(listOf(outside)))
    }

    @Test
    fun personalResourcesSurviveEditingMetadataPasswordRestartAndRemoval() {
        val id = notebook()
        repo.putResource(id, "images/photo.png", byteArrayOf(1, 2, 3))
        repo.putResource(id, "data/info.txt", "private text".toByteArray())
        repo.save(id, repo.read(id).replace("<title>Recipe</title>", "<title>Updated</title>"))
        repo.updateMetadata(id, NotebookMetadata("Edited", "Description", "Author", "1"))
        val secret = "my password".toCharArray()
        repo.setPassword(id, secret)
        assertTrue(secret.all { it == '\u0000' })
        assertArrayEquals(byteArrayOf(1, 2, 3), repo.attachment(id, "images/photo.png"))
        repo.lockAll()
        assertThrows(NotebookLockedException::class.java) { repo.read(id) }
        repo = NotebookRepository(context, db, TestDraftCipher)
        assertFalse(repo.isUnlocked(id))
        assertThrows(Exception::class.java) { repo.unlock(id, "bad password".toCharArray()) }
        assertFalse(repo.isUnlocked(id))
        repo.unlock(id, "my password".toCharArray())
        assertEquals("private text", repo.attachment(id, "data/info.txt").toString(Charsets.UTF_8))
        repo.removePassword(id)
        assertFalse(repo.isEncrypted(id))
        assertArrayEquals(byteArrayOf(1, 2, 3), repo.attachment(id, "images/photo.png"))
    }

    @Test
    fun conversionPreservesIdsAndBlocksLossOfSharedResources() {
        val f = repo.createFolder("Folder", null).id
        val a = notebook(f)
        val b = notebook(f, "B")
        repo.putResource(a, "own.txt", "own".toByteArray())
        val before = repo.children(f).map { it.id }
        repo.convertContainer(f)
        assertTrue(repo.allFolders().single().collection)
        assertEquals(before, repo.children(f).map { it.id })
        repo.putResource(f, "images/common.png", "shared".toByteArray())
        assertEquals(
            "shared",
            repo.attachment(a, "shared/images/common.png").toString(Charsets.UTF_8),
        )
        assertThrows(IllegalArgumentException::class.java) { repo.convertContainer(f) }
        repo.deleteResource(f, "images")
        repo.convertContainer(f)
        assertFalse(repo.allFolders().single().collection)
        assertEquals(before, repo.children(f).map { it.id })
        assertEquals("own", repo.attachment(a, "own.txt").toString(Charsets.UTF_8))
        assertEquals("B", repo.entry(b).title)
    }

    @Test
    fun nestedContainersCannotConvertAndCollectionCannotAcceptFolders() {
        val f = repo.createFolder("F", null).id
        repo.createFolder("Nested", f)
        assertThrows(IllegalArgumentException::class.java) { repo.convertContainer(f) }
        val c = collection()
        val before = count()
        assertThrows(IllegalArgumentException::class.java) { repo.transfer(listOf(f), c, true) }
        assertEquals(before, count())
        assertThrows(IllegalArgumentException::class.java) { repo.createFolder("Bad", c) }
    }

    @Test
    fun mixedSubtreeCopiesNormalizeAncestorsAndCyclesRollback() {
        val f = repo.createFolder("F", null).id
        val a = notebook(f)
        val root = notebook()
        val dest = repo.createFolder("Target", null).id
        repo.transfer(listOf(f, a, root), dest, false)
        val folder = repo.children(dest).filterIsInstance<VfsItem.Folder>().single()
        assertEquals(1, repo.children(folder.id).size)
        assertNotEquals(a, repo.children(folder.id).single().id)
        assertEquals(2, repo.children(dest).size)
        val before = count()
        assertThrows(IllegalArgumentException::class.java) {
            repo.transfer(listOf(dest), folder.id, true)
        }
        assertEquals(before, count())
        repo.transfer(listOf(f, root), dest, true)
        assertEquals(
            setOf(f, folder.id),
            repo.children(dest).filterIsInstance<VfsItem.Folder>().map { it.id }.toSet(),
        )
    }

    @Test
    fun leavingCollectionSnapshotsSharedFilesWithoutChangingCode() {
        val c = collection()
        val n = notebook(c)
        repo.putResource(c, "data.json", "{\"x\":1}".toByteArray())
        repo.putResource(n, "own.txt", "own".toByteArray())
        val source = repo.read(n)
        repo.transfer(listOf(n), null, true)
        assertEquals(source, repo.read(n))
        assertEquals("{\"x\":1}", repo.attachment(n, "shared/data.json").toString(Charsets.UTF_8))
        assertTrue(repo.children(c).isEmpty())
        assertEquals(1, repo.resourceFiles(c).size)
        val next = collection("Next")
        repo.putResource(next, "data.json", "different".toByteArray())
        repo.transfer(listOf(n), next, true)
        assertEquals("{\"x\":1}", repo.attachment(n, "shared/data.json").toString(Charsets.UTF_8))
    }

    @Test
    fun collectionPasswordProtectsMembersAndResourcesAndLockedCopiesRemainPortable() {
        val c = collection()
        val n = notebook(c)
        repo.putResource(c, "common.txt", "secret common".toByteArray())
        repo.putResource(n, "private.txt", "secret personal".toByteArray())
        repo.setPassword(c, "collection password".toCharArray())
        assertTrue(repo.entry(n).encrypted)
        val node = runBlocking { db.vfsDao().get(c)!! }
        assertTrue(PortableBundle.protected(DocumentFileStore(context).read(node.contentRef!!)))
        repo.lockAll()
        assertFalse(repo.entry(n).unlocked)
        assertTrue(repo.children(c).isEmpty())
        assertThrows(NotebookLockedException::class.java) { repo.attachment(n, "private.txt") }
        repo.transfer(listOf(c), null, false)
        val copy = repo.allFolders().single { it.id != c }
        assertFalse(copy.unlocked)
        repo.unlock(copy.id, "collection password".toCharArray())
        val child = repo.children(copy.id).single().id
        assertNotEquals(n, child)
        assertEquals(
            "secret common",
            repo.attachment(child, "shared/common.txt").toString(Charsets.UTF_8),
        )
        repo.setPassword(copy.id, "new collection password".toCharArray())
        repo.lockAll()
        assertThrows(Exception::class.java) {
            repo.unlock(copy.id, "collection password".toCharArray())
        }
        repo.unlock(copy.id, "new collection password".toCharArray())
        repo.removePassword(copy.id)
        assertEquals(
            "secret personal",
            repo.attachment(child, "private.txt").toString(Charsets.UTF_8),
        )
    }

    @Test
    fun lockedCollectionArchiveRoundTripRestoresMembersAfterUnlock() {
        val c = collection()
        val n = notebook(c)
        repo.putResource(c, "shared.txt", "abc".toByteArray())
        repo.setPassword(c, "secret password".toCharArray())
        repo.lockAll()
        val zip = file("collection.zip")
        repo.exportItems(listOf(c), Uri.fromFile(zip))
        val imported = repo.importAny(Uri.fromFile(zip), null).single()
        assertFalse(repo.allFolders().single { it.id == imported }.unlocked)
        assertTrue(repo.children(imported).isEmpty())
        repo.unlock(imported, "secret password".toCharArray())
        val child = repo.children(imported).single().id
        assertNotEquals(n, child)
        assertEquals("abc", repo.attachment(child, "shared/shared.txt").toString(Charsets.UTF_8))
    }

    @Test
    fun fullArchivePasswordAndMalformedImportNeverCreatePartialTrees() {
        val f = repo.createFolder("F", null).id
        val n = notebook(f)
        repo.putResource(n, "a.txt", "hello".toByteArray())
        val archive = file("protected.zip")
        val secret = "archive password".toCharArray()
        repo.exportItems(listOf(f), Uri.fromFile(archive), secret)
        assertTrue(secret.all { it == '\u0000' })
        val before = count()
        assertThrows(ArchivePasswordRequired::class.java) {
            repo.importAny(Uri.fromFile(archive), null)
        }
        assertThrows(Exception::class.java) {
            repo.importAny(Uri.fromFile(archive), null, "wrong password".toCharArray())
        }
        assertEquals(before, count())
        val imported =
            repo.importAny(Uri.fromFile(archive), null, "archive password".toCharArray()).single()
        val child = repo.children(imported).single().id
        assertEquals("hello", repo.attachment(child, "a.txt").toString(Charsets.UTF_8))
        val invalid = file("invalid.zip")
        val manifest =
            """{"format":"pocket-library","version":1,"nodes":[{"id":"n0","kind":"FOLDER","parent":null,"title":"Partial","file":null},{"id":"n1","kind":"NOTEBOOK","parent":"n0","title":"Bad","file":"objects/n1.bin"}]}"""
        invalid.writeBytes(
            ArchiveFiles.pack(
                mapOf(
                    "manifest.json" to manifest.toByteArray(),
                    "objects/n1.bin" to byteArrayOf(1, 2),
                )
            )
        )
        val stable = count()
        assertThrows(Exception::class.java) { repo.importAny(Uri.fromFile(invalid), null) }
        assertEquals(stable, count())
    }

    @Test
    fun exportingOneMemberCarriesItsSharedDependenciesAndProtection() {
        val c = collection()
        val n = notebook(c)
        repo.putResource(c, "common.txt", "value".toByteArray())
        val zip = file("single.zip")
        repo.exportItems(listOf(n), Uri.fromFile(zip))
        val copy = repo.importAny(Uri.fromFile(zip), null).single()
        assertEquals("value", repo.attachment(copy, "shared/common.txt").toString(Charsets.UTF_8))
        repo.setPassword(c, "secret password".toCharArray())
        val secured = file("single-secured.zip")
        repo.exportItems(listOf(n), Uri.fromFile(secured))
        val imported = repo.importAny(Uri.fromFile(secured), null).single()
        assertFalse(repo.allFolders().single { it.id == imported }.unlocked)
        repo.unlock(imported, "secret password".toCharArray())
        assertEquals(
            "value",
            repo
                .attachment(repo.children(imported).single().id, "shared/common.txt")
                .toString(Charsets.UTF_8),
        )
    }

    @Test
    fun resourceSubtreesCopyCutRenameAndRejectConflictsAtomically() {
        val a = notebook()
        val b = notebook(name = "B")
        repo.putResource(a, "images/a.png", byteArrayOf(1))
        repo.putResource(a, "images/b.txt", byteArrayOf(2))
        repo.transferResources(a, "images", b, "", false)
        assertEquals(2, repo.resourceFiles(b).size)
        assertEquals(2, repo.resourceFiles(a).size)
        repo.renameResource(b, "images", "pictures")
        assertArrayEquals(byteArrayOf(1), repo.attachment(b, "pictures/a.png"))
        assertThrows(IllegalArgumentException::class.java) {
            repo.putResource(b, "images/a.png", byteArrayOf(3))
            repo.transferResources(a, "images", b, "", true)
        }
        assertEquals(2, repo.resourceFiles(a).size)
        repo.deleteResource(b, "images")
        repo.transferResources(a, "images", b, "", true)
        assertTrue(repo.resourceFiles(a).isEmpty())
        assertEquals(4, repo.resourceFiles(b).size)
    }

    @Test
    fun resourceCyclesPathsAndFileDirectoryCollisionsAreRejected() {
        val n = notebook()
        repo.putResource(n, "dir/a.txt", byteArrayOf(1))
        for (path in listOf("../bad", "/absolute", "a/../b", "a\\b", "x:/a", "a//b")) assertThrows(
            IllegalArgumentException::class.java
        ) {
            repo.putResource(n, path, byteArrayOf(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.putResource(n, "dir", byteArrayOf(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.transferResources(n, "dir", n, "dir/sub", true)
        }
        assertEquals(listOf("dir/a.txt"), repo.resourceFiles(n).map { it.path })
    }

    @Test
    fun protectedConversionPreservesPersonalResourcesAndRemovesOnlyCollectionPassword() {
        val c = collection()
        val n = notebook(c)
        repo.putResource(n, "a.txt", "a".toByteArray())
        repo.setPassword(c, "secret password".toCharArray())
        repo.convertContainer(c)
        assertFalse(repo.allFolders().single().collection)
        assertFalse(repo.entry(n).encrypted)
        assertEquals("a", repo.attachment(n, "a.txt").toString(Charsets.UTF_8))
        repo.convertContainer(c)
        assertTrue(repo.allFolders().single().collection)
    }

    @Test
    fun oldVersionOneNotebookPackagesStillImportAndGainResources() {
        val source = "<notebook><title>Legacy</title></notebook>"
        val meta = NotebookMetadataCodec.read(source)
        val doc =
            JSONObject()
                .put("format", "notebook-pocket")
                .put("formatVersion", 1)
                .put("title", meta.title)
                .put("description", meta.description)
                .put("author", meta.author)
                .put("version", meta.version)
                .put("coverMime", JSONObject.NULL)
                .put("coverSha256", JSONObject.NULL)
                .put("encrypted", false)
        val zip = file("old.npkg")
        zip.writeBytes(
            ArchiveFiles.pack(
                mapOf(
                    "manifest.json" to doc.toString().toByteArray(),
                    "content.html" to source.toByteArray(),
                )
            )
        )
        val id = repo.importAny(Uri.fromFile(zip), null).single()
        repo.putResource(id, "new.txt", "new".toByteArray())
        assertEquals(source, repo.read(id))
        assertEquals("new", repo.attachment(id, "new.txt").toString(Charsets.UTF_8))
    }

    @Test
    fun movingOriginalIntoItsCollectionCopyCannotOverwriteCopiedMember() {
        val c = collection()
        val original = notebook(c)
        repo.putResource(original, "value.txt", "original".toByteArray())
        repo.transfer(listOf(c), null, false)
        val copy = repo.allFolders().single { it.id != c }.id
        val copied = repo.children(copy).single().id
        repo.putResource(copied, "extra.txt", "copy".toByteArray())
        repo.transfer(listOf(original), copy, true)
        assertEquals(setOf(original, copied), repo.children(copy).map { it.id }.toSet())
        assertEquals("copy", repo.attachment(copied, "extra.txt").toString(Charsets.UTF_8))
        assertThrows(Exception::class.java) { repo.attachment(original, "extra.txt") }
    }

    @Test
    fun failedMixedCopyLeavesNoPartialDocumentsOrFileRevisions() {
        val first = notebook()
        val c = collection()
        val locked = notebook(c, "Locked")
        repo.putResource(c, "shared.txt", "shared".toByteArray())
        repo.setPassword(locked, "document password".toCharArray())
        repo.lockAll()
        val target = repo.createFolder("Target", null).id
        val before = count()
        val fileCount = File(context.filesDir, "document-files").walkTopDown().count { it.isFile }
        assertThrows(NotebookLockedException::class.java) {
            repo.transfer(listOf(first, locked), target, false)
        }
        assertEquals(before, count())
        assertTrue(repo.children(target).isEmpty())
        assertEquals(
            fileCount,
            File(context.filesDir, "document-files").walkTopDown().count { it.isFile },
        )
    }

    @Test
    fun changingPasswordOnImportedCollectionResetsKdfParametersTogether() {
        val c = collection()
        notebook(c)
        val original = runBlocking { db.vfsDao().get(c)!! }
        val plain = DocumentFileStore(context).read(original.contentRef!!)
        val salt = dev.pocket.notebook.data.security.NotebookCrypto.randomBytes(16)
        val key =
            dev.pocket.notebook.data.security.NotebookCrypto.derive(
                "old password".toCharArray(),
                salt,
                600001,
            )
        val sealed =
            try {
                PortableBundle.seal(plain, key, salt, "collection", 600001)
            } finally {
                key.fill(0)
            }
        val manifest =
            """{"format":"pocket-library","version":1,"nodes":[{"id":"n0","kind":"COLLECTION","parent":null,"title":"Imported","file":"objects/n0.bin"}]}"""
        val archive = file("custom-kdf.zip")
        archive.writeBytes(
            ArchiveFiles.pack(
                mapOf("manifest.json" to manifest.toByteArray(), "objects/n0.bin" to sealed)
            )
        )
        val id = repo.importAny(Uri.fromFile(archive), null).single()
        repo.unlock(id, "old password".toCharArray())
        repo.setPassword(id, "new password".toCharArray())
        repo.lockAll()
        repo.unlock(id, "new password".toCharArray())
        assertEquals(1, repo.children(id).size)
    }
}
