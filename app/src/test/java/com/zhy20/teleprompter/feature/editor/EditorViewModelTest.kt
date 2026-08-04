package com.zhy20.teleprompter.feature.editor

import androidx.lifecycle.SavedStateHandle
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.SaveState
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.core.model.ScriptSpanStyle
import com.zhy20.teleprompter.core.model.TextSelection
import com.zhy20.teleprompter.data.repository.ScriptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun editBecomesDirty_debouncesAndMarksLatestRevisionSaved() {
        val repository = EditorFakeRepository(script())
        val viewModel = EditorViewModel(SavedStateHandle(mapOf("scriptId" to "script")), repository)
        mainDispatcherRule.scheduler.runCurrent()

        viewModel.replaceText("新的正文", TextSelection(4, 4))
        assertTrue(viewModel.uiState.value.isDirty)
        assertEquals(SaveState.Saving, viewModel.uiState.value.saveState)
        mainDispatcherRule.scheduler.advanceTimeBy(699)
        assertEquals(0, repository.documentSaveCount)
        mainDispatcherRule.scheduler.advanceTimeBy(1)
        mainDispatcherRule.scheduler.runCurrent()

        assertEquals(1, repository.documentSaveCount)
        assertFalse(viewModel.uiState.value.isDirty)
        assertEquals(viewModel.uiState.value.editRevision, viewModel.uiState.value.savedRevision)
        assertEquals(SaveState.Saved, viewModel.uiState.value.saveState)
    }

    @Test
    fun roomAcknowledgementDoesNotOverwriteDirtyLocalDraft() {
        val repository = EditorFakeRepository(script())
        val viewModel = EditorViewModel(SavedStateHandle(mapOf("scriptId" to "script")), repository)
        mainDispatcherRule.scheduler.runCurrent()

        viewModel.updateTitle("本地草稿")
        repository.emit(script().copy(title = "数据库旧值"))
        mainDispatcherRule.scheduler.runCurrent()

        assertEquals("本地草稿", viewModel.uiState.value.title)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun flushSavesImmediately_andFailureCanRetry() {
        val repository = EditorFakeRepository(script())
        val viewModel = EditorViewModel(SavedStateHandle(mapOf("scriptId" to "script")), repository)
        mainDispatcherRule.scheduler.runCurrent()
        viewModel.updateTitle("需要立即保存")
        repository.failWrites = true
        viewModel.flush()
        mainDispatcherRule.scheduler.runCurrent()
        assertEquals(SaveState.Error, viewModel.uiState.value.saveState)

        repository.failWrites = false
        viewModel.retrySave()
        mainDispatcherRule.scheduler.runCurrent()
        assertEquals(SaveState.Saved, viewModel.uiState.value.saveState)
        assertEquals("需要立即保存", repository.current.value?.title)
    }

    @Test
    fun undoRestoresTextStyleAndSelection() {
        val repository = EditorFakeRepository(script())
        val viewModel = EditorViewModel(SavedStateHandle(mapOf("scriptId" to "script")), repository)
        mainDispatcherRule.scheduler.runCurrent()
        viewModel.replaceText("正文增加", TextSelection(4, 4))
        viewModel.updateSelection(TextSelection(0, 2))
        viewModel.toggleStyle(ScriptSpanStyle.Bold)
        assertTrue(viewModel.uiState.value.editor.canUndo)

        viewModel.undo()

        assertEquals(TextSelection(0, 2), viewModel.uiState.value.editor.selection)
        assertFalse(viewModel.uiState.value.editor.document.blocks.first().let { it as ScriptBlock.Paragraph }.spans.any { it.bold })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    val scheduler get() = dispatcher.scheduler
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}

private class EditorFakeRepository(initial: Script) : ScriptRepository {
    val current = MutableStateFlow<Script?>(initial)
    var failWrites = false
    var documentSaveCount = 0

    fun emit(script: Script?) { current.value = script }
    override fun observeAll(): Flow<List<Script>> = MutableStateFlow(listOfNotNull(current.value))
    override fun observeInFolder(folderId: String): Flow<List<Script>> = observeAll()
    override fun observeUncategorized(): Flow<List<Script>> = observeAll()
    override fun observeById(id: String): Flow<Script?> = current
    override suspend fun getById(id: String): Script? = current.value
    override suspend fun create(folderId: String?): Script = error("Not used")
    override suspend fun createFromDocument(title: String, document: ScriptDocument, folderId: String?): Script = error("Not used")
    override suspend fun updateTitle(id: String, title: String) {
        if (failWrites) error("write failed")
        current.value = current.value?.copy(title = title.trim().ifBlank { "未命名台本" })
    }
    override suspend fun updateDocument(id: String, document: ScriptDocument) {
        if (failWrites) error("write failed")
        documentSaveCount += 1
        current.value = current.value?.copy(content = document)
    }
    override suspend fun move(id: String, folderId: String?) = Unit
    override suspend fun updatePlaybackSettings(id: String, settings: PlaybackSettings) = Unit
    override suspend fun delete(id: String) { current.value = null }
}

private fun script(): Script = Script(
    id = "script",
    title = "标题",
    plainTextPreview = "正文",
    content = ScriptContent(listOf(ScriptBlock.Paragraph("p", listOf(ScriptSpan("正文"))))),
    folderId = null,
    wordCount = 2,
    normalEstimatedDurationSeconds = 1,
    lastModifiedAt = 1,
    playbackSettings = PlaybackSettings(),
)
