package com.zhy20.teleprompter.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.data.importer.ScriptImportCoordinator
import com.zhy20.teleprompter.data.importer.ScriptImportManager
import com.zhy20.teleprompter.data.importer.UriFileMetadataReader
import com.zhy20.teleprompter.data.local.TeleprompterDatabase
import com.zhy20.teleprompter.data.repository.DataStoreSettingsRepository
import com.zhy20.teleprompter.data.repository.RoomScriptFolderRepository
import com.zhy20.teleprompter.data.repository.RoomScriptRepository
import com.zhy20.teleprompter.data.repository.ScriptFolderRepository
import com.zhy20.teleprompter.data.repository.ScriptRepository
import com.zhy20.teleprompter.data.repository.SettingsRepository
import com.zhy20.teleprompter.remote.session.DefaultRemoteSessionRepository
import com.zhy20.teleprompter.remote.session.RemoteSessionRepository
import com.zhy20.teleprompter.remote.transport.FakeRemoteTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

private val Context.settingsDataStore by preferencesDataStore(name = "teleprompter_settings")

interface AppContainer {
    val database: TeleprompterDatabase
    val scriptRepository: ScriptRepository
    val folderRepository: ScriptFolderRepository
    val settingsRepository: SettingsRepository
    val scriptImportCoordinator: ScriptImportCoordinator
    val uriFileMetadataReader: UriFileMetadataReader
    val remoteSessionRepository: RemoteSessionRepository
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
    override val scriptImportCoordinator: ScriptImportCoordinator by lazy {
        ScriptImportCoordinator(
            manager = ScriptImportManager(),
            repository = scriptRepository,
        )
    }
    override val uriFileMetadataReader: UriFileMetadataReader by lazy { UriFileMetadataReader(context.contentResolver) }

    /**
     * This phase ships an in-memory fake transport so the demo works on a single device and
     * the unit tests can run on the JVM. Loopback echoes every sent message back as an
     * incoming message, letting the same app instance act as both the controller and the
     * prompter. A real network transport (WebSocket/TCP) will be swapped in here in a later
     * phase without changing the UI.
     */
    override val remoteSessionRepository: RemoteSessionRepository by lazy {
        DefaultRemoteSessionRepository(
            transport = FakeRemoteTransport(autoConnectDelayMillis = 1_200, loopback = true),
            scope = CoroutineScope(SupervisorJob()),
        )
    }
}
