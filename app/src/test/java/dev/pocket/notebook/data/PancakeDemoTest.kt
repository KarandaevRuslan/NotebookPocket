package dev.pocket.notebook.data

import android.net.Uri
import androidx.room.Room
import dev.pocket.notebook.R
import dev.pocket.notebook.data.demo.PancakeDemo
import dev.pocket.notebook.data.local.NotebookDatabase
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PancakeDemoTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var db: NotebookDatabase
    private lateinit var repo: NotebookRepository

    @Before
    fun setup() {
        context.getSharedPreferences("demo-upgrades", 0).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, NotebookDatabase::class.java).build()
        repo = NotebookRepository(context, db, TestDraftCipher)
    }

    @After
    fun close() {
        repo.lockAll()
        db.close()
    }

    private fun legacy() =
        context.resources.openRawResource(R.raw.sample_pancakes).bufferedReader().use {
            it.readText()
        }

    private fun import(source: String): String {
        val f = File(context.cacheDir, "demo-${java.util.UUID.randomUUID()}.html")
        f.writeText(source)
        return repo.importDocument(Uri.fromFile(f)).id
    }

    @Test
    fun sameCellsAndFiveAttachmentsSurviveExportInBothLanguages() {
        val en = repo.create("en").id
        val ru = repo.create("ru").id
        val cells = Regex("<script[\\s\\S]*")
        assertEquals(cells.find(repo.read(en))!!.value, cells.find(repo.read(ru))!!.value)
        assertNotEquals(
            NotebookMetadataCodec.read(repo.read(en)).title,
            NotebookMetadataCodec.read(repo.read(ru)).title,
        )
        val resources = PancakeDemo.resources(context)
        assertEquals(5, resources.size)
        for (name in resources.keys) assertArrayEquals(resources[name], repo.attachment(en, name))
        assertTrue(repo.read(en).contains("Pocket.language"))
        assertTrue(repo.read(ru).contains("pocketNow"))
        val archive = File(context.cacheDir, "demo-export.zip")
        repo.exportItems(listOf(en), Uri.fromFile(archive))
        val restored = repo.importAny(Uri.fromFile(archive), null).single()
        for (name in resources.keys) assertArrayEquals(
            resources[name],
            repo.attachment(restored, name),
        )
    }

    @Test
    fun migrationOnlyReplacesUntouchedPublicDemoWithoutAttachments() {
        val original = legacy()
        val untouched = import(original)
        val edited = import(original + "\n<!-- My recipe -->")
        val withResource = import(original)
        repo.putResource(withResource, "mine.txt", "mine".toByteArray())
        val protected = import(original)
        repo.setPassword(protected, "demo-password".toCharArray())
        repo.lockAll()
        assertTrue(repo.migrateUntouchedLegacyDemo("ru"))
        assertEquals(PancakeDemo.resources(context).keys, repo.attachmentNames(untouched).toSet())
        assertTrue(repo.read(untouched).contains("Pocket.timer"))
        assertEquals(original + "\n<!-- My recipe -->", repo.read(edited))
        assertEquals(original, repo.read(withResource))
        assertThrows(NotebookLockedException::class.java) { repo.read(protected) }
        repo.unlock(protected, "demo-password".toCharArray())
        assertEquals(original, repo.read(protected))
        assertFalse(repo.migrateUntouchedLegacyDemo("en"))
    }

    @Test
    fun nexusDemoUpgradeMigratesOnlyUntouchedV6AndKeepsCustomDocuments() {
        val previous =
            context.assets.open("demo/pancakes/upgrade/v6-source.html").bufferedReader().use {
                it.readText()
            }
        val oldResources =
            PancakeDemo.resources(context) +
                mapOf(
                    "styles/pancakes.css" to
                        javaClass.classLoader!!.getResourceAsStream("pancakes-v6.css")!!.use {
                            it.readBytes()
                        },
                    "i18n/en.json" to
                        javaClass.classLoader!!.getResourceAsStream("pancakes-v6-en.json")!!.use {
                            it.readBytes()
                        },
                    "i18n/ru.json" to
                        javaClass.classLoader!!.getResourceAsStream("pancakes-v6-ru.json")!!.use {
                            it.readBytes()
                        },
                )
        val metadata = NotebookMetadata("My pancakes", "Keep my description", "Chef", "2.0")
        val source = NotebookMetadataCodec.update(previous, metadata)
        fun createOld(code: String, resources: Map<String, ByteArray>): String {
            val file = File(context.cacheDir, "v6-${java.util.UUID.randomUUID()}.npkg")
            file.writeBytes(
                dev.pocket.notebook.data.packing.NotebookPackage.pack(
                    code, NotebookMetadataCodec.read(code), null, null, resources = resources,
                )
            )
            return repo.importDocument(Uri.fromFile(file)).id
        }
        val untouched = createOld(source, oldResources)
        val customCode = createOld(source + "\n<!-- mine -->", oldResources)
        val customResource = createOld(source, oldResources + ("i18n/ru.json" to "mine".toByteArray()))
        // This flag was already stored by v0.9.3; it must not suppress the Nexus upgrade.
        context.getSharedPreferences("demo-upgrades", 0).edit()
            .putBoolean("pancakes-v6", true).commit()
        assertTrue(PancakeDemo.canUpgradeV6(context, source, oldResources))
        assertFalse(PancakeDemo.canUpgradeV6(context, source + "\n<!-- mine -->", oldResources))
        assertTrue(repo.migrateUntouchedLegacyDemo("ru"))
        assertEquals(metadata, NotebookMetadataCodec.read(repo.read(untouched)))
        assertTrue(repo.read(untouched).contains("pancakeScope = nexus.scope(invalidation)"))
        assertEquals(source + "\n<!-- mine -->", repo.read(customCode))
        assertEquals(source, repo.read(customResource))
        assertEquals(PancakeDemo.resources(context).keys, repo.attachmentNames(untouched).toSet())
        assertArrayEquals(PancakeDemo.resources(context).getValue("i18n/en.json"),
            repo.attachment(untouched, "i18n/en.json"))
        assertFalse(repo.migrateUntouchedLegacyDemo("ru"))
    }

    @Test
    fun migrationUpgradesV5PalettesButPreservesUserCodeResourcesAndMetadata() {
        val previous =
            context.assets.open("demo/pancakes/upgrade/v5-source.html").bufferedReader().use {
                it.readText()
            }
        val oldResources =
            PancakeDemo.resources(context) +
                mapOf(
                    "styles/pancakes.css" to
                        javaClass.classLoader!!.getResourceAsStream("pancakes-v5.css")!!.use {
                            it.readBytes()
                        },
                    // The old v5 and v6 demos used the same translation bytes.
                    "i18n/en.json" to
                        javaClass.classLoader!!.getResourceAsStream("pancakes-v6-en.json")!!.use {
                            it.readBytes()
                        },
                    "i18n/ru.json" to
                        javaClass.classLoader!!.getResourceAsStream("pancakes-v6-ru.json")!!.use {
                            it.readBytes()
                        },
                )
        val metadata = NotebookMetadata("My pancakes", "Personal title only", "Me", "custom")
        val source = NotebookMetadataCodec.update(previous, metadata)
        fun packet(code: String, resources: Map<String, ByteArray>): String {
            val file = File(context.cacheDir, "old-${java.util.UUID.randomUUID()}.npkg")
            file.writeBytes(
                dev.pocket.notebook.data.packing.NotebookPackage.pack(
                    code,
                    NotebookMetadataCodec.read(code),
                    null,
                    null,
                    resources = resources,
                )
            )
            return repo.importDocument(Uri.fromFile(file)).id
        }
        val original = packet(source, oldResources)
        val editedCode = packet(source + "\n<!-- changed -->", oldResources)
        val customCss = oldResources + ("styles/pancakes.css" to "/* My palette */".toByteArray())
        val editedCss = packet(source, customCss)
        // The old migration preference does not suppress this specific new upgrade.
        context
            .getSharedPreferences("demo-upgrades", 0)
            .edit()
            .putBoolean("pancakes-v5", true)
            .commit()
        assertTrue(repo.migrateUntouchedLegacyDemo("ru"))
        assertEquals(metadata, NotebookMetadataCodec.read(repo.read(original)))
        assertTrue(repo.read(original).contains("Pocket.theme"))
        assertArrayEquals(
            PancakeDemo.resources(context).getValue("styles/pancakes.css"),
            repo.attachment(original, "styles/pancakes.css"),
        )
        assertEquals(source + "\n<!-- changed -->", repo.read(editedCode))
        assertEquals(source, repo.read(editedCss))
        assertArrayEquals(
            customCss.getValue("styles/pancakes.css"),
            repo.attachment(editedCss, "styles/pancakes.css"),
        )
        assertFalse(repo.migrateUntouchedLegacyDemo("en"))
    }
}
