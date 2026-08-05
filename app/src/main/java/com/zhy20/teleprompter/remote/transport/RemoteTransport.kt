package com.zhy20.teleprompter.remote.transport

import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemoteFailureReason
import com.zhy20.teleprompter.remote.protocol.RemoteMessage
import kotlinx.coroutines.flow.Flow

/** Events emitted by a transport about the connection lifecycle. */
sealed interface RemoteTransportEvent {
    /** A peer connected; the handshake message follows on [RemoteTransport.incomingMessages]. */
    data class Connected(val device: RemoteDeviceInfo) : RemoteTransportEvent

    /** The link dropped; [reason] is null for a clean stop. */
    data class Disconnected(val reason: RemoteFailureReason? = null) : RemoteTransportEvent
}

/**
 * Connection-independent transport contract for remote messaging. Implementations must not
 * reference WebSocket/HTTP/TCP classes so a fake can run on the JVM in unit tests.
 *
 * Lifecycle is managed by the [RemoteSessionRepository], never by a Composable.
 */
interface RemoteTransport {
    val connectionEvents: Flow<RemoteTransportEvent>
    val incomingMessages: Flow<RemoteMessage>

    suspend fun start()
    suspend fun stop()
    suspend fun send(message: RemoteMessage)
}
