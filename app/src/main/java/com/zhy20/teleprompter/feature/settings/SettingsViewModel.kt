package com.zhy20.teleprompter.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.data.repository.GlobalSettings
import com.zhy20.teleprompter.data.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val _settings = MutableStateFlow(GlobalSettings())
    val settings: StateFlow<GlobalSettings> = _settings.asStateFlow()
    private var saveDefaultsJob: Job? = null

    init {
        viewModelScope.launch { repository.settings.collectLatest { _settings.value = it } }
    }

    fun updateDefaults(settings: PlaybackSettings) {
        _settings.value = _settings.value.copy(playbackDefaults = settings)
        saveDefaultsJob?.cancel()
        saveDefaultsJob = viewModelScope.launch { delay(350); repository.updatePlaybackDefaults(settings) }
    }

    fun updateLanguage(languageTag: String) {
        val normalized = if (languageTag.startsWith("en", ignoreCase = true)) "en-US" else "zh-CN"
        _settings.value = _settings.value.copy(languageTag = normalized)
        viewModelScope.launch { repository.updateLanguage(normalized) }
    }
}
