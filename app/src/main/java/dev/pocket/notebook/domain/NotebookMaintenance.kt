package dev.pocket.notebook.domain

import dev.pocket.notebook.data.*

/**
 * Public NotebookMaintenance contract. Calls serialize storage access; invoke IO work off the UI
 * thread.
 */
interface NotebookMaintenance {
    fun migrateUntouchedLegacyDemo(language: String): Boolean
}
