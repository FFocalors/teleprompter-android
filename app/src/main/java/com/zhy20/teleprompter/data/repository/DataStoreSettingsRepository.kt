package com.zhy20.teleprompter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.data.serialization.PlaybackSettingsSerializer
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<GlobalSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            GlobalSettings(
                playbackDefaults = preferences[PlaybackDefaults]?.let(PlaybackSettingsSerializer::decode) ?: PlaybackSettings(),
                languageTag = preferences[LanguageTag] ?: "zh-CN",
            )
        }

    override suspend fun updatePlaybackDefaults(settings: PlaybackSettings) {
        dataStore.edit { preferences -> preferences[PlaybackDefaults] = PlaybackSettingsSerializer.encode(settings) }
    }

    override suspend fun updateLanguage(languageTag: String) {
        val normalized = if (languageTag.startsWith("en", ignoreCase = true)) "en-US" else "zh-CN"
        dataStore.edit { preferences -> preferences[LanguageTag] = normalized }
    }

    private companion object {
        val PlaybackDefaults = stringPreferencesKey("playback_defaults_json")
        val LanguageTag = stringPreferencesKey("language_tag")
    }
}
