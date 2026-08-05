package com.zhy20.teleprompter.remote.session

import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemoteSessionState
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle and protocol hub for a remote session. Composable code observes the state flows;
 * nobody except this repository talks to the [RemoteTransport].
 *
 * The repository does not navigate and does not execute playback itself. It surfaces
 * structured [RemoteSessionEffect]s so an application coordinator can perform navigation and
 * call the real business methods.
 */
interface RemoteSessionRepository {

    val sessionState: StateFlow<RemoteSessionState>
    val snapshot: StateFlow<RemotePrompterSnapshot?>

    /** Commands received from the controller, emitted exactly once after deduplication. */
    val incomingCommands: Flow<RemoteCommand>

    /** Effects the application coordinator must perform (navigation + business execution). */
    val sessionEffects: Flow<RemoteSessionEffect>

    /** Starts waiting for a controller to connect and drives the fake transport. */
    suspend fun startWaiting()

    /** Stops waiting and disconnects the current session. */
    suspend fun stopWaiting()

    /** Disconnects an active session, returning the session to the disabled state. */
    suspend fun disconnect()

    /** Sends a controller command through the transport. */
    suspend fun sendCommand(command: RemoteCommand)

    /** Publishes a fresh prompter snapshot to the transport. */
    fun updatePrompterSnapshot(snapshot: RemotePrompterSnapshot)

    /** Clears the in-memory deduplication history (used after reconnect). */
    fun resetCommandHistory()
}

/**
 * Structured request for the application layer. The repository never holds a NavController;
 * the coordinator observes this and performs real navigation + business calls.
 */
sealed interface RemoteSessionEffect {
    /** The controller asked the prompter to start playing the given script. */
    data class ExecuteCommand(val command: RemoteCommand) : RemoteSessionEffect
}

/** What the application layer must do for a navigation-affecting remote command. */
sealed interface RemoteNavigationEffect {
    /** Start playing [scriptId] and navigate to the prompter page. */
    data class StartPrompter(val scriptId: String) : RemoteNavigationEffect
}

/** A pure, injectable mapping from a remote command to a navigation effect, for tests. */
object RemoteCommandToEffect {
    fun map(command: RemoteCommand): RemoteNavigationEffect? = when (command) {
        is RemoteCommand.StartPlayback -> RemoteNavigationEffect.StartPrompter(command.scriptId)
        else -> null
    }
}
