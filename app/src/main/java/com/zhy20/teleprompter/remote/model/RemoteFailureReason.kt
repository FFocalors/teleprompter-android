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
    /** No usable local network address; the prompter cannot start waiting. */
    NoNetworkAddress,
    /** The pairing QR was invalid or has expired. */
    InvalidPairing,
    /** The controller timed out waiting for the prompter to accept. */
    ConnectionTimeout,
    /** The peer already has a connected controller. */
    AlreadyConnected,
    /** The server port could not be bound. */
    PortUnavailable,
}
