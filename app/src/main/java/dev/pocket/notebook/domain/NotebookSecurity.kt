package dev.pocket.notebook.domain

import dev.pocket.notebook.data.*

/**
 * Public NotebookSecurity contract. Calls serialize storage access; invoke IO work off the UI
 * thread.
 */
interface NotebookSecurity {
    fun securityOwner(id: String): String

    fun setPassword(id: String, password: CharArray)

    fun unlock(id: String, password: CharArray)

    fun removePassword(requestedId: String)

    fun lockAll()
}
