package com.zhy20.teleprompter.remote.pairing

import com.zhy20.teleprompter.remote.protocol.RemoteProtocol

/**
 * Stable model carried inside a pairing QR code. The controller parses this to learn where
 * the prompter's WebSocket server listens and what credentials prove it may join.
 *
 * @param expiresAtEpochMillis the pairing QR becomes invalid after this wall-clock time.
 */
data class RemotePairingPayload(
    val protocolVersion: Int,
    val host: String,
    val port: Int,
    val sessionId: String,
    val pairingToken: String,
    val expiresAtEpochMillis: Long,
)

/**
 * Structured error returned when parsing or validating a pairing payload fails.
 */
sealed class RemotePairingError(message: String? = null) : Throwable(message) {
    data object NotPairingUri : RemotePairingError()
    data object MissingFields : RemotePairingError()
    data object InvalidHost : RemotePairingError()
    data object InvalidPort : RemotePairingError()
    data object InvalidToken : RemotePairingError()
    data object InvalidSession : RemotePairingError()
    data object InvalidExpiry : RemotePairingError()
    data object UnsupportedVersion : RemotePairingError()
    data object Expired : RemotePairingError()
    data object Malformed : RemotePairingError()
}

/** Limits applied by the pairing codec. */
const val MAX_TOKEN_LENGTH = 128
const val MAX_SESSION_LENGTH = 64
const val MIN_PAIRING_TOKEN_CHARS = 32
const val PAIRING_VALIDITY_MILLIS = 5 * 60 * 1_000L

/** Scheme used for the pairing URI: `teleprompter://pair?...`. */
const val PAIRING_SCHEME = "teleprompter"
const val PAIRING_HOST = "pair"
