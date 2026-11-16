package dev.pocket.notebook.data.facade

import dev.pocket.notebook.data.demo.DemoUpgrade
import dev.pocket.notebook.data.*
import dev.pocket.notebook.domain.NotebookMaintenance

internal class MaintenanceFacade(
    private val executor: BlockingRepositoryExecutor,
    private val demoUpgrade: DemoUpgrade,
) : NotebookMaintenance {
    override fun migrateUntouchedLegacyDemo(language: String): Boolean =
        executor.execute { demoUpgrade.migrateUntouchedLegacyDemo(language) }
}
