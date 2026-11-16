@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pocket.notebook.feature.files.state

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.pocket.notebook.data.packing.ArchiveFiles

/**
 * Transient history: private resource paths are never stored in preferences or a saved-state
 * Bundle.
 */
internal class ResourceNavigation {
    var path by mutableStateOf("")
        private set

    private val previous = mutableStateListOf<String>()
    private val next = mutableStateListOf<String>()
    val canBack
        get() = previous.isNotEmpty()

    val canForward
        get() = next.isNotEmpty()

    fun go(value: String) {
        if (value.isNotEmpty()) ArchiveFiles.path(value)
        if (value == path) return
        previous.add(path)
        if (previous.size > 100) previous.removeAt(0)
        next.clear()
        path = value
    }

    fun back() {
        if (canBack) {
            next.add(path)
            path = previous.removeAt(previous.lastIndex)
        }
    }

    fun forward() {
        if (canForward) {
            previous.add(path)
            path = next.removeAt(next.lastIndex)
        }
    }

    fun up() = go(path.substringBeforeLast('/', ""))
}
