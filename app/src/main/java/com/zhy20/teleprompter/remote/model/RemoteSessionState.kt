package com.zhy20.teleprompter.remote.model

import com.zhy20.teleprompter.remote.pairing.RemotePairingPayload

/**
 * Aggregate session state exposed to the UI. The connection status is the source of truth
 * for connection, and the optional snapshot mirrors the peer device.
 *
 * @param role the role this device plays in the session (null before a role is chosen).
 * @param pairingPayload the active prompter pairing QR (only while waiting).
 * @param reconnecting true while the controller is in an automatic reconnect window.
 */
data class RemoteSessionState(
    val status: RemoteConnectionStatus = RemoteConnectionStatus.Disabled,
    val snapshot: RemotePrompterSnapshot? = null,
    val commandInFlight: Boolean = false,
    val role: RemoteRole? = null,
    val pairingPayload: RemotePairingPayload? = null,
    val reconnecting: Boolean = false,
    val lastCommandError: String? = null,
) {
    val isConnected: Boolean get() = status is RemoteConnectionStatus.Connected

    /** True while a controller is connected but the prompter has not produced a snapshot. */
    val awaitingSnapshot: Boolean get() = status is RemoteConnectionStatus.Connected && snapshot == null
}
