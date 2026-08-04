package com.zhy20.teleprompter.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.data.local.TeleprompterDatabase
import com.zhy20.teleprompter.data.repository.DataStoreSettingsRepository
import com.zhy20.teleprompter.data.repository.RoomScriptFolderRepository
import com.zhy20.teleprompter.data.repository.RoomScriptRepository
import com.zhy20.teleprompter.data.repository.ScriptFolderRepository
import com.zhy20.teleprompter.data.repository.ScriptRepository
import com.zhy20.teleprompter.data.repository.SettingsRepository

private val Context.settingsDataStore by preferencesDataStore(name = "teleprompter_settings")

interface AppContainer {
    val database: TeleprompterDatabase
    val scriptRepository: ScriptRepository
    val folderRepository: ScriptFolderRepository
    val settingsRepository: SettingsRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: TeleprompterDatabase by lazy {
        Room.databaseBuilder(context, TeleprompterDatabase::class.java, "teleprompter.db").build()
    }
    override val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(context.settingsDataStore) }
    override val scriptRepository: ScriptRepository by lazy {
        RoomScriptRepository(
            scriptDao = database.scriptDao(),
            folderDao = database.scriptFolderDao(),
            settingsRepository = settingsRepository,
            defaultTitle = { context.getString(R.string.untitled_script) },
        )
    }
    override val folderRepository: ScriptFolderRepository by lazy {
        RoomScriptFolderRepository(database, database.scriptFolderDao(), database.scriptDao())
    }
}
