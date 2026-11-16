package dev.pocket.notebook.feature.files

import dev.pocket.notebook.data.TestDraftCipher
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import dev.pocket.notebook.data.*
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.feature.library.NotebookLibraryScreen
import dev.pocket.notebook.feature.library.state.LibraryViewModel
import dev.pocket.notebook.i18n.stringsFor
import dev.pocket.notebook.ui.theme.NotebookTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResourcesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: NotebookDatabase
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @After
    fun close() {
        scope.cancel()
        if (::db.isInitialized) db.close()
    }

    private fun show(language: String): PocketTools {
        db = Room.inMemoryDatabaseBuilder(compose.activity, NotebookDatabase::class.java).build()
        val repo = NotebookRepository(compose.activity, db, TestDraftCipher)
        val c = repo.createCollection("Recipes", null).id
        val fixture = java.io.File(compose.activity.cacheDir, "ui.html")
        fixture.writeText("<notebook><title>Recipe</title></notebook>")
        repo.importDocument(android.net.Uri.fromFile(fixture), c)
        val picture =
            android.graphics.Bitmap.createBitmap(64, 48, android.graphics.Bitmap.Config.ARGB_8888)
        picture.eraseColor(android.graphics.Color.BLUE)
        val bytes = java.io.ByteArrayOutputStream()
        picture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
        picture.recycle()
        repo.putResource(c, "images/photo.png", bytes.toByteArray())
        repo.putResource(c, "data.json", "{}".toByteArray())
        val state = LibraryViewModel(SavedStateHandle())
        state.openFolder(c)
        val t = PocketTools(repo, compose.activity, scope, {}, { _, _ -> })
        t.language = language
        val strings = stringsFor(compose.activity, language)
        compose.setContent {
            NotebookTheme(false) {
                CompositionLocalProvider(LocalPocketTools provides t) {
                    Surface(Modifier.fillMaxSize()) {
                        NotebookLibraryScreen(
                            state,
                            strings,
                            language,
                            t.revision,
                            repo::children,
                            repo::folderPath,
                            repo::allFolders,
                            null,
                            repo::readCover,
                            {},
                            {},
                            { _, _ -> },
                            {},
                            {},
                            {},
                            { _, _ -> },
                            { _, _ -> },
                            { _, _ -> },
                            {},
                        )
                        PocketToolsHost()
                    }
                }
            }
        }
        return t
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view =
                org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView
                    ?: compose.activity.window.decorView
            if (view.width > 0 && view.height > 0) {
                val bitmap =
                    android.graphics.Bitmap.createBitmap(
                        view.width,
                        view.height,
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                view.draw(android.graphics.Canvas(bitmap))
                val folder = java.io.File("build/previews").apply { mkdirs() }
                java.io.File(folder, "$name.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    @Test
    fun phoneOpensSharedResourcesAndShowsCorrectObservablePaths() {
        val tools = show("en")
        compose.onNodeWithTag("library-actions-toggle").assertDoesNotExist()
        screenshot("phone-library-actions-en")
        compose.onNodeWithContentDescription("New folder").assertDoesNotExist()
        val shared =
            compose
                .onNodeWithContentDescription("Shared collection resources")
                .fetchSemanticsNode()
                .boundsInRoot
        val password =
            compose.onNodeWithContentDescription("Set password").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(kotlin.math.abs(password.top - shared.top) < 0.5f)
        compose.runOnIdle { tools.selected = setOf(tools.repo.list().single().id) }
        compose.onNodeWithContentDescription("Shared collection resources").assertDoesNotExist()
        compose.onNodeWithContentDescription("Copy").performClick()
        compose.onNodeWithContentDescription("Paste").assertIsDisplayed()
        compose.onNodeWithContentDescription("Shared collection resources").performClick()
        compose.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithText("data.json").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("FileAttachment(\"shared/data.json\")").assertExists()
        compose.onNodeWithContentDescription("Import file").assertIsDisplayed()
        screenshot("phone-resources-en")
        compose.onNodeWithText("images").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("photo.png").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("FileAttachment(\"shared/images/photo.png\")").assertExists()
    }

    @Test
    @Config(qualifiers = "ru-rRU-w1100dp-h820dp-land-mdpi")
    fun tabletKeepsRussianLibraryAndSharedResourceActionsAccessible() {
        val tools = show("ru")
        compose.onNodeWithTag("library-actions-toggle").assertDoesNotExist()
        screenshot("tablet-library-actions-ru")
        compose
            .onNodeWithContentDescription("Общие ресурсы коллекции")
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(2, tools.repo.resourceFiles(tools.resourceOwner!!).size)
        }
        compose.onNodeWithTag("resource-files").assertExists()
        compose.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithText("data.json").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Импортировать файл").assertIsDisplayed()
        screenshot("tablet-resources-ru")
        compose.onAllNodesWithContentDescription("Действия").onFirst().assertIsDisplayed()
    }

    @Test
    fun resourcesNavigateHistoryCreateFoldersAndOpenTextInAcode() {
        val tools = show("en")
        compose.onNodeWithContentDescription("Shared collection resources").performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("images").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("images").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("photo.png").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("data.json").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Forward").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("photo.png").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Parent folder").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("data.json").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("resource-new-folder").performClick()
        compose.onNodeWithTag("file-name").performTextInput("empty")
        compose.onNodeWithTag("file-name-save").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("This folder is empty").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle {
            Assert.assertTrue(tools.repo.resourceEntry(tools.resourceOwner!!, "empty").directory)
        }
        compose.onNodeWithTag("resource-go-path").performClick()
        compose.onNodeWithTag("resource-path-input").performTextReplacement("/")
        compose.onNodeWithText("Open").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("data.json").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("data.json").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("resource-editor-save").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Save as").assertIsDisplayed()
        compose.onNodeWithTag("resource-editor-save").assertIsNotEnabled()
    }

    @Test
    fun resourceImagesOpenAsImagesWithResetControls() {
        show("en")
        compose.onNodeWithContentDescription("Shared collection resources").performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("images").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("images").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("photo.png").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("photo.png").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("resource-image-preview").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("resource-image-preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reset zoom").performClick()
        compose.onNodeWithContentDescription("Save as").assertDoesNotExist()
    }
}
