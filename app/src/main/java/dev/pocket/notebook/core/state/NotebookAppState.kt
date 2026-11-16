package dev.pocket.notebook.core.state

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.pocket.notebook.data.NotebookEntry
import dev.pocket.notebook.data.NotebookMetadata
import dev.pocket.notebook.data.NotebookMetadataCodec
import dev.pocket.notebook.data.VfsItem
import dev.pocket.notebook.domain.NotebookGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** UI-independent notebook actions; editor writes are coordinated asynchronously. */
class NotebookAppState(private val repository: NotebookGateway) {
    val writer = RepositoryWriteScopes.forRepository(repository)

    fun foreground() = writer.foreground()

    fun flushAndLock(onLocked: () -> Unit) = writer.flushAndLock(repository::lockAll, onLocked)

    fun load(id: String) = repository.load(id)

    var entries by mutableStateOf<List<NotebookEntry>>(emptyList())
        private set

    var revision by mutableIntStateOf(0)
        private set

    init {
        refresh()
    }

    fun refresh() {
        entries = repository.list()
        revision++
    }

    fun create(language: String, parentId: String? = null): NotebookEntry =
        repository.create(language, parentId).also { refresh() }

    fun read(id: String): String = repository.read(id)

    fun isUnlocked(id: String): Boolean = repository.isUnlocked(id)

    fun readCover(id: String): ByteArray? = repository.readCover(id)

    fun setCover(id: String, bytes: ByteArray?, mime: String?) {
        repository.setCover(id, bytes, mime)
        refresh()
    }

    fun setPassword(id: String, password: CharArray) {
        repository.setPassword(id, password)
        refresh()
    }

    fun unlock(id: String, password: CharArray) {
        repository.unlock(id, password)
        refresh()
    }

    fun removePassword(id: String) {
        repository.removePassword(id)
        refresh()
    }

    fun save(id: String, source: String) {
        repository.save(id, source)
        val newMeta = NotebookMetadataCodec.read(source)
        val previous = entries.firstOrNull { it.id == id }
        if (
            previous != null &&
                (previous.title != newMeta.title ||
                    previous.description != newMeta.description ||
                    previous.author != newMeta.author ||
                    previous.version != newMeta.version)
        ) {
            refresh()
        }
    }

    fun updateMetadata(id: String, metadata: NotebookMetadata): NotebookEntry =
        repository.updateMetadata(id, metadata).also { refresh() }

    fun updateMetadataAndCover(
        id: String,
        metadata: NotebookMetadata,
        changeCover: Boolean,
        bytes: ByteArray?,
        mime: String?,
    ) = repository.updateMetadataAndCover(id, metadata, changeCover, bytes, mime).also { refresh() }

    fun move(id: String, targetId: String) {
        repository.move(id, targetId)
        refresh()
    }

    fun setSiblingOrder(parentId: String?, orderedIds: List<String>) {
        repository.setSiblingOrder(parentId, orderedIds)
        refresh()
    }

    /** Delete a notebook or tree only after in-flight writes have left the storage gate.
     * A failed deletion must not drop unsaved editor text or its retry status.
     */
    suspend fun delete(id: String) = deleteItems(listOf(id))

    suspend fun deleteItems(ids: List<String>) {
        withContext(Dispatchers.IO) {
            val affected = repository.notebookIdsInBranches(ids)
            writer.discardAndRun(affected) { repository.deleteItems(ids) }
        }
        withContext(Dispatchers.Main) { refresh() }
    }

    fun import(uri: Uri, parentId: String? = null): NotebookEntry =
        repository.importDocument(uri, parentId).also { refresh() }

    fun children(folderId: String?): List<VfsItem> {
        revision
        return repository.children(folderId)
    }

    fun path(folderId: String?): List<VfsItem.Folder> {
        revision
        return repository.folderPath(folderId)
    }

    fun folders(): List<VfsItem.Folder> {
        revision
        return repository.allFolders()
    }

    fun createFolder(name: String, parentId: String?): VfsItem.Folder =
        repository.createFolder(name, parentId).also { refresh() }

    fun renameFolder(id: String, name: String) {
        repository.renameFolder(id, name)
        refresh()
    }

    fun moveToFolder(id: String, parentId: String?) {
        repository.moveToFolder(id, parentId)
        refresh()
    }

    fun export(id: String, uri: Uri) = repository.exportDocument(id, uri)
}
