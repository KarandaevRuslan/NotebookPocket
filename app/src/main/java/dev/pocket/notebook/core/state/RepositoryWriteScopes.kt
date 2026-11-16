package dev.pocket.notebook.core.state

import dev.pocket.notebook.domain.NotebookGateway
import java.util.IdentityHashMap

/** One process-owned queue per repository, retained across Activity and route recreation. */
internal object RepositoryWriteScopes {
    private val writers = IdentityHashMap<NotebookGateway, DocumentWriteCoordinator>()

    @Synchronized
    fun forRepository(repository: NotebookGateway): DocumentWriteCoordinator =
        writers.getOrPut(repository) {
            DocumentWriteCoordinator({ id, source -> repository.save(id, source) })
        }
}
