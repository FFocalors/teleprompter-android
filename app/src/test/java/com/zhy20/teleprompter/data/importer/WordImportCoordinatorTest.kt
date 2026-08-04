package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.data.repository.FolderNotFoundException
import com.zhy20.teleprompter.data.repository.ScriptRepository
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Verifies that DOC and DOCX imports flow through the coordinator into an atomic repository create
 * with the correct title, document, folder and derived fields, and that failures never leave a
 * half-created script.
 */
class WordImportCoordinatorTest {

    private val docxBytes: ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream("fixtures/real_sample.docx")) { "missing docx fixture" }
            .use { it.readBytes() }
    private val docBytes: ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream("fixtures/real_sample.doc")) { "missing doc fixture" }
            .use { it.readBytes() }

    private fun coordinator(repository: ScriptRepository): ScriptImportCoordinator =
        ScriptImportCoordinator(ScriptImportManager(), repository)

    @Test
    fun docxImport_createsScriptAtomically() = runTest {
        val repository = RecordingWordRepository()
        val id = coordinator(repository).importFile(
            ImportFileMetadata("心得.docx", DocxScriptImporter.MimeTypeWordOpenXml, docxBytes.size.toLong()),
            { ByteArrayInputStream(docxBytes) },
            folderId = null,
        )
        assertEquals("id-0", id)
        assertEquals(1, repository.created.size)
        assertEquals("心得", repository.created.single().title)
        assertTrue(repository.created.single().content.plainText().contains("第一段"))
    }

    @Test
    fun docImport_createsScriptAtomically() = runTest {
        val repository = RecordingWordRepository()
        val id = coordinator(repository).importFile(
            ImportFileMetadata("心得.doc", DocScriptImporter.MimeTypeWordLegacy, docBytes.size.toLong()),
            { ByteArrayInputStream(docBytes) },
            folderId = null,
        )
        assertEquals("id-0", id)
        assertEquals(1, repository.created.size)
        assertEquals("心得", repository.created.single().title)
        assertTrue(repository.created.single().content.plainText().contains("第一段"))
    }

    @Test
    fun docxImport_passesFolderId() = runTest {
        val repository = RecordingWordRepository()
        coordinator(repository).importFile(
            ImportFileMetadata("心得.docx", DocxScriptImporter.MimeTypeWordOpenXml, docxBytes.size.toLong()),
            { ByteArrayInputStream(docxBytes) },
            folderId = "folder-1",
        )
        assertEquals("folder-1", repository.created.single().folderId)
    }

    @Test
    fun corruptDocx_doesNotCreateScript() = runTest {
        val repository = RecordingWordRepository()
        val bad = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x01, 0x02, 0x03)
        try {
            coordinator(repository).importFile(
                ImportFileMetadata("bad.docx", DocxScriptImporter.MimeTypeWordOpenXml, bad.size.toLong()),
                { ByteArrayInputStream(bad) },
                null,
            )
            fail("Expected corrupt")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.Corrupt, e.error)
        }
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun unsupportedFile_doesNotCreateScript() = runTest {
        val repository = RecordingWordRepository()
        try {
            coordinator(repository).importFile(
                ImportFileMetadata("file.pdf", "application/pdf", 10),
                { ByteArrayInputStream(ByteArray(0)) },
                null,
            )
            fail("Expected unsupported")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.UnsupportedType, e.error)
        }
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun encryptedDocx_isRejectedAndDoesNotCreateScript() = runTest {
        val repository = RecordingWordRepository()
        // An OLE2 container (not a ZIP) named .docx is reported as corrupt/encrypted — never saved.
        val oleBytes = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
        ) + ByteArray(512)
        try {
            coordinator(repository).importFile(
                ImportFileMetadata("protected.docx", DocxScriptImporter.MimeTypeWordOpenXml, oleBytes.size.toLong()),
                { ByteArrayInputStream(oleBytes) },
                null,
            )
            fail("Expected failure")
        } catch (e: ScriptImportException) {
            assertTrue(
                "expected corrupt/too-complex/too-large, got ${e.error}",
                e.error == ScriptImportError.Corrupt || e.error == ScriptImportError.TooComplex || e.error == ScriptImportError.TooLarge,
            )
        }
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun repositorySaveFailure_doesNotCreateHalfScript() = runTest {
        val repository = RecordingWordRepository(throwOnFolder = "missing")
        try {
            coordinator(repository).importFile(
                ImportFileMetadata("心得.docx", DocxScriptImporter.MimeTypeWordOpenXml, docxBytes.size.toLong()),
                { ByteArrayInputStream(docxBytes) },
                "missing",
            )
            fail("Expected save-failed")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.SaveFailed, e.error)
        }
        assertTrue(repository.created.isEmpty())
    }

    private class RecordingWordRepository(
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
