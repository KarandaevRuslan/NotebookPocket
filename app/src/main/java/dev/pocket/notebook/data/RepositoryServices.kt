package dev.pocket.notebook.data

import android.content.Context
import dev.pocket.notebook.data.catalog.LibraryCatalog
import dev.pocket.notebook.data.catalog.LibraryOperations
import dev.pocket.notebook.data.collection.CollectionConversion
import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.demo.DemoUpgrade
import dev.pocket.notebook.data.document.DocumentContent
import dev.pocket.notebook.data.document.DocumentEditor
import dev.pocket.notebook.data.draft.*
import dev.pocket.notebook.data.facade.BlockingRepositoryExecutor
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.data.resource.NotebookResources
import dev.pocket.notebook.data.resource.ResourceAccess
import dev.pocket.notebook.data.security.DocumentSecurity
import dev.pocket.notebook.data.storage.LocalStorageAccess
import dev.pocket.notebook.data.storage.StorageAccess
import dev.pocket.notebook.data.transfer.DocumentImporter
import dev.pocket.notebook.data.transfer.LibraryArchive
import dev.pocket.notebook.data.transfer.LibraryTransfer
import java.io.File

/** Composition root with an acyclic graph of storage and use-case services. */
internal class RepositoryServices(
    context: Context,
    database: NotebookDatabase?,
    draftCipher: DraftCipher = KeystoreDraftCipher(),
) {
    val appContext = context
    val storage: StorageAccess = LocalStorageAccess(appContext, database)
    val tree = VfsTreeStore(storage.db, storage.dao)
    val collections = CollectionStore(storage)
    val drafts = DraftStore(File(appContext.noBackupFilesDir, "notebook-drafts"), draftCipher)

    init {
        val live =
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                storage.dao.notebookIds().toSet()
            }
        drafts.recover(live, storage.now())
    }

    val content = DocumentContent(storage, collections, drafts)
    val resources = NotebookResources(storage, collections, content)
    val editor = DocumentEditor(appContext, storage, collections, content, tree, drafts)
    val security = DocumentSecurity(storage, collections, content, resources)
    val conversion = CollectionConversion(storage, collections, content)
    val catalog = LibraryCatalog(storage, collections, content, tree, conversion)
    val operations = LibraryOperations(storage, collections, content, resources)
    val importer = DocumentImporter(storage, collections, content, tree)
    val archive = LibraryArchive(storage, collections, content, resources, operations)
    val transfer = LibraryTransfer(appContext, archive, importer)
    val resourceAccess = ResourceAccess(storage, collections, resources)
    val demoUpgrade = DemoUpgrade(appContext, storage, collections, content, resources)

    val executor = BlockingRepositoryExecutor()
}
