package dev.pocket.notebook.data.storage

import android.content.Context
import dev.pocket.notebook.data.LegacyStorageMigration
import dev.pocket.notebook.data.local.DocumentFileStore
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.data.security.DeviceKeyCache
import java.util.UUID

internal class LocalStorageAccess(context: Context, database: NotebookDatabase?) : StorageAccess {
    override val db = database ?: NotebookDatabase.get(context)
    override val dao = db.vfsDao()
    override val files = DocumentFileStore(context)
    override val keyCache = DeviceKeyCache(context)

    override fun now() = System.currentTimeMillis()

    override fun newId() = UUID.randomUUID().toString()

    init {
        LegacyStorageMigration(context, db, files).migrate()
        val report =
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                StorageRecovery(files, dao).run(now())
            }
        if (report.missingReferences.isNotEmpty() || report.deferredDeletes > 0)
            dev.pocket.notebook.core.logging.NotebookLog.w(
                "NotebookStorage",
                "Recovery requires attention: missing=${report.missingReferences.size}, deferred=${report.deferredDeletes}",
            )
    }
}
