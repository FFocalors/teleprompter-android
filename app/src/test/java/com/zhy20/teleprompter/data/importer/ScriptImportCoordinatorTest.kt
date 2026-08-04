package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.data.repository.FolderNotFoundException
import com.zhy20.teleprompter.data.repository.ScriptRepository
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScriptImportCoordinatorTest {
    private fun importManager(): ScriptImportManager = ScriptImportManager(listOf(PlainTextScriptImporter()))

    @Test
    fun successfulImport_returnsNewScriptId() = runTest {
        val repository = RecordingRepository()
        val coordinator = ScriptImportCoordinator(importManager(), repository)

        val id = coordinator.importFile(
            ImportFileMetadata("欢迎词.txt", "text/plain", null),
            { ByteArrayInputStream("大家好。".toByteArray(StandardCharsets.UTF_8)) },
            folderId = null,
        )

        assertEquals("id-0", id)
        assertEquals(1, repository.created.size)
        assertEquals("欢迎词", repository.created.single().title)
        assertEquals("大家好。", repository.created.single().content.plainText())
    }

    @Test
    fun unsupportedType_throwsMappedError() = runTest {
        val repository = RecordingRepository()
        val coordinator = ScriptImportCoordinator(importManager(), repository)

        try {
            coordinator.importFile(
                ImportFileMetadata("report.pdf", "application/pdf", null),
                { ByteArrayInputStream(ByteArray(0)) },
                folderId = null,
            )
            fail("Expected unsupported-type exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.UnsupportedType, e.error)
        }
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun repositoryFolderError_mapsToSaveFailed() = runTest {
        val repository = RecordingRepository(throwOnFolder = "missing")
        val coordinator = ScriptImportCoordinator(importManager(), repository)

        try {
            coordinator.importFile(
                ImportFileMetadata("a.txt", "text/plain", null),
                { ByteArrayInputStream("正文".toByteArray(StandardCharsets.UTF_8)) },
                folderId = "missing",
            )
            fail("Expected save-failed exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.SaveFailed, e.error)
        }
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun emptyFile_throwsEmptyAndDoesNotCreateScript() = runTest {
        val repository = RecordingRepository()
        val coordinator = ScriptImportCoordinator(importManager(), repository)

        try {
            coordinator.importFile(
                ImportFileMetadata("空.txt", "text/plain", null),
                { ByteArrayInputStream(ByteArray(0)) },
                folderId = null,
            )
            fail("Expected empty exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.Empty, e.error)
        }
        assertTrue(repository.created.isEmpty())
    }

    private class RecordingRepository(
        private val throwOnFolder: String? = null,
    ) : ScriptRepository {
        val created = mutableListOf<Script>()
        private val state = MutableStateFlow<List<Script>>(emptyList())

        override fun observeAll(): Flow<List<Script>> = state
        override fun observeInFolder(folderId: String): Flow<List<Script>> = state
        override fun observeUncategorized(): Flow<List<Script>> = state
        override fun observeById(id: String): Flow<Script?> = MutableStateFlow(created.lastOrNull { it.id == id })
        override suspend fun getById(id: String): Script? = created.lastOrNull { it.id == id }
        override suspend fun create(folderId: String?): Script = error("Not used")
        override suspend fun createFromDocument(title: String, document: ScriptDocument, folderId: String?): Script {
            if (throwOnFolder != null && throwOnFolder == folderId) throw FolderNotFoundException(folderId)
            val script = Script(
                id = "id-${created.size}",
                title = title,
                plainTextPreview = document.plainText(),
                content = document,
                folderId = folderId,
                wordCount = document.plainText().count { !it.isWhitespace() },
                normalEstimatedDurationSeconds = document.plainText().length,
                lastModifiedAt = 1,
                playbackSettings = PlaybackSettings(),
            )
            created += script
            state.value = created.toList()
            return script
        }

        override suspend fun updateTitle(id: String, title: String) = Unit
        override suspend fun updateDocument(id: String, document: ScriptDocument) = Unit
        override suspend fun move(id: String, folderId: String?) = Unit
        override suspend fun updatePlaybackSettings(id: String, settings: PlaybackSettings) = Unit
        override suspend fun delete(id: String) = Unit
    }
}
