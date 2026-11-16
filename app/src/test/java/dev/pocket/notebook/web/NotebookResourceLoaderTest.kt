package dev.pocket.notebook.web

import dev.pocket.notebook.data.TestDraftCipher
import android.net.Uri
import androidx.room.Room
import dev.pocket.notebook.data.*
import dev.pocket.notebook.data.local.NotebookDatabase
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotebookResourceLoaderTest {
    @Test
    fun scopedPreviewServesOnlyOwnAndSharedResourcesAndRevokesAccessOnLock() {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, NotebookDatabase::class.java).build()
        try {
            val repo = NotebookRepository(context, db, TestDraftCipher)
            val c = repo.createCollection("C", null).id
            val a = repo.create("en", c).id
            val b = repo.create("en").id
            repo.putResource(a, "own.txt", "own".toByteArray())
            repo.putResource(b, "secret.txt", "other notebook".toByteArray())
            repo.putResource(c, "common.txt", "shared".toByteArray())
            val image =
                android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            val png =
                java.io
                    .ByteArrayOutputStream()
                    .also { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    .toByteArray()
            repo.putResource(a, "images/picture.png", png)
            val loader = NotebookResourceLoader(repo, a, "scope-a")
            val base = "https://appassets.androidplatform.net/notebook-resources/scope-a/"
            val scopedHost = previewHost(a)
            val isolatedLoader = NotebookResourceLoader(repo, a, "scope-a", scopedHost)
            val isolatedBase = "https://$scopedHost/notebook-resources/scope-a/"
            assertEquals(
                "own",
                isolatedLoader.read(Uri.parse(isolatedBase + "own.txt"))!!
                    .data.readBytes().toString(Charsets.UTF_8),
            )
            assertNull(isolatedLoader.read(Uri.parse(base + "own.txt")))
            val picture = loader.read(Uri.parse(base + "images/picture.png"))!!
            assertEquals("image/png", picture.mimeType)
            val served = picture.data.readBytes()
            assertEquals(
                2,
                android.graphics.BitmapFactory.decodeByteArray(served, 0, served.size).width,
            )
            val own = loader.read(Uri.parse(base + "own.txt"))!!
            assertEquals("own", own.data.readBytes().toString(Charsets.UTF_8))
            assertEquals("no-store", own.responseHeaders["Cache-Control"])
            assertEquals(
                "shared",
                loader
                    .read(Uri.parse(base + "shared/common.txt"))!!
                    .data
                    .readBytes()
                    .toString(Charsets.UTF_8),
            )
            for (url in
                listOf(
                    base + "secret.txt",
                    base + "../secret.txt",
                    base.replace("scope-a", "scope-b") + "own.txt",
                    base.replace("appassets.androidplatform.net", "example.com") + "own.txt",
                )) assertNull(loader.read(Uri.parse(url)))
            repo.setPassword(c, "secret password".toCharArray())
            repo.lockAll()
            assertNull(loader.read(Uri.parse(base + "own.txt")))
            repo.unlock(c, "secret password".toCharArray())
            assertNotNull(loader.read(Uri.parse(base + "own.txt")))
            repo.lockAll()
        } finally {
            db.close()
        }
    }
}
