package com.zhy20.teleprompter.feature.library

import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptFolder
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.data.importer.ImportFileMetadata
import com.zhy20.teleprompter.data.importer.PlainTextScriptImporter
import com.zhy20.teleprompter.data.importer.ScriptImportCoordinator
import com.zhy20.teleprompter.data.importer.ScriptImportError
import com.zhy20.teleprompter.data.importer.ScriptImportManager
import com.zhy20.teleprompter.data.importer.ScriptImportState
import com.zhy20.teleprompter.data.repository.FolderNotFoundException
import com.zhy20.teleprompter.data.repository.ScriptFolderRepository
import com.zhy20.teleprompter.data.repository.ScriptRepository
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: FakeScriptRepository,
        coordinator: ScriptImportCoordinator = ScriptImportCoordinator(ScriptImportManager(), repository),
    ) = LibraryViewModel(
        scriptRepository = repository,
        folderRepository = FakeFolderRepository(),
        importCoordinator = coordinator,
        importDispatcher = dispatcher,
    )

    private fun stream(text: String = "正文"): () -> InputStream =
        { ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)) }

    @Test
    fun startImport_successSetsReadingThenSuccessAndReturnsId() = runTest(dispatcher) {
        val repository = FakeScriptRepository()
        val viewModel = viewModel(repository)
        var returnedId: String? = null
        viewModel.startImport(
            ImportFileMetadata("ok.txt", "text/plain", 10),
            stream(),
            null,
        ) { returnedId = it }

        assertEquals(ScriptImportState.Reading, viewModel.importState.value)
        advanceUntilIdle()
        assertEquals("id-0", returnedId)
        assertEquals("ok", repository.created.single().title)
        assertEquals(ScriptImportState.Idle, viewModel.importState.value)
    }

    @Test
    fun startImport_unsupportedType_setsErrorState() = runTest(dispatcher) {
        val repository = FakeScriptRepository()
        val viewModel = viewModel(repository)
        viewModel.startImport(
            ImportFileMetadata("bad.pdf", "application/pdf", 10),
            stream(),
            null,
        ) {}

        advanceUntilIdle()
        assertEquals(ScriptImportState.Error(ScriptImportError.UnsupportedType), viewModel.importState.value)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun startImport_sizeTooLarge_mapsToTooLargeError() = runTest(dispatcher) {
        val repository = FakeScriptRepository()
        val viewModel = viewModel(repository)
        viewModel.startImport(
            ImportFileMetadata("big.txt", "text/plain", PlainTextScriptImporter.MaxImportBytes.toLong() + 1),
            stream(),
            null,
        ) {}

        advanceUntilIdle()
        assertEquals(ScriptImportState.Error(ScriptImportError.TooLarge), viewModel.importState.value)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun startImport_emptyFile_mapsToEmptyError() = runTest(dispatcher) {
        val repository = FakeScriptRepository()
        val viewModel = viewModel(repository)
        viewModel.startImport(
            ImportFileMetadata("empty.txt", "text/plain", 0),
            stream(""),
            null,
        ) {}

        advanceUntilIdle()
        assertEquals(ScriptImportState.Error(ScriptImportError.Empty), viewModel.importState.value)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun startImport_repositorySaveFailure_mapsToSaveFailed() = runTest(dispatcher) {
        val repository = FakeScriptRepository(failSave = true)
        val viewModel = viewModel(repository)
        viewModel.startImport(
            ImportFileMetadata("ok.txt", "text/plain", 10),
            stream(),
            null,
        ) {}

        advanceUntilIdle()
        assertEquals(ScriptImportState.Error(ScriptImportError.SaveFailed), viewModel.importState.value)
    }

    @Test
    fun startImport_secondCallWhileRunning_isIgnored() = runTest(dispatcher) {
        val repository = FakeScriptRepository()
        val viewModel = viewModel(repository)
        var firstCalls = 0
        var secondCalls = 0
        viewModel.startImport(
            ImportFileMetadata("ok.txt", "text/plain", 10),
            {
                firstCalls += 1
                ByteArrayInputStream("正文".toByteArray(StandardCharsets.UTF_8))
            },
            null,
        ) {}

        viewModel.startImport(
            ImportFileMetadata("ok.txt", "text/plain", 10),
            {
                secondCalls += 1
                ByteArrayInputStream("正文".toByteArray(StandardCharsets.UTF_8))
            },
            null,
        ) {}

        advanceUntilIdle()
        assertEquals(1, firstCalls)
        assertEquals(0, secondCalls)
        assertEquals(1, repository.created.size)
    }

    @Test
    fun startImport_passesFolderIdThrough() = runTest(dispatcher) {
        val repository = FakeScriptRepository()
        val viewModel = viewModel(repository)
        viewModel.startImport(
            ImportFileMetadata("ok.txt", "text/plain", 10),
            stream(),
            "folder-1",
        ) {}

        advanceUntilIdle()
        assertEquals("folder-1", repository.created.single().folderId)
    }

    @Test
    fun clearImportError_resetsErrorToIdle() = runTest(dispatcher) {
        val repository = FakeScriptRepository()
        val viewModel = viewModel(repository)
        viewModel.startImport(
            ImportFileMetadata("bad.pdf", "application/pdf", 10),
            stream(),
            null,
        ) {}
        advanceUntilIdle()
        assertEquals(ScriptImportState.Error(ScriptImportError.UnsupportedType), viewModel.importState.value)

        viewModel.clearImportError()
        assertEquals(ScriptImportState.Idle, viewModel.importState.value)
    }

    @Test
    fun startImport_docx_realFixture_succeedsAndNavigatesOnce() = runTest(dispatcher) {
        val repository = FakeScriptRepository()
        val viewModel = viewModel(repository)
        val docxBytes = checkNotNull(javaClass.classLoader.getResourceAsStream("fixtures/real_sample.docx")) {
            "missing docx fixture"
        }.use { it.readBytes() }
        var navigations = 0
        viewModel.startImport(
            ImportFileMetadata("心得.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes.size.toLong()),
            { java.io.ByteArrayInputStream(docxBytes) },
            null,
        ) { navigations += 1 }

        advanceUntilIdle()
        assertEquals(1, navigations)
        assertEquals(1, repository.created.size)
        assertEquals("心得", repository.created.single().title)
        assertEquals(ScriptImportState.Idle, viewModel.importState.value)
    }

    @Test
    fun startImport_corruptDocx_mapsToCorruptAndDoesNotCreate() = runTest(dispatcher) {
        val repository = FakeScriptRepository()
        val viewModel = viewModel(repository)
        val bad = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x01, 0x02, 0x03)
        viewModel.startImport(
            ImportFileMetadata("bad.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bad.size.toLong()),
            { java.io.ByteArrayInputStream(bad) },
            null,
        ) {}

        advanceUntilIdle()
        assertEquals(ScriptImportState.Error(ScriptImportError.Corrupt), viewModel.importState.value)
        assertTrue(repository.created.isEmpty())
    }

    private class FakeFolderRepository : ScriptFolderRepository {
        private val state = MutableStateFlow<List<ScriptFolder>>(emptyList())
        override fun observeAll(): Flow<List<ScriptFolder>> = state
        override suspend fun create(name: String): ScriptFolder = ScriptFolder("f", name, 1, 0)
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun updateSortOrder(id: String, sortOrder: Int) = Unit
        override suspend fun deleteAndUncategorizeScripts(id: String) = Unit
    }

    private class FakeScriptRepository(
        private val failSave: Boolean = false,
    ) : ScriptRepository {
        val created = mutableListOf<Script>()
        private val state = MutableStateFlow<List<Script>>(emptyList())

        override fun observeAll(): Flow<List<Script>> = state
        override fun observeInFolder(folderId: String): Flow<List<Script>> = state
        override fun observeUncategorized(): Flow<List<Script>> = state
        override fun observeById(id: String): Flow<Script?> = MutableStateFlow(created.lastOrNull { it.id == id })
        override suspend fun getById(id: String): Script? = created.lastOrNull { it.id == id }
        override suspend fun create(folderId: String?): Script = Script(
            "new", "未命名台本", "",
            ScriptContent(listOf(ScriptBlock.Paragraph("p", listOf(ScriptSpan(""))))),
            folderId, 0, 1, 1, PlaybackSettings(),
        )
        override suspend fun createFromDocument(title: String, document: ScriptDocument, folderId: String?): Script {
            if (failSave) throw FolderNotFoundException("missing-folder")
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
