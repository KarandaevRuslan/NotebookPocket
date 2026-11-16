package dev.pocket.notebook.data.storage

import dev.pocket.notebook.data.local.VfsDao

data class StorageRecoveryReport(
    val missingReferences: Set<String>,
    val removedOrphans: Int,
    val deferredDeletes: Int,
)

/** Never repairs a missing live revision by deleting its metadata or substituting empty content. */
internal class StorageRecovery(private val files: DocumentFiles, private val dao: VfsDao) {
    suspend fun run(now: Long, graceMillis: Long = 60 * 60 * 1000L): StorageRecoveryReport {
        val live =
            dao.allNodes()
                .flatMap { listOfNotNull(it.contentRef, it.coverRef) }
                .filterNot { it.startsWith("member:") }
                .toSet()
        val inventory = files.inventory()
        val present = inventory.filterNot { it.temporary }.map { it.ref }.toSet()
        var removed = 0
        var deferred = 0
        for (revision in inventory) {
            if (revision.ref in live || revision.modified > now - graceMillis) continue
            try {
                files.deleteRef(revision.ref)
                removed++
            } catch (_: Exception) {
                deferred++
            }
        }
        return StorageRecoveryReport(live - present, removed, deferred)
    }
}
