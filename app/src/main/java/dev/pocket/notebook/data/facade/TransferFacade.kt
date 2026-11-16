package dev.pocket.notebook.data.facade

import android.net.Uri
import dev.pocket.notebook.data.*
import dev.pocket.notebook.data.transfer.LibraryTransfer
import dev.pocket.notebook.domain.NotebookTransfer

internal class TransferFacade(
    private val executor: BlockingRepositoryExecutor,
    private val transfer: LibraryTransfer,
) : NotebookTransfer {
    override fun importDocument(uri: Uri, parentId: String?): NotebookEntry =
        executor.execute { transfer.importDocument(uri, parentId) }

    override fun exportDocument(id: String, uri: Uri) =
        executor.execute { transfer.export(listOf(id), uri, null) }

    override fun exportItems(ids: List<String>, uri: Uri, password: CharArray?) =
        executor.execute { transfer.export(ids, uri, password) }

    override fun importAny(uri: Uri, parent: String?, password: CharArray?): List<String> =
        executor.execute { transfer.import(uri, parent, password) }
}
