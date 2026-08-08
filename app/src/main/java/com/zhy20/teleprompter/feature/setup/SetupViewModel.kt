package com.zhy20.teleprompter.feature.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.SaveState
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.data.repository.ScriptRepository
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

data class SetupUiState(
    val script: Script? = null,
    val settings: PlaybackSettings = PlaybackSettings(),
    val isLoading: Boolean = true,
    val isDirty: Boolean = false,
    val saveState: SaveState = SaveState.Initial,
    val revision: Long = 0,
    val error: SetupError? = null,
)

enum class SetupError { ScriptNotFound, LoadFailed, SaveFailed }

class SetupViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ScriptRepository,
) : ViewModel() {
    private val scriptId: String = requireNotNull(savedStateHandle["scriptId"])
    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()
    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    private var initialized = false

    init {
        viewModelScope.launch {
            runCatching {
                repository.observeById(scriptId).collectLatest { script ->
                    if (script == null) {
                        _uiState.value = _uiState.value.copy(script = null, isLoading = false, error = SetupError.ScriptNotFound)
                    } else if (!initialized || !_uiState.value.isDirty) {
                        initialized = true
                        _uiState.value = _uiState.value.copy(
                            script = script,
                            settings = script.playbackSettings,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
            }.onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = SetupError.LoadFailed) }
        }
    }

    fun updateSettings(settings: PlaybackSettings) {
        val current = _uiState.value
        if (settings == current.settings) return
        _uiState.value = current.copy(
            settings = settings,
            isDirty = true,
            saveState = SaveState.Saving,
            revision = current.revision + 1,
            error = null,
        )
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(700); saveLatest() }
    }

    fun flush(onComplete: (Boolean) -> Unit = {}) {
        saveJob?.cancel()
        viewModelScope.launch { onComplete(saveLatest()) }
    }

    /** Suspends until the pending save completes and returns whether it succeeded. */
    suspend fun flushNow(): Boolean {
        saveJob?.cancel()
        return saveLatest()
    }

    private suspend fun saveLatest(): Boolean = saveMutex.withLock {
        val snapshot = _uiState.value
        if (!snapshot.isDirty) return@withLock true
        return@withLock runCatching { repository.updatePlaybackSettings(scriptId, snapshot.settings) }.fold(
            onSuccess = {
                val latest = _uiState.value
                if (latest.revision == snapshot.revision) {
                    _uiState.value = latest.copy(isDirty = false, saveState = SaveState.Saved)
                }
                true
            },
            onFailure = {
                _uiState.value = _uiState.value.copy(saveState = SaveState.Error, error = SetupError.SaveFailed)
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
