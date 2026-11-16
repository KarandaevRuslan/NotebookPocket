package dev.pocket.notebook.feature.files

import dev.pocket.notebook.feature.files.state.ResourceNavigation
import dev.pocket.notebook.feature.files.viewer.decodeResourceImage
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResourceMediaTest {
    @Test
    fun largeImageIsSampledWithoutChangingOriginalDimensionsOrBytes() {
        val bitmap = Bitmap.createBitmap(4096, 512, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        val bytes = output.toByteArray()
        val original = bytes.copyOf()
        val result = decodeResourceImage(bytes)!!
        assertEquals(4096, result.width)
        assertEquals(512, result.height)
        assertTrue(result.bitmap.width <= 2048)
        assertTrue(result.bitmap.height <= 2048)
        assertArrayEquals(original, bytes)
        result.bitmap.recycle()
        assertNull(decodeResourceImage("not an image".toByteArray()))
    }

    @Test
    fun navigationHistorySupportsBreadcrumbsUpAndNewBranches() {
        val nav = ResourceNavigation()
        nav.go("one/two/three")
        nav.go("one")
        nav.back()
        assertEquals("one/two/three", nav.path)
        assertTrue(nav.canForward)
        nav.forward()
        assertEquals("one", nav.path)
        nav.up()
        assertEquals("", nav.path)
        nav.back()
        nav.go("other")
        assertFalse(nav.canForward)
        assertThrows(IllegalArgumentException::class.java) { nav.go("../bad") }
        assertEquals("other", nav.path)
    }
}
