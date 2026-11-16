package dev.pocket.notebook.data.facade

import dev.pocket.notebook.data.security.DocumentSecurity
import dev.pocket.notebook.data.*
import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.storage.StorageAccess
import dev.pocket.notebook.domain.NotebookSecurity

internal class SecurityFacade(
    private val executor: BlockingRepositoryExecutor,
    private val security: DocumentSecurity,
    private val storage: StorageAccess,
    private val collections: CollectionStore,
) : NotebookSecurity {
    override fun securityOwner(id: String): String = executor.execute { security.securityOwner(id) }

    override fun setPassword(id: String, password: CharArray) =
        executor.execute { security.setPassword(id, password) }

    override fun unlock(id: String, password: CharArray) =
        executor.execute { security.unlock(id, password) }

    override fun removePassword(requestedId: String) =
        executor.execute { security.removePassword(requestedId) }

    override fun lockAll() =
        executor.execute {
            storage.keyCache.clear()
            collections.clear()
        }
}
