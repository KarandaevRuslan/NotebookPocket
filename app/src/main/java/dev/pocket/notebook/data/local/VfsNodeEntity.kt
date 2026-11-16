package dev.pocket.notebook.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Only folder structure, public metadata, ordering and file references live in SQLite. */
@Entity(tableName = "vfs_nodes", indices = [Index(value = ["parentId", "position"])])
data class VfsNodeEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val kind: String, // FOLDER / COLLECTION / NOTEBOOK
    val position: Int,
    val title: String,
    val description: String = "",
    val author: String = "",
    val version: String = "",
    val updated: Long = System.currentTimeMillis(),
    val contentRef: String? = null,
    val packageFormat: Boolean = false,
    val encrypted: Boolean = false,
    val coverRef: String? = null,
    val hasCover: Boolean = false,
)
