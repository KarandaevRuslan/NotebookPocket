package dev.pocket.notebook.feature.files

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.feature.files.components.ResourceScopeSelector
import dev.pocket.notebook.ui.theme.NotebookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-rUS-w320dp-h640dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResourceScopeSelectorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(notebook: String, shared: String) {
        var isShared by mutableStateOf(false)
        compose.setContent {
            NotebookTheme(false) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    ResourceScopeSelector(
                        notebookLabel = notebook,
                        sharedLabel = shared,
                        selectedShared = isShared,
                        onSelectNotebook = { isShared = false },
                        onSelectShared = { isShared = true },
                    )
                }
            }
        }
    }

    private fun checkSizeAndSelection() {
        compose.onNodeWithTag("resource-scope-notebook").assertIsSelected()
        compose.onNodeWithTag("resource-scope-shared").assertIsNotSelected()
        val container = compose.onNodeWithTag("resource-scope-selector").fetchSemanticsNode().boundsInRoot
        val left = compose.onNodeWithTag("resource-scope-notebook").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("resource-scope-shared").fetchSemanticsNode().boundsInRoot
        assertTrue("First segment outside parent", left.left >= container.left - 2f)
        assertTrue("Second segment outside parent", right.right <= container.right + 2f)
        assertTrue("Segments should have equal width", abs(left.width - right.width) <= 3f)
        assertTrue("Segments overlap too much", left.right <= right.left + 4f)
        compose.onNodeWithTag("resource-scope-shared").performClick().assertIsSelected()
        compose.onNodeWithTag("resource-scope-notebook").performClick().assertIsSelected()
    }

    @Test
    fun englishPortraitDoesNotOverflowAndCanChangeScope() {
        render("Notebook resources", "Shared collection resources")
        checkSizeAndSelection()
    }

    @Test
    @Config(qualifiers = "ru-rRU-w320dp-h640dp-port-mdpi")
    fun russianPortraitDoesNotOverflowAndCanChangeScope() {
        render("Ресурсы инструкции", "Общие ресурсы коллекции")
        checkSizeAndSelection()
    }
}
