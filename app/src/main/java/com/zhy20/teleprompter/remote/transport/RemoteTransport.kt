package com.zhy20.teleprompter.remote.transport

import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemoteFailureReason
import com.zhy20.teleprompter.remote.protocol.RemoteJsonCodec
import com.zhy20.teleprompter.remote.protocol.RemoteMessage
import com.zhy20.teleprompter.remote.protocol.RemoteProtocolErrorCode
import kotlinx.coroutines.flow.Flow

/** Events emitted by a transport about the connection lifecycle. */
sealed interface RemoteTransportEvent {
    /**
     * A transport connection opened. [device] is null here: the peer identity is only known
     * after the repository completes the handshake, which then emits the domain Connected.
     */
    data class Connected(val device: RemoteDeviceInfo? = null) : RemoteTransportEvent

    /** The link dropped; [reason] is null for a clean stop. */
    data class Disconnected(val reason: RemoteFailureReason? = null) : RemoteTransportEvent
}

/**
 * Connection-independent transport contract for remote messaging. Implementations must not
 * expose WebSocket/HTTP/TCP types so a fake can run on the JVM in unit tests.
 *
 * Lifecycle is managed by the [com.zhy20.teleprompter.remote.session.RemoteSessionRepository],
 * never by a Composable.
 */
interface RemoteTransport {
    val connectionEvents: Flow<RemoteTransportEvent>
    val incomingMessages: Flow<RemoteMessage>

    /** The role-specific start (prompter server or controller client). */
    suspend fun start()

    /** Fully tears down the connection/server and cancels heartbeat/reconnect work. */
    suspend fun stop()

    /** Sends a single protocol message; must be a no-op if not connected. */
    suspend fun send(message: RemoteMessage)
}
