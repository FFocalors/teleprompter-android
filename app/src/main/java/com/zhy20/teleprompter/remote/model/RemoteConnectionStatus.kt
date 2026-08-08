package com.zhy20.teleprompter.remote.model

/**
 * Connection lifecycle of a remote session, expressed as a sealed type so the UI never has
 * to guess what the next state is and later phases can add pairing, handshake, reconnect
 * and rejection without widening a flat enum.
 */
sealed interface RemoteConnectionStatus {

    /** No session is active; the user has not started waiting. */
    data object Disabled : RemoteConnectionStatus

    /** The session is set up and idle. */
    data object Ready : RemoteConnectionStatus

    /** The prompter is broadcasting/waiting for a controller to connect. */
    data object WaitingForController : RemoteConnectionStatus

    /** A handshake is in progress. */
    data object Connecting : RemoteConnectionStatus

    /** A controller is connected. */
    data class Connected(
        val device: RemoteDeviceInfo,
    ) : RemoteConnectionStatus

    /** The link dropped and the session is trying to reconnect. */
    data class Reconnecting(
        val device: RemoteDeviceInfo?,
    ) : RemoteConnectionStatus

    /** The connection failed for a structured reason. */
    data class Failed(
        val reason: RemoteFailureReason,
    ) : RemoteConnectionStatus
}
