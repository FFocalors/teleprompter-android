package com.zhy20.teleprompter.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zhy20.teleprompter.data.local.entity.ScriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY updatedAt DESC, title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE folderId = :folderId ORDER BY updatedAt DESC, title COLLATE NOCASE ASC")
    fun observeInFolder(folderId: String): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE folderId IS NULL ORDER BY updatedAt DESC, title COLLATE NOCASE ASC")
    fun observeUncategorized(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    fun observeById(id: String): Flow<ScriptEntity?>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getById(id: String): ScriptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(script: ScriptEntity)

    @Update
    suspend fun update(script: ScriptEntity): Int

    @Query("UPDATE scripts SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long): Int

    @Query("UPDATE scripts SET documentJson = :documentJson, plainText = :plainText, wordCount = :wordCount, normalEstimatedDurationSeconds = :durationSeconds, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDocument(
        id: String,
        documentJson: String,
        plainText: String,
        wordCount: Int,
        durationSeconds: Long,
        updatedAt: Long,
    ): Int

    @Query("UPDATE scripts SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFolder(id: String, folderId: String?, updatedAt: Long): Int

    @Query("UPDATE scripts SET playbackSettingsJson = :settingsJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePlaybackSettings(id: String, settingsJson: String, updatedAt: Long): Int

    @Query("UPDATE scripts SET folderId = NULL, updatedAt = :updatedAt WHERE folderId = :folderId")
    suspend fun moveAllToUncategorized(folderId: String, updatedAt: Long): Int

    @Delete
    suspend fun delete(script: ScriptEntity): Int

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
