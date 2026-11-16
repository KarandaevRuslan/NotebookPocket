package dev.pocket.notebook.web

import android.view.View
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two retained native WebViews: active mode controls visibility, input and accessibility.
 * Robolectric has no Chromium frame provider; this verifies native visibility, not GPU pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
class WorkspaceWebViewVisibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchingViewCodeResourcesHidesNativeSurfacesAndRetainsTheirInstances() {
        var mode by mutableIntStateOf(1)
        lateinit var preview: WebView
        lateinit var editor: WebView
        var previewsCreated = 0
        var editorsCreated = 0
        compose.setContent {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = {
                            WebView(it).also { v ->
                                editor = v
                                editorsCreated++
                            }
                        },
                        update = { updateWorkspaceWebViewVisibility(it, mode == 0) },
                        onRelease = { it.destroy() },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // This is the problematic order: Preview would cover Code without native
                    // hiding.
                    AndroidView(
                        factory = {
                            WebView(it).also { v ->
                                preview = v
                                previewsCreated++
                            }
                        },
                        update = { updateWorkspaceWebViewVisibility(it, mode == 1) },
                        onRelease = { it.destroy() },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (mode == 2) Surface(Modifier.fillMaxSize()) { Text("Resources pane") }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(preview.isShown)
            assertFalse(editor.isShown)
        }
        val originalPreview = preview
        val originalEditor = editor
        for (selected in listOf(0, 2, 1, 2, 0, 1)) {
            compose.runOnIdle { mode = selected }
            compose.waitForIdle()
            compose.runOnIdle {
                assertSame(originalPreview, preview)
                assertSame(originalEditor, editor)
                assertEquals(1, previewsCreated)
                assertEquals(1, editorsCreated)
                assertEquals(selected == 1, preview.isShown)
                assertEquals(selected == 0, editor.isShown)
                assertEquals(selected == 1, preview.isEnabled)
                if (selected != 1) {
                    assertEquals(View.INVISIBLE, preview.visibility)
                    assertEquals(
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                        preview.importantForAccessibility,
                    )
                    assertFalse(preview.hasFocus())
                }
            }
            if (selected == 2) compose.onNodeWithText("Resources pane").assertIsDisplayed()
        }
    }
}
