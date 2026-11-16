package dev.pocket.notebook.web

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PreviewIsolationTest {
    @Test
    fun editorAndEachPreviewUseSeparateOriginsAndProtectedPreviewDisablesStorage() {
        assertNotEquals(EDITOR_HOST, previewHost("first"))
        assertNotEquals(previewHost("first"), previewHost("second"))
        assertEquals(previewHost("first"), previewHost("first"))
        val context = RuntimeEnvironment.getApplication()
        val view =
            createNotebookWebView(
                context,
                LocalAssetsClient(context, originHost = previewHost("first")),
                false,
            )
        try {
            assertFalse(view.settings.domStorageEnabled)
            applyPreviewStoragePolicy(view, previewHost("first"), false)
            assertTrue(view.settings.domStorageEnabled)
            applyPreviewStoragePolicy(view, previewHost("first"), true)
            assertFalse(view.settings.domStorageEnabled)
            assertTrue(
                webPageUrl("preview.html", false, "en", previewHost("first"))
                    .startsWith("https://${previewHost("first")}/")
            )
        } finally {
            view.destroy()
        }
    }
}
