package com.zhy20.teleprompter.remote.protocol

/**
 * Commands a controller sends to the prompter. Each command carries a unique [commandId]
 * so the repository can deduplicate re-delivered commands and correlate results.
 *
 * These are deliberately separate from the app-internal [PlaybackEvent]: the protocol model
 * stays stable and testable, and a conversion layer maps it to business events.
 */
sealed interface RemoteCommand {

    val commandId: String

    data class StartPlayback(
        override val commandId: String,
        val scriptId: String,
    ) : RemoteCommand

    data class PausePlayback(
        override val commandId: String,
    ) : RemoteCommand

    data class ResumeImmediately(
        override val commandId: String,
    ) : RemoteCommand

    data class ResumeWithCountdown(
        override val commandId: String,
    ) : RemoteCommand

    data class SeekBy(
        override val commandId: String,
        /** Delta in normalized progress units, validated to `-1f..1f`. */
        val delta: Float,
    ) : RemoteCommand

    data class ChangeSpeed(
        override val commandId: String,
        /** Signed speed delta, validated to a finite value within `-5f..5f`. */
        val delta: Float,
    ) : RemoteCommand

    data class EndPlayback(
        override val commandId: String,
    ) : RemoteCommand
}

/** Maximum delta magnitude accepted for [RemoteCommand.SeekBy] (normalized progress units). */
const val MAX_SEEK_DELTA = 1f

/** Maximum delta magnitude accepted for [RemoteCommand.ChangeSpeed] (speed multiplier units). */
const val MAX_SPEED_DELTA = 5f

/**
 * Range-checks a command's numeric payloads. Returns a descriptive message, or null when
 * the command is valid and safe to execute.
 */
fun RemoteCommand.validationError(): String? = when (this) {
    is RemoteCommand.StartPlayback ->
        if (scriptId.isBlank()) "StartPlayback requires a scriptId" else null
    is RemoteCommand.SeekBy ->
        if (!delta.isFinite()) "SeekBy delta must be finite"
        else if (delta.coerceIn(-MAX_SEEK_DELTA, MAX_SEEK_DELTA) != delta) "SeekBy delta out of range"
        else null
    is RemoteCommand.ChangeSpeed ->
        if (!delta.isFinite()) "ChangeSpeed delta must be finite"
        else if (delta.coerceIn(-MAX_SPEED_DELTA, MAX_SPEED_DELTA) != delta) "ChangeSpeed delta out of range"
        else null
    else -> null
}
