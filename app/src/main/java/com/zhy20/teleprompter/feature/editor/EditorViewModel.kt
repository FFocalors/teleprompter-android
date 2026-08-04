package com.zhy20.teleprompter.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhy20.teleprompter.core.model.RichTextEditorState
import com.zhy20.teleprompter.core.model.SaveState
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptSpanStyle
import com.zhy20.teleprompter.core.model.TextSelection
import com.zhy20.teleprompter.data.repository.ScriptRepository
import com.zhy20.teleprompter.data.serialization.ScriptDocumentSerializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EditorUiState(
    val scriptId: String,
    val title: String = "",
    val editor: RichTextEditorState = RichTextEditorState(ScriptDocumentSerializer.emptyDocument()),
    val isLoading: Boolean = true,
    val isDirty: Boolean = false,
    val saveState: SaveState = SaveState.Initial,
    val editRevision: Long = 0,
    val savedRevision: Long = 0,
    val errorMessage: EditorError? = null,
) {
    val undoAvailable: Boolean get() = editor.canUndo
}

enum class EditorError { ScriptNotFound, LoadFailed, SaveFailed }

class EditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ScriptRepository,
) : ViewModel() {
    private val scriptId: String = requireNotNull(savedStateHandle["scriptId"])
    private val _uiState = MutableStateFlow(EditorUiState(scriptId = scriptId))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    private var initialized = false

    init {
        viewModelScope.launch {
            runCatching {
                repository.observeById(scriptId).collectLatest { script ->
                    if (script == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isDirty = false,
                            errorMessage = EditorError.ScriptNotFound,
                        )
                        return@collectLatest
                    }
                    if (!initialized) {
                        initialized = true
                        _uiState.value = EditorUiState(
                            scriptId = scriptId,
                            title = script.title,
                            editor = RichTextEditorState(script.content),
                            isLoading = false,
                        )
                    }
                    // Subsequent Room emissions are acknowledgements. Local dirty drafts remain authoritative.
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = EditorError.LoadFailed)
            }
        }
    }

    fun updateTitle(title: String) = edit { it.copy(title = title) }

    fun replaceText(text: String, selection: TextSelection) {
        if (text == _uiState.value.editor.text) {
            updateSelection(selection)
            return
        }
        edit { state -> state.copy(editor = state.editor.replaceText(text, selection)) }
    }

    fun updateSelection(selection: TextSelection) {
        _uiState.value = _uiState.value.copy(editor = _uiState.value.editor.withSelection(selection))
    }

    fun toggleStyle(style: ScriptSpanStyle) = edit { state -> state.copy(editor = state.editor.toggleStyle(style)) }
    fun undo() = edit { state -> state.copy(editor = state.editor.undo()) }
    fun retrySave() { scheduleSave(delayMillis = 0) }

    fun flush(onComplete: (Boolean) -> Unit = {}) {
        saveJob?.cancel()
        viewModelScope.launch { onComplete(saveLatest()) }
    }

    private fun edit(transform: (EditorUiState) -> EditorUiState) {
        val before = _uiState.value
        val transformed = transform(before)
        if (transformed.title == before.title && transformed.editor == before.editor) return
        _uiState.value = transformed.copy(
            isDirty = true,
            saveState = if (before.saveState == SaveState.Error) SaveState.Error else SaveState.Saving,
            editRevision = before.editRevision + 1,
            errorMessage = null,
        )
        scheduleSave()
    }

    private fun scheduleSave(delayMillis: Long = 700) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(delayMillis)
            saveLatest()
        }
    }

    private suspend fun saveLatest(): Boolean = saveMutex.withLock {
        val snapshot = _uiState.value
        if (!snapshot.isDirty && snapshot.saveState != SaveState.Error) return@withLock true
        _uiState.value = snapshot.copy(saveState = SaveState.Saving, errorMessage = null)
        var storedTitle: String? = null
        return@withLock runCatching {
            repository.updateTitle(scriptId, snapshot.title)
            repository.updateDocument(scriptId, snapshot.editor.document)
            storedTitle = repository.getById(scriptId)?.title
        }.fold(
            onSuccess = {
                val latest = _uiState.value
                if (latest.editRevision == snapshot.editRevision) {
                    _uiState.value = latest.copy(
                        title = storedTitle ?: snapshot.title,
                        isDirty = false,
                        saveState = SaveState.Saved,
                        savedRevision = snapshot.editRevision,
                    )
                }
                true
            },
            onFailure = {
                val latest = _uiState.value
                _uiState.value = latest.copy(saveState = SaveState.Error, errorMessage = EditorError.SaveFailed)
                false
            },
        )
    }

    override fun onCleared() {
        saveJob?.cancel()
        if (_uiState.value.isDirty) runBlocking { withTimeoutOrNull(1_500) { saveLatest() } }
        super.onCleared()
    }
}
