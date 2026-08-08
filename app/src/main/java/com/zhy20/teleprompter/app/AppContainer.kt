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
import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.network.LocalNetworkAddressProvider
import com.zhy20.teleprompter.remote.session.DefaultRemoteSessionRepository
import com.zhy20.teleprompter.remote.session.RemoteSessionRepository
import com.zhy20.teleprompter.remote.transport.WebSocketRemoteTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    val localNetworkAddressProvider: LocalNetworkAddressProvider
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
    override val localNetworkAddressProvider: LocalNetworkAddressProvider by lazy {
        LocalNetworkAddressProvider(context)
    }

    /**
     * Production default: a real LAN WebSocket transport managed app-wide by this container,
     * so navigation and rotation never restart the server. The prompter role binds on port
     * 8765 (with the WebSocket server falling back to an OS-assigned port if occupied); the
     * controller role connects to whatever host/port the scanned QR carries.
     */
    override val remoteSessionRepository: RemoteSessionRepository by lazy {
        // Per-process stable id: enough for pairing within a session. An app process death
        // ends the session anyway (pairing token + resume token live in memory only), so a
        // hardware identifier is unnecessary and privacy-friendlier to avoid.
        val device = RemoteDeviceInfo(
            deviceId = java.util.UUID.randomUUID().toString(),
            displayName = android.os.Build.MODEL,
            role = RemoteRole.Prompter,
        )
        DefaultRemoteSessionRepository(
            transport = WebSocketRemoteTransport(
                bindPort = 8765,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            device = device,
            lanAddressProvider = { localNetworkAddressProvider.currentAddress() },
        )
    }
}
