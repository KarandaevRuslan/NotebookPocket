package dev.pocket.notebook.data

import androidx.lifecycle.SavedStateHandle
import dev.pocket.notebook.feature.library.state.LibraryViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibraryViewModelTest {
    @Test
    fun folderAndSearchRestoreFromSavedState() {
        val state = SavedStateHandle()
        val original = LibraryViewModel(state)
        original.openFolder("nested-folder-id")
        original.updateQuery("test")

        // Simulates restoring the view model from the same SavedStateHandle after
        // changing configuration or navigating back from a workspace destination.
        val restored = LibraryViewModel(state)
        assertEquals("nested-folder-id", restored.folderId)
        assertEquals("test", restored.query)
    }
}
