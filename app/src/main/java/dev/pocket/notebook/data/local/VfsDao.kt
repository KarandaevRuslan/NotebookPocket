package dev.pocket.notebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VfsDao {
    @Query("SELECT * FROM vfs_nodes WHERE id = :id LIMIT 1")
    suspend fun get(id: String): VfsNodeEntity?

    @Query("SELECT * FROM vfs_nodes WHERE kind = 'NOTEBOOK' ORDER BY updated DESC")
    suspend fun allNotebooks(): List<VfsNodeEntity>

    @Query(
        "SELECT * FROM vfs_nodes WHERE kind IN ('FOLDER','COLLECTION') ORDER BY position ASC, title COLLATE NOCASE"
    )
    suspend fun allFolders(): List<VfsNodeEntity>

    @Query("SELECT * FROM vfs_nodes WHERE parentId IS :parentId ORDER BY position ASC, id ASC")
    suspend fun children(parentId: String?): List<VfsNodeEntity>

    @Query("SELECT * FROM vfs_nodes") suspend fun allNodes(): List<VfsNodeEntity>

    @Query("SELECT COUNT(*) FROM vfs_nodes") suspend fun count(): Int

    @Query("SELECT MAX(position) FROM vfs_nodes WHERE parentId IS :parentId")
    suspend fun lastPosition(parentId: String?): Int?

    @Query("SELECT * FROM vfs_nodes WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<VfsNodeEntity>

    @Query("SELECT id FROM vfs_nodes WHERE kind = 'NOTEBOOK'")
    suspend fun notebookIds(): List<String>

    @Query("SELECT COUNT(*) FROM vfs_nodes WHERE kind = 'NOTEBOOK'")
    fun observeNotebookCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(node: VfsNodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(node: VfsNodeEntity)

    @Update suspend fun update(node: VfsNodeEntity)

    @Update suspend fun updateAll(nodes: List<VfsNodeEntity>)

    @Query("UPDATE vfs_nodes SET position = position + 1 WHERE parentId IS :parentId")
    suspend fun shiftPositions(parentId: String?)

    @Query("UPDATE vfs_nodes SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("UPDATE vfs_nodes SET parentId = :parentId, position = :position WHERE id = :id")
    suspend fun setParentAndPosition(id: String, parentId: String?, position: Int)

    @Query("DELETE FROM vfs_nodes WHERE id = :id") suspend fun delete(id: String)
}
