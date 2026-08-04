package com.zhy20.teleprompter.data.repository

import androidx.room.withTransaction
import com.zhy20.teleprompter.core.model.ChineseSpeechDurationEstimator
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptFolder
import com.zhy20.teleprompter.data.local.TeleprompterDatabase
import com.zhy20.teleprompter.data.local.dao.ScriptDao
import com.zhy20.teleprompter.data.local.dao.ScriptFolderDao
import com.zhy20.teleprompter.data.local.entity.ScriptEntity
import com.zhy20.teleprompter.data.local.entity.ScriptFolderEntity
import com.zhy20.teleprompter.data.serialization.PlaybackSettingsSerializer
import com.zhy20.teleprompter.data.serialization.ScriptDocumentSerializer
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomScriptRepository(
    private val scriptDao: ScriptDao,
    private val folderDao: ScriptFolderDao,
    private val settingsRepository: SettingsRepository,
    private val defaultTitle: () -> String,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ScriptRepository {
    override fun observeAll(): Flow<List<Script>> = scriptDao.observeAll().map { scripts -> scripts.map(ScriptEntity::toModel) }
    override fun observeInFolder(folderId: String): Flow<List<Script>> = scriptDao.observeInFolder(folderId).map { it.map(ScriptEntity::toModel) }
    override fun observeUncategorized(): Flow<List<Script>> = scriptDao.observeUncategorized().map { it.map(ScriptEntity::toModel) }
    override fun observeById(id: String): Flow<Script?> = scriptDao.observeById(id).map { it?.toModel() }
    override suspend fun getById(id: String): Script? = dataOperation("read script") { scriptDao.getById(id)?.toModel() }

    override suspend fun create(folderId: String?): Script = dataOperation("create script") {
        if (folderId != null && folderDao.getById(folderId) == null) throw FolderNotFoundException(folderId)
        val now = clockMillis()
        val document = ScriptDocumentSerializer.emptyDocument()
        val defaults = settingsRepository.settings.first().playbackDefaults
        val entity = ScriptEntity(
            id = newId(),
            title = defaultTitle(),
            folderId = folderId,
            documentJson = ScriptDocumentSerializer.encode(document),
            plainText = "",
            wordCount = 0,
            normalEstimatedDurationSeconds = ChineseSpeechDurationEstimator.estimate("").toLong(),
            playbackSettingsJson = PlaybackSettingsSerializer.encode(defaults),
            createdAt = now,
            updatedAt = now,
        )
        scriptDao.insert(entity)
        entity.toModel()
    }

    override suspend fun updateTitle(id: String, title: String) = dataOperation("update title") {
        val normalized = title.trim().ifBlank(defaultTitle)
        if (scriptDao.updateTitle(id, normalized, clockMillis()) == 0) throw ScriptNotFoundException(id)
    }

    override suspend fun updateDocument(id: String, document: ScriptDocument) = dataOperation("update document") {
        val current = scriptDao.getById(id) ?: throw ScriptNotFoundException(id)
        val plainText = document.plainText()
        val plainTextChanged = plainText != current.plainText
        val duration = if (plainTextChanged) {
            ChineseSpeechDurationEstimator.estimate(plainText).toLong()
        } else {
            current.normalEstimatedDurationSeconds
        }
        val wordCount = if (plainTextChanged) plainText.count { !it.isWhitespace() } else current.wordCount
        val changed = scriptDao.updateDocument(
            id = id,
            documentJson = ScriptDocumentSerializer.encode(document),
            plainText = plainText,
            wordCount = wordCount,
            durationSeconds = duration,
            updatedAt = clockMillis(),
        )
        if (changed == 0) throw ScriptNotFoundException(id)
    }

    override suspend fun move(id: String, folderId: String?) = dataOperation("move script") {
        if (folderId != null && folderDao.getById(folderId) == null) throw FolderNotFoundException(folderId)
        if (scriptDao.updateFolder(id, folderId, clockMillis()) == 0) throw ScriptNotFoundException(id)
    }

    override suspend fun updatePlaybackSettings(id: String, settings: PlaybackSettings) = dataOperation("update playback settings") {
        if (scriptDao.updatePlaybackSettings(id, PlaybackSettingsSerializer.encode(settings), clockMillis()) == 0) {
            throw ScriptNotFoundException(id)
        }
    }

    override suspend fun delete(id: String) = dataOperation("delete script") {
        if (scriptDao.deleteById(id) == 0) throw ScriptNotFoundException(id)
    }
}

class RoomScriptFolderRepository(
    private val database: TeleprompterDatabase,
    private val folderDao: ScriptFolderDao,
    private val scriptDao: ScriptDao,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ScriptFolderRepository {
    override fun observeAll(): Flow<List<ScriptFolder>> = combine(folderDao.observeAll(), scriptDao.observeAll()) { folders, scripts ->
        folders.map { folder ->
            ScriptFolder(
                id = folder.id,
                name = folder.name,
                createdAt = folder.createdAt,
                scriptCount = scripts.count { it.folderId == folder.id },
            )
        }
    }

    override suspend fun create(name: String): ScriptFolder = dataOperation("create folder") {
        val normalized = validateName(name)
        if (folderDao.nameExists(normalized)) throw FolderNameConflictException(normalized)
        val now = clockMillis()
        val entity = ScriptFolderEntity(newId(), normalized, now, now, folderDao.nextSortOrder())
        runCatching { folderDao.insert(entity) }.getOrElse { throw FolderNameConflictException(normalized) }
        ScriptFolder(entity.id, entity.name, entity.createdAt, scriptCount = 0)
    }

    override suspend fun rename(id: String, name: String) = dataOperation("rename folder") {
        val normalized = validateName(name)
        if (folderDao.getById(id) == null) throw FolderNotFoundException(id)
        if (folderDao.nameExists(normalized, id)) throw FolderNameConflictException(normalized)
        runCatching { folderDao.rename(id, normalized, clockMillis()) }.getOrElse { throw FolderNameConflictException(normalized) }
        Unit
    }

    override suspend fun updateSortOrder(id: String, sortOrder: Int) = dataOperation("sort folder") {
        if (folderDao.updateSortOrder(id, sortOrder, clockMillis()) == 0) throw FolderNotFoundException(id)
    }

    override suspend fun deleteAndUncategorizeScripts(id: String) = dataOperation("delete folder") {
        database.withTransaction {
            if (folderDao.getById(id) == null) throw FolderNotFoundException(id)
            scriptDao.moveAllToUncategorized(id, clockMillis())
            if (folderDao.deleteById(id) == 0) throw FolderNotFoundException(id)
        }
    }

    private fun validateName(name: String): String = name.trim().ifEmpty { throw EmptyFolderNameException() }
}

private fun ScriptEntity.toModel(): Script {
    val document = ScriptDocumentSerializer.decode(documentJson)
    return Script(
        id = id,
        title = title,
        plainTextPreview = plainText.replace('\n', ' ').take(140),
        content = document,
        folderId = folderId,
        wordCount = wordCount,
        normalEstimatedDurationSeconds = normalEstimatedDurationSeconds.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        lastModifiedAt = updatedAt,
        playbackSettings = PlaybackSettingsSerializer.decode(playbackSettingsJson),
    )
}

private suspend inline fun <T> dataOperation(name: String, crossinline block: suspend () -> T): T = try {
    block()
} catch (known: DataOperationException) {
    throw known
} catch (unexpected: Exception) {
    throw DataAccessException(name, unexpected)
}
