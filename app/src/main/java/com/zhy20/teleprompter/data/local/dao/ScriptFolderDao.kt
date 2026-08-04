package com.zhy20.teleprompter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zhy20.teleprompter.data.local.entity.ScriptFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptFolderDao {
    @Query("SELECT * FROM script_folders ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<ScriptFolderEntity>>

    @Query("SELECT * FROM script_folders WHERE id = :id")
    suspend fun getById(id: String): ScriptFolderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(folder: ScriptFolderEntity)

    @Query("UPDATE script_folders SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long): Int

    @Query("UPDATE script_folders SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long): Int

    @Query("DELETE FROM script_folders WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM script_folders WHERE name = :name COLLATE NOCASE AND (:excludedId IS NULL OR id != :excludedId))")
    suspend fun nameExists(name: String, excludedId: String? = null): Boolean

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM script_folders")
    suspend fun nextSortOrder(): Int
}
