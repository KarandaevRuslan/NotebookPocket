package dev.pocket.notebook.feature.library.state

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/**
 * The library's navigation and search state lives above the NavHost destinations. It therefore
 * survives workspace navigation, configuration changes and process recreation.
 */
class LibraryViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    var folderId by mutableStateOf(savedState.get<String>("folderId"))
        private set

    var query by mutableStateOf(savedState.get<String>("query") ?: "")
        private set

    fun openFolder(id: String?) {
        folderId = id
        savedState["folderId"] = id
        updateQuery("")
    }

    fun updateQuery(value: String) {
        query = value
        savedState["query"] = value
    }
}
