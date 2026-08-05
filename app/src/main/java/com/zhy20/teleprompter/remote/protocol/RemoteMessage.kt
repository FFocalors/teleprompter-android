package com.zhy20.teleprompter.remote.protocol

import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemoteRole

/**
 * Wire-format messages exchanged between the prompter and a controller. Every message is
 * transport-agnostic and never references Compose, Android or database types.
 */
sealed interface RemoteMessage {

    /** Declared protocol version and device role; used for handshake and version checks. */
    data class Hello(
        val protocolVersion: Int,
        val role: RemoteRole,
        val device: RemoteDeviceInfo,
    ) : RemoteMessage

    /** A controller asks the prompter to execute an action. */
    data class Command(
        val command: RemoteCommand,
    ) : RemoteMessage

    /** The prompter publishes the latest immutable snapshot. */
    data class Snapshot(
        val snapshot: RemotePrompterSnapshot,
    ) : RemoteMessage

    /** Acknowledges an executed command. @param commandId may be null for protocol errors. */
    data class CommandResult(
        val commandId: String?,
        val accepted: Boolean,
        val errorMessage: String? = null,
    ) : RemoteMessage

    /** Periodic keep-alive, also used to detect a dead link. */
    data class Heartbeat(
        val sequence: Long,
    ) : RemoteMessage

    /** Structured protocol-level failure (version mismatch, unsupported message, ...). */
    data class ProtocolError(
        val code: RemoteProtocolErrorCode,
        val message: String? = null,
    ) : RemoteMessage
}

/** Stable machine-readable error codes so the UI can map to localized strings. */
enum class RemoteProtocolErrorCode {
    UnsupportedVersion,
    MalformedMessage,
    CommandRejected,
}
