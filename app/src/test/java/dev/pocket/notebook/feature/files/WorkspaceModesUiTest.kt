package dev.pocket.notebook.feature.files

import dev.pocket.notebook.data.TestDraftCipher
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import dev.pocket.notebook.data.NotebookRepository
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.feature.workspace.components.WorkspaceToolbar
import dev.pocket.notebook.i18n.stringsFor
import dev.pocket.notebook.ui.theme.NotebookTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "ru-rRU-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceModesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: NotebookDatabase
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @After
    fun close() {
        scope.cancel()
        if (::db.isInitialized) db.close()
    }

    @Test
    fun resourcesShareThePickerAndOpenInsideTheDocument() {
        db = Room.inMemoryDatabaseBuilder(compose.activity, NotebookDatabase::class.java).build()
        val repo = NotebookRepository(compose.activity, db, TestDraftCipher)
        val doc = repo.create("ru").id
        val tools = PocketTools(repo, compose.activity, scope, {}, { _, _ -> })
        tools.language = "ru"
        val strings = stringsFor(compose.activity, "ru")
        compose.setContent {
            NotebookTheme(false) {
                CompositionLocalProvider(LocalPocketTools provides tools) {
                    var mode by remember { mutableIntStateOf(1) }
                    Surface(Modifier.fillMaxSize()) {
                        Column {
                            WorkspaceToolbar(
                                "Блины",
                                false,
                                mode,
                                { mode = it },
                                false,
                                strings,
                                {},
                                {},
                                {},
                                {},
                            )
                            if (mode == 2) ResourceManager(doc, embedded = true) { mode = 1 }
                        }
                    }
                }
            }
        }
        val tabs = (0..2).map { compose.onNodeWithTag("workspace-mode-$it") }
        tabs.forEach { it.assertIsDisplayed() }
        Assert.assertEquals(1, tabs.map { it.fetchSemanticsNode().boundsInRoot.top }.toSet().size)
        compose.onNodeWithTag("workspace-mode-2").performClick().assertIsSelected()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("i18n").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Импортировать файл").assertIsDisplayed()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap =
                android.graphics.Bitmap.createBitmap(
                    view.width,
                    view.height,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
            view.draw(android.graphics.Canvas(bitmap))
            val folder = java.io.File("build/previews").apply { mkdirs() }
            java.io.File(folder, "phone-workspace-resources-ru.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("workspace-mode-1").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Импортировать файл").assertDoesNotExist()
    }
}
