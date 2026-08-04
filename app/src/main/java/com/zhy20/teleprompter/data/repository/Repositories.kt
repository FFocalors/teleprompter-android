package com.zhy20.teleprompter.data.repository

import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptFolder
import kotlinx.coroutines.flow.Flow

sealed class DataOperationException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ScriptNotFoundException(val scriptId: String) : DataOperationException("Script not found: $scriptId")
class FolderNotFoundException(val folderId: String) : DataOperationException("Folder not found: $folderId")
class EmptyFolderNameException : DataOperationException("Folder name cannot be empty")
class FolderNameConflictException(val folderName: String) : DataOperationException("Folder name already exists: $folderName")
class DataAccessException(operation: String, cause: Throwable) : DataOperationException("Data operation failed: $operation", cause)

interface ScriptRepository {
    fun observeAll(): Flow<List<Script>>
    fun observeInFolder(folderId: String): Flow<List<Script>>
    fun observeUncategorized(): Flow<List<Script>>
    fun observeById(id: String): Flow<Script?>
    suspend fun getById(id: String): Script?
    suspend fun create(folderId: String? = null): Script
    suspend fun updateTitle(id: String, title: String)
    suspend fun updateDocument(id: String, document: ScriptDocument)
    suspend fun move(id: String, folderId: String?)
    suspend fun updatePlaybackSettings(id: String, settings: PlaybackSettings)
    suspend fun delete(id: String)
}

interface ScriptFolderRepository {
    fun observeAll(): Flow<List<ScriptFolder>>
    suspend fun create(name: String): ScriptFolder
    suspend fun rename(id: String, name: String)
    suspend fun updateSortOrder(id: String, sortOrder: Int)
    suspend fun deleteAndUncategorizeScripts(id: String)
}

data class GlobalSettings(
    val playbackDefaults: PlaybackSettings = PlaybackSettings(),
    val languageTag: String = "zh-CN",
)

interface SettingsRepository {
    val settings: Flow<GlobalSettings>
    suspend fun updatePlaybackDefaults(settings: PlaybackSettings)
    suspend fun updateLanguage(languageTag: String)
}
