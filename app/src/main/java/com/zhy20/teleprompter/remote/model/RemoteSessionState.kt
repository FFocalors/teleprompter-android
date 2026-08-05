package com.zhy20.teleprompter.remote.model

/**
 * Aggregate session state exposed to the UI. The connection status is the source of truth
 * for connection, and the optional snapshot mirrors the prompter device for the current
 * session role.
 */
data class RemoteSessionState(
    val status: RemoteConnectionStatus = RemoteConnectionStatus.Disabled,
    val snapshot: RemotePrompterSnapshot? = null,
    val commandInFlight: Boolean = false,
) {
    val isConnected: Boolean get() = status is RemoteConnectionStatus.Connected

    /** True while a controller is connected but the prompter has not produced a snapshot. */
    val awaitingSnapshot: Boolean get() = status is RemoteConnectionStatus.Connected && snapshot == null
}
