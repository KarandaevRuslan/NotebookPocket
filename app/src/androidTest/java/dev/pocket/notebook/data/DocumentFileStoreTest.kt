package dev.pocket.notebook.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pocket.notebook.data.local.DocumentFileStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentFileStoreTest {
    @Test fun versionedWritesKeepOldRevisionUntilExplicitCleanup() {
        val store = DocumentFileStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val id = UUID.randomUUID().toString()
        try {
            val initial = store.write(id, "first".toByteArray(), "html")
            val changed = store.write(id, "second".toByteArray(), "html")
            assertNotEquals(initial, changed)
            assertArrayEquals("first".toByteArray(), store.read(initial))
            assertArrayEquals("second".toByteArray(), store.read(changed))
            store.deleteRef(initial)
            assertArrayEquals("second".toByteArray(), store.read(changed))
            assertFalse(runCatching { store.read("../../settings") }.isSuccess)
        } finally { store.deleteDocument(id) }
    }
}
