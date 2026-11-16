package dev.pocket.notebook.data

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocket.notebook.feature.library.state.LibraryViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
