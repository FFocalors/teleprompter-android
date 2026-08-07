package com.zhy20.teleprompter.remote.session

import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemoteReadingCursor
import com.zhy20.teleprompter.remote.model.RemoteReadingWindow
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.model.RemoteSessionState
import com.zhy20.teleprompter.remote.pairing.RemotePairingPayload
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.protocol.RemoteMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle and protocol hub for a remote session. Composable code observes the state flows;
 * nobody except this repository talks to the transport.
 *
 * The repository does not navigate and does not execute playback itself. It surfaces
 * structured [RemoteSessionEffect]s so an application coordinator can perform navigation and
 * call the real business methods.
 */
interface RemoteSessionRepository {

    val sessionState: StateFlow<RemoteSessionState>
    val snapshot: StateFlow<RemotePrompterSnapshot?>

    /** Controller-side: the latest reading window received from the prompter (low-frequency). */
    val readingWindow: StateFlow<RemoteReadingWindow?>

    /** Controller-side: the latest absolute reading cursor received (high-frequency, latest-only). */
    val readingCursor: StateFlow<RemoteReadingCursor?>

    /** Commands received from the controller, emitted exactly once after deduplication. */
    val incomingCommands: Flow<RemoteCommand>

    /** Effects the application coordinator must perform (navigation + business execution). */
    val sessionEffects: Flow<RemoteSessionEffect>

    /** The local device this repository represents. */
    val localDevice: RemoteDeviceInfoHolder

    /** Chooses this device's role and prepares a session. */
    suspend fun prepare(role: RemoteRole)

    /** Prompter: starts the server and generates a fresh pairing payload. */
    suspend fun startWaiting()

    /** Prompter: stops waiting and invalidates the pairing QR. */
    suspend fun stopWaiting()

    /** Controller: connects to a prompter using the scanned pairing payload. */
    suspend fun connectToPrompter(payload: RemotePairingPayload)

    /** Controller: manual connect using explicit host/port + session/token. */
    suspend fun connectManual(host: String, port: Int, sessionId: String, token: String)

    /** Disconnects the active session and returns to the disabled state. */
    suspend fun disconnect()

    /**
     * Prompter: disconnects the currently connected controller only. The server keeps
     * running with a fresh session/token (new QR); the old credentials are destroyed and no
     * reconnect is possible with them.
     */
    suspend fun disconnectController()

    /**
     * Controller: explicitly disconnects from the prompter. Sends a DisconnectNotice, stops
     * the client, forbids auto-reconnect, and clears credentials + snapshot.
     */
    suspend fun disconnectFromPrompter()

    /**
     * Prompter: fully stops hosting — disconnects any controller, closes the server, stops
     * heartbeat/waiting work, clears pairing data, and returns to Disabled (role kept).
     */
    suspend fun stopHosting()

    /** Disconnects and clears the chosen role so the user can re-pick prompter/controller. */
    suspend fun resetRole()

    /** Controller: sends a command request. */
    suspend fun sendCommand(command: RemoteCommand)

    /** Prompter: publishes a fresh prompter snapshot to the controller. */
    fun updatePrompterSnapshot(snapshot: RemotePrompterSnapshot)

    /** Prompter: publishes a reading text window (sent only when the window actually changes). */
    fun updateReadingWindow(update: RemoteMessage.ReadingWindowUpdate)

    /** Prompter: publishes the absolute reading cursor (high-frequency, latest-only). */
    fun updateReadingCursor(update: RemoteMessage.ReadingCursorUpdate)

    /** Clears the in-memory deduplication history (used after reconnect). */
    fun resetCommandHistory()

    /** Consumes any pending result for [commandId] (returns it once, then null). */
    fun takeCommandResult(commandId: String): RemoteCommandResultState?

    /** Records a command result (prompter role) and sends the CommandResult to the peer. */
    fun recordResult(commandId: String, result: RemoteCommandResultState)
}

/** Small holder so the repository exposes its own device identity immutably. */
interface RemoteDeviceInfoHolder {
    val device: com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
}

/** Result of a controller command, produced by the prompter. */
data class RemoteCommandResultState(
    val commandId: String,
    val success: Boolean,
    val errorReason: com.zhy20.teleprompter.remote.protocol.RemoteRejectReason? = null,
    val errorMessage: String? = null,
    val resultingSnapshotRevision: Long? = null,
)

/**
 * Structured request for the application layer. The repository never holds a NavController;
 * the coordinator observes this and performs real navigation + business calls.
 */
sealed interface RemoteSessionEffect {
    /** The controller asked the prompter to execute the given command. */
    data class ExecuteCommand(val command: RemoteCommand) : RemoteSessionEffect
}

/** What the application layer must do for a navigation-affecting remote command. */
sealed interface RemoteNavigationEffect {
    /** Start playing [scriptId] after confirming setup is saved and navigating. */
    data class StartPrompter(val scriptId: String) : RemoteNavigationEffect
}

/** A pure, injectable mapping from a remote command to a navigation effect, for tests. */
object RemoteCommandToEffect {
    fun map(command: RemoteCommand): RemoteNavigationEffect? = when (command) {
        is RemoteCommand.StartPlayback -> RemoteNavigationEffect.StartPrompter(command.scriptId)
        else -> null
    }
}
