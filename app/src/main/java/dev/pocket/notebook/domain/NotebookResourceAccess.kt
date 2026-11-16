package dev.pocket.notebook.domain

import dev.pocket.notebook.data.*
import dev.pocket.notebook.data.ResourceInfo

/**
 * Public NotebookResourceAccess contract. Calls serialize storage access; invoke IO work off the UI
 * thread.
 */
interface NotebookResourceAccess {
    fun resourceFiles(owner: String): List<ResourceInfo>

    fun resourceEntries(owner: String, directory: String = ""): List<ResourceInfo>

    fun resourceEntry(owner: String, path: String): ResourceInfo

    fun readResource(owner: String, path: String): ByteArray

    fun putResource(owner: String, path: String, bytes: ByteArray)

    fun createResourceFolder(owner: String, path: String)

    fun replaceResource(owner: String, path: String, bytes: ByteArray, expectedHash: String)

    fun deleteResource(owner: String, path: String)

    fun deleteResources(owner: String, paths: List<String>)

    fun transferResources(
        source: String,
        path: String,
        target: String,
        destination: String,
        cut: Boolean,
    )

    fun transferResources(
        source: String,
        paths: List<String>,
        target: String,
        destination: String,
        cut: Boolean,
    )

    fun renameResource(owner: String, path: String, newPath: String)

    fun ownerProtected(id: String): Boolean

    fun resourceOwner(id: String): String?

    fun attachmentNames(id: String): List<String>

    fun attachment(id: String, path: String): ByteArray
}
