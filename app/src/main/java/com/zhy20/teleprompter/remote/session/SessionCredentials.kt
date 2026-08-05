package com.zhy20.teleprompter.remote.session

import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo

/**
 * Mutable in-memory credentials for an active or pending pairing session. Never persisted.
 */
data class SessionCredentials(
    val sessionId: String,
    val pairingToken: String,
    val expiresAtEpochMillis: Long,
    /** Set after a successful handshake; used for reconnection. */
    var connectionId: String? = null,
    var resumeToken: String? = null,
)
