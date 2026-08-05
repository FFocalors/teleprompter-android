package com.zhy20.teleprompter.remote.model

/**
 * Structured reason for a failed connection. UI keeps user-visible text in string
 * resources and maps from this type, so the protocol model never leaks display strings.
 */
enum class RemoteFailureReason {
    /** The peer never accepted the handshake or the link went down. */
    HandshakeFailed,
    /** The peer speaks a different protocol version. */
    ProtocolMismatch,
    /** Transport refused to start or stopped unexpectedly. */
    TransportUnavailable,
    /** The peer rejected the connection. */
    Rejected,
}
