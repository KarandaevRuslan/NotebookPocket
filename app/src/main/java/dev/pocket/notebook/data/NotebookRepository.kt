package dev.pocket.notebook.data

import android.content.Context
import dev.pocket.notebook.data.draft.DraftCipher
import dev.pocket.notebook.data.facade.*
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.data.packing.NotebookPackage
import dev.pocket.notebook.domain.*

/** Compatible public entry point; each concern delegates to its own port adapter. */
class NotebookRepository private constructor(services: RepositoryServices) :
    NotebookGateway,
    NotebookDocuments by DocumentFacade(
        services.executor,
        services.storage,
        services.content,
        services.editor,
    ),
    LibraryCatalogAccess by CatalogFacade(services.executor, services.catalog, services.operations),
    NotebookSecurity by SecurityFacade(
        services.executor,
        services.security,
        services.storage,
        services.collections,
    ),
    NotebookTransfer by TransferFacade(services.executor, services.transfer),
    NotebookResourceAccess by ResourceFacade(services.executor, services.resourceAccess),
    NotebookMaintenance by MaintenanceFacade(services.executor, services.demoUpgrade) {
    constructor(
        context: Context,
        database: NotebookDatabase? = null,
    ) : this(RepositoryServices(context.applicationContext, database))

    internal constructor(context: Context, database: NotebookDatabase?, draftCipher: DraftCipher) :
        this(RepositoryServices(context.applicationContext, database, draftCipher))

    companion object {
        const val MAX_BYTES = NotebookPackage.MAX_SOURCE
    }
}
