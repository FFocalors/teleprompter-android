package com.zhy20.teleprompter.remote.protocol

import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemoteRole

/**
 * Wire-format messages exchanged between the prompter and a controller.
 *
 * Every message is transport-agnostic and never references Compose, Android or database
 * types. The handshake is explicit: a controller first sends [ClientHello] and must receive
 * [ServerAccepted] before it is allowed to send [CommandRequest].
 */
sealed interface RemoteMessage {

    /**
     * First message a controller sends after the WebSocket opens. The prompter validates the
     * protocol version, pairing session and token before accepting.
     */
    data class ClientHello(
        val protocolVersion: Int,
        val sessionId: String,
        val pairingToken: String,
        val device: RemoteDeviceInfo,
    ) : RemoteMessage

    /** Prompter accepts the handshake and the connection becomes active. */
    data class ServerAccepted(
        val connectionId: String,
        val prompterDevice: RemoteDeviceInfo,
        /** In-memory credential used to resume a dropped connection. Never persisted. */
        val resumeToken: String,
        val initialSnapshot: RemotePrompterSnapshot? = null,
    ) : RemoteMessage

    /** Prompter rejects the handshake with a structured reason; the connection is closed. */
    data class ServerRejected(
        val reason: RemoteRejectReason,
        val message: String? = null,
    ) : RemoteMessage

    /** A controller asks the prompter to execute an action (only after acceptance). */
    data class CommandRequest(
        val command: RemoteCommand,
    ) : RemoteMessage

    /** The prompter acknowledges an executed command. */
    data class CommandResult(
        val commandId: String?,
        val success: Boolean,
        val errorReason: RemoteRejectReason? = null,
        val errorMessage: String? = null,
        val resultingSnapshotRevision: Long? = null,
    ) : RemoteMessage

    /** The prompter publishes the latest immutable snapshot. */
    data class SnapshotUpdate(
        val snapshot: RemotePrompterSnapshot,
    ) : RemoteMessage

    /**
     * Prompter → controller: a large contiguous window of the canonical text for local
     * re-layout. Low-frequency; sent only when the window actually changes.
     *
     * All offsets are UTF-16 code unit offsets into the canonical text.
     */
    data class ReadingWindowUpdate(
        val textRevision: Long,
        val windowRevision: Long,
        val startOffset: Int,
        val endOffset: Int,
        val text: String,
    ) : RemoteMessage

    /**
     * Prompter → controller: the current absolute reading position, expressed as a continuous
     * UTF-16 offset. High-frequency and tiny. [sequence] is monotonic; the controller ignores
     * out-of-order or stale sequences (latest-only).
     */
    data class ReadingCursorUpdate(
        val textRevision: Long,
        val absoluteOffset: Double,
        val sequence: Long,
        val sentAtElapsedRealtimeMillis: Long,
    ) : RemoteMessage

    /** Application-level keep-alive; used to detect a dead link. */
    data class HeartbeatPing(
        val sequence: Long,
    ) : RemoteMessage

    data class HeartbeatPong(
        val sequence: Long,
    ) : RemoteMessage

    /** Sent by either side on an intentional disconnect so the peer can react promptly. */
    data class DisconnectNotice(
        val reason: RemoteRejectReason? = null,
        val message: String? = null,
    ) : RemoteMessage

    /** Structured protocol-level failure (version mismatch, unsupported message, ...). */
    data class ProtocolError(
        val code: RemoteProtocolErrorCode,
        val message: String? = null,
    ) : RemoteMessage
}

/** Stable, machine-readable rejection/error reasons mapped to localized strings by the UI. */
enum class RemoteRejectReason {
    /** The protocol version is not supported. */
    ProtocolMismatch,
    /** The pairing session id does not match the current one. */
    SessionMismatch,
    /** The pairing token is missing, wrong, or already consumed. */
    InvalidToken,
    /** The pairing token has expired. */
    TokenExpired,
    /** Another controller is already connected; only one is allowed. */
    AlreadyConnected,
    /** The device info is missing or malformed. */
    InvalidDeviceInfo,
    /** The command is invalid or out of range. */
    InvalidCommand,
    /** The command cannot be executed in the current playback state. */
    CommandNotAllowedInState,
    /** The current setup settings could not be saved before starting playback. */
    SetupSaveFailed,
    /** The referenced script no longer exists. */
    ScriptNotFound,
    /** The command is unknown or the message is malformed. */
    Malformed,
    /** A generic internal failure. */
    InternalError,
}

/** Stable machine-readable protocol-level error codes. */
enum class RemoteProtocolErrorCode {
    UnsupportedVersion,
    MalformedMessage,
    CommandRejected,
}
