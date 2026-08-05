package com.zhy20.teleprompter.remote.protocol

import com.zhy20.teleprompter.remote.model.MAX_NEARBY_TEXT_LENGTH
import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemotePlaybackState
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemotePrompterSurface
import com.zhy20.teleprompter.remote.model.RemoteRole
import org.json.JSONObject

/**
 * Explicit, hand-written JSON codec for [RemoteMessage]. It intentionally avoids Java
 * serialization and reflection so unknown fields are ignored, missing/wrong-typed fields are
 * rejected with a structured [RemoteProtocolErrorCode], and malicious payloads cannot reach
 * domain logic unvalidated.
 *
 * Size and string-length limits mirror the domain models so a peer cannot push unbounded data.
 */
object RemoteJsonCodec {

    private const val MAX_STRING_LENGTH = 4096
    private const val MAX_MESSAGE_LENGTH = 64 * 1024

    private const val KEY_TYPE = "type"
    private const val KEY_PROTOCOL_VERSION = "protocolVersion"
    private const val KEY_SESSION_ID = "sessionId"
    private const val KEY_PAIRING_TOKEN = "pairingToken"
    private const val KEY_DEVICE = "device"
    private const val KEY_DEVICE_ID = "deviceId"
    private const val KEY_DISPLAY_NAME = "displayName"
    private const val KEY_ROLE = "role"
    private const val KEY_CONNECTION_ID = "connectionId"
    private const val KEY_PROMPTER_DEVICE = "prompterDevice"
    private const val KEY_RESUME_TOKEN = "resumeToken"
    private const val KEY_INITIAL_SNAPSHOT = "initialSnapshot"
    private const val KEY_REASON = "reason"
    private const val KEY_MESSAGE = "message"
    private const val KEY_COMMAND = "command"
    private const val KEY_COMMAND_ID = "commandId"
    private const val KEY_SUCCESS = "success"
    private const val KEY_ERROR_REASON = "errorReason"
    private const val KEY_RESULTING_REVISION = "resultingSnapshotRevision"
    private const val KEY_SNAPSHOT = "snapshot"
    private const val KEY_SEQUENCE = "sequence"
    private const val KEY_CODE = "code"
    private const val KEY_REVISION = "revision"
    private const val KEY_SURFACE = "surface"
    private const val KEY_SCRIPT_ID = "scriptId"
    private const val KEY_SCRIPT_TITLE = "scriptTitle"
    private const val KEY_ESTIMATED_DURATION = "estimatedDurationSeconds"
    private const val KEY_PLAYBACK_STATE = "playbackState"
    private const val KEY_IS_PLAYING = "isPlaying"
    private const val KEY_IS_PAUSED = "isPaused"
    private const val KEY_IS_COUNTDOWN = "isCountdown"
    private const val KEY_IS_FINISHED = "isFinished"
    private const val KEY_COUNTDOWN_REMAINING = "countdownSecondsRemaining"
    private const val KEY_PROGRESS = "progress"
    private const val KEY_ELAPSED = "elapsedTimeMillis"
    private const val KEY_REMAINING = "remainingTimeMillis"
    private const val KEY_SPEED = "speedMultiplier"
    private const val KEY_NEARBY_TEXT = "nearbyText"
    private const val KEY_COMMAND_TYPE = "commandType"
    private const val KEY_DELTA = "delta"

    private const val TYPE_CLIENT_HELLO = "clientHello"
    private const val TYPE_SERVER_ACCEPTED = "serverAccepted"
    private const val TYPE_SERVER_REJECTED = "serverRejected"
    private const val TYPE_COMMAND_REQUEST = "commandRequest"
    private const val TYPE_COMMAND_RESULT = "commandResult"
    private const val TYPE_SNAPSHOT_UPDATE = "snapshotUpdate"
    private const val TYPE_HEARTBEAT_PING = "heartbeatPing"
    private const val TYPE_HEARTBEAT_PONG = "heartbeatPong"
    private const val TYPE_DISCONNECT_NOTICE = "disconnectNotice"
    private const val TYPE_PROTOCOL_ERROR = "protocolError"

    private const val COMMAND_START = "startPlayback"
    private const val COMMAND_PAUSE = "pausePlayback"
    private const val COMMAND_RESUME_NOW = "resumeImmediately"
    private const val COMMAND_RESUME_COUNTDOWN = "resumeWithCountdown"
    private const val COMMAND_SEEK = "seekBy"
    private const val COMMAND_SPEED = "changeSpeed"
    private const val COMMAND_END = "endPlayback"

    /**
     * Encodes a message to its wire JSON string. Fails (throws) only on programmer error —
     * every domain value is codec-safe.
     */
    fun encode(message: RemoteMessage): String {
        val json = JSONObject()
        when (message) {
            is RemoteMessage.ClientHello -> {
                json.put(KEY_TYPE, TYPE_CLIENT_HELLO)
                json.put(KEY_PROTOCOL_VERSION, message.protocolVersion)
                json.put(KEY_SESSION_ID, message.sessionId)
                json.put(KEY_PAIRING_TOKEN, message.pairingToken)
                json.put(KEY_DEVICE, encodeDevice(message.device))
            }
            is RemoteMessage.ServerAccepted -> {
                json.put(KEY_TYPE, TYPE_SERVER_ACCEPTED)
                json.put(KEY_CONNECTION_ID, message.connectionId)
                json.put(KEY_PROMPTER_DEVICE, encodeDevice(message.prompterDevice))
                json.put(KEY_RESUME_TOKEN, message.resumeToken)
                message.initialSnapshot?.let { json.put(KEY_INITIAL_SNAPSHOT, encodeSnapshot(it)) }
            }
            is RemoteMessage.ServerRejected -> {
                json.put(KEY_TYPE, TYPE_SERVER_REJECTED)
                json.put(KEY_REASON, message.reason.name)
                message.message?.let { json.put(KEY_MESSAGE, it) }
            }
            is RemoteMessage.CommandRequest -> {
                json.put(KEY_TYPE, TYPE_COMMAND_REQUEST)
                json.put(KEY_COMMAND, encodeCommand(message.command))
            }
            is RemoteMessage.CommandResult -> {
                json.put(KEY_TYPE, TYPE_COMMAND_RESULT)
                message.commandId?.let { json.put(KEY_COMMAND_ID, it) }
                json.put(KEY_SUCCESS, message.success)
                message.errorReason?.let { json.put(KEY_ERROR_REASON, it.name) }
                message.errorMessage?.let { json.put(KEY_MESSAGE, it) }
                message.resultingSnapshotRevision?.let { json.put(KEY_RESULTING_REVISION, it) }
            }
            is RemoteMessage.SnapshotUpdate -> {
                json.put(KEY_TYPE, TYPE_SNAPSHOT_UPDATE)
                json.put(KEY_SNAPSHOT, encodeSnapshot(message.snapshot))
            }
            is RemoteMessage.HeartbeatPing -> {
                json.put(KEY_TYPE, TYPE_HEARTBEAT_PING)
                json.put(KEY_SEQUENCE, message.sequence)
            }
            is RemoteMessage.HeartbeatPong -> {
                json.put(KEY_TYPE, TYPE_HEARTBEAT_PONG)
                json.put(KEY_SEQUENCE, message.sequence)
            }
            is RemoteMessage.DisconnectNotice -> {
                json.put(KEY_TYPE, TYPE_DISCONNECT_NOTICE)
                message.reason?.let { json.put(KEY_REASON, it.name) }
                message.message?.let { json.put(KEY_MESSAGE, it) }
            }
            is RemoteMessage.ProtocolError -> {
                json.put(KEY_TYPE, TYPE_PROTOCOL_ERROR)
                json.put(KEY_CODE, message.code.name)
                message.message?.let { json.put(KEY_MESSAGE, it) }
            }
        }
        return json.toString()
    }

    /**
     * Decodes a raw wire string into a [RemoteMessage] or a [RemoteDecodeError]. The codec
     * returns errors instead of throwing so the transport can send a structured protocol
     * error back without crashing.
     */
    fun decode(raw: String): Result<RemoteMessage> {
        if (raw.length > MAX_MESSAGE_LENGTH) {
            return Result.failure(RemoteDecodeError.Malformed("message too large"))
        }
        val json = runCatching { JSONObject(raw) }.getOrElse {
            return Result.failure(RemoteDecodeError.Malformed("invalid JSON"))
        }
        val type = json.optString(KEY_TYPE)
            ?: return Result.failure(RemoteDecodeError.Malformed("missing type"))

        return when (type) {
            TYPE_CLIENT_HELLO -> decodeClientHello(json)
            TYPE_SERVER_ACCEPTED -> decodeServerAccepted(json)
            TYPE_SERVER_REJECTED -> decodeServerRejected(json)
            TYPE_COMMAND_REQUEST -> decodeCommandRequest(json)
            TYPE_COMMAND_RESULT -> decodeCommandResult(json)
            TYPE_SNAPSHOT_UPDATE -> decodeSnapshotUpdate(json)
            TYPE_HEARTBEAT_PING -> decodeHeartbeat(json, pong = false)
            TYPE_HEARTBEAT_PONG -> decodeHeartbeat(json, pong = true)
            TYPE_DISCONNECT_NOTICE -> decodeDisconnectNotice(json)
            TYPE_PROTOCOL_ERROR -> decodeProtocolError(json)
            else -> Result.failure(RemoteDecodeError.UnknownType(type))
        }
    }

    // ---- encoders ----

    private fun encodeDevice(device: RemoteDeviceInfo): JSONObject = JSONObject()
        .put(KEY_DEVICE_ID, device.deviceId)
        .put(KEY_DISPLAY_NAME, device.displayName)
        .put(KEY_ROLE, device.role.name)

    private fun encodeSnapshot(snapshot: RemotePrompterSnapshot): JSONObject {
        val playback = JSONObject()
            .put(KEY_IS_PLAYING, snapshot.playbackState.isPlaying)
            .put(KEY_IS_PAUSED, snapshot.playbackState.isPaused)
            .put(KEY_IS_COUNTDOWN, snapshot.playbackState.isCountdown)
            .put(KEY_IS_FINISHED, snapshot.playbackState.isFinished)
        snapshot.playbackState.countdownSecondsRemaining?.let { playback.put(KEY_COUNTDOWN_REMAINING, it) }

        val json = JSONObject()
            .put(KEY_REVISION, snapshot.revision)
            .put(KEY_SURFACE, snapshot.surface.name)
        snapshot.scriptId?.let { json.put(KEY_SCRIPT_ID, it) }
        snapshot.scriptTitle?.let { json.put(KEY_SCRIPT_TITLE, it) }
        snapshot.estimatedDurationSeconds?.let { json.put(KEY_ESTIMATED_DURATION, it) }
        json.put(KEY_PLAYBACK_STATE, playback)
            .put(KEY_PROGRESS, snapshot.progress.toDouble())
            .put(KEY_ELAPSED, snapshot.elapsedTimeMillis)
            .put(KEY_REMAINING, snapshot.remainingTimeMillis)
            .put(KEY_SPEED, snapshot.speedMultiplier.toDouble())
        snapshot.countdownSecondsRemaining?.let { json.put(KEY_COUNTDOWN_REMAINING, it) }
        snapshot.nearbyText?.let { json.put(KEY_NEARBY_TEXT, it) }
        return json
    }

    private fun encodeCommand(command: RemoteCommand): JSONObject {
        val json = JSONObject().put(KEY_COMMAND_ID, command.commandId)
        when (command) {
            is RemoteCommand.StartPlayback -> json
                .put(KEY_COMMAND_TYPE, COMMAND_START)
                .put(KEY_SCRIPT_ID, command.scriptId)
            is RemoteCommand.PausePlayback -> json.put(KEY_COMMAND_TYPE, COMMAND_PAUSE)
            is RemoteCommand.ResumeImmediately -> json.put(KEY_COMMAND_TYPE, COMMAND_RESUME_NOW)
            is RemoteCommand.ResumeWithCountdown -> json.put(KEY_COMMAND_TYPE, COMMAND_RESUME_COUNTDOWN)
            is RemoteCommand.SeekBy -> json
                .put(KEY_COMMAND_TYPE, COMMAND_SEEK)
                .put(KEY_DELTA, command.delta.toDouble())
            is RemoteCommand.ChangeSpeed -> json
                .put(KEY_COMMAND_TYPE, COMMAND_SPEED)
                .put(KEY_DELTA, command.delta.toDouble())
            is RemoteCommand.EndPlayback -> json.put(KEY_COMMAND_TYPE, COMMAND_END)
        }
        return json
    }

    // ---- decoders ----

    private fun decodeClientHello(json: JSONObject): Result<RemoteMessage> {
        val version = json.optInt(KEY_PROTOCOL_VERSION, -1).takeIf { it >= 0 }
            ?: return Result.failure(RemoteDecodeError.Malformed("bad protocolVersion"))
        val session = boundedString(json, KEY_SESSION_ID, 64)
            ?: return Result.failure(RemoteDecodeError.Malformed("bad sessionId"))
        val token = boundedString(json, KEY_PAIRING_TOKEN, 128)
            ?: return Result.failure(RemoteDecodeError.Malformed("bad pairingToken"))
        val device = decodeDevice(json.optJSONObject(KEY_DEVICE))
            ?: return Result.failure(RemoteDecodeError.Malformed("bad device"))
        return Result.success(
            RemoteMessage.ClientHello(version, session, token, device),
        )
    }

    private fun decodeServerAccepted(json: JSONObject): Result<RemoteMessage> {
        val connectionId = boundedString(json, KEY_CONNECTION_ID, 64)
            ?: return Result.failure(RemoteDecodeError.Malformed("bad connectionId"))
        val prompterDevice = decodeDevice(json.optJSONObject(KEY_PROMPTER_DEVICE))
            ?: return Result.failure(RemoteDecodeError.Malformed("bad prompterDevice"))
        val resumeToken = boundedString(json, KEY_RESUME_TOKEN, 128)
            ?: return Result.failure(RemoteDecodeError.Malformed("bad resumeToken"))
        val initial = json.optJSONObject(KEY_INITIAL_SNAPSHOT)?.let(::decodeSnapshot)
            ?.getOrNull()
        return Result.success(
            RemoteMessage.ServerAccepted(connectionId, prompterDevice, resumeToken, initial),
        )
    }

    private fun decodeServerRejected(json: JSONObject): Result<RemoteMessage> =
        Result.success(
            RemoteMessage.ServerRejected(
                reason = enumOf(RemoteRejectReason::class.java, json.optString(KEY_REASON)) ?: RemoteRejectReason.Malformed,
                message = optionalString(json, KEY_MESSAGE),
            ),
        )

    private fun decodeCommandRequest(json: JSONObject): Result<RemoteMessage> {
        val command = decodeCommand(json.optJSONObject(KEY_COMMAND))
            ?: return Result.failure(RemoteDecodeError.Malformed("bad command"))
        return Result.success(RemoteMessage.CommandRequest(command))
    }

    private fun decodeCommandResult(json: JSONObject): Result<RemoteMessage> {
        val commandId = optionalString(json, KEY_COMMAND_ID)
        val success = json.optBoolean(KEY_SUCCESS, false)
        val errorReason = enumOf(RemoteRejectReason::class.java, json.optString(KEY_ERROR_REASON))
        val errorMessage = optionalString(json, KEY_MESSAGE)
        val revision = json.optLong(KEY_RESULTING_REVISION, -1L).takeIf { it >= 0 }
        return Result.success(
            RemoteMessage.CommandResult(commandId, success, errorReason, errorMessage, revision),
        )
    }

    private fun decodeSnapshotUpdate(json: JSONObject): Result<RemoteMessage> {
        val snapshotJson = json.optJSONObject(KEY_SNAPSHOT)
            ?: return Result.failure(RemoteDecodeError.Malformed("missing snapshot"))
        return decodeSnapshot(snapshotJson).map { RemoteMessage.SnapshotUpdate(it) }
    }

    private fun decodeHeartbeat(json: JSONObject, pong: Boolean): Result<RemoteMessage> {
        val sequence = json.optLong(KEY_SEQUENCE, -1L)
        if (sequence < 0L) return Result.failure(RemoteDecodeError.Malformed("bad sequence"))
        return Result.success(
            if (pong) RemoteMessage.HeartbeatPong(sequence) else RemoteMessage.HeartbeatPing(sequence),
        )
    }

    private fun decodeDisconnectNotice(json: JSONObject): Result<RemoteMessage> =
        Result.success(
            RemoteMessage.DisconnectNotice(
                reason = enumOf(RemoteRejectReason::class.java, json.optString(KEY_REASON)),
                message = optionalString(json, KEY_MESSAGE),
            ),
        )

    private fun decodeProtocolError(json: JSONObject): Result<RemoteMessage> =
        Result.success(
            RemoteMessage.ProtocolError(
                code = enumOf(RemoteProtocolErrorCode::class.java, json.optString(KEY_CODE)) ?: RemoteProtocolErrorCode.MalformedMessage,
                message = optionalString(json, KEY_MESSAGE),
            ),
        )

    private fun decodeSnapshot(json: JSONObject): Result<RemotePrompterSnapshot> {
        val revision = json.optLong(KEY_REVISION, -1L)
        if (revision < 0L) return Result.failure(RemoteDecodeError.Malformed("bad revision"))
        val surfaceName = json.optString(KEY_SURFACE)
        val surface = enumOf(RemotePrompterSurface::class.java, surfaceName)
            ?: return Result.failure(RemoteDecodeError.Malformed("bad surface"))

        val progress = json.optDouble(KEY_PROGRESS, Double.NaN)
        if (!progress.isFinite() || progress < 0.0 || progress > 1.0) {
            return Result.failure(RemoteDecodeError.Malformed("bad progress"))
        }
        val speed = json.optDouble(KEY_SPEED, Double.NaN)
        if (!speed.isFinite() || speed < 0.0 || speed > 10.0) {
            return Result.failure(RemoteDecodeError.Malformed("bad speed"))
        }
        val elapsed = json.optLong(KEY_ELAPSED, -1L)
        val remaining = json.optLong(KEY_REMAINING, -1L)
        if (elapsed < 0L || remaining < 0L) return Result.failure(RemoteDecodeError.Malformed("bad duration"))

        val playback = json.optJSONObject(KEY_PLAYBACK_STATE)
        val playbackState = RemotePlaybackState(
            isPlaying = playback?.optBoolean(KEY_IS_PLAYING, false) ?: false,
            isPaused = playback?.optBoolean(KEY_IS_PAUSED, false) ?: false,
            isCountdown = playback?.optBoolean(KEY_IS_COUNTDOWN, false) ?: false,
            isFinished = playback?.optBoolean(KEY_IS_FINISHED, false) ?: false,
            countdownSecondsRemaining = playback?.optLong(KEY_COUNTDOWN_REMAINING, -1L)
                ?.takeIf { it >= 0 }?.toInt(),
        )

        val estimated = json.optLong(KEY_ESTIMATED_DURATION, -1L).takeIf { it >= 0 }?.toInt()
        val countdown = json.optLong(KEY_COUNTDOWN_REMAINING, -1L).takeIf { it >= 0 }?.toInt()

        return Result.success(
            RemotePrompterSnapshot(
                revision = revision,
                surface = surface,
                scriptId = optionalString(json, KEY_SCRIPT_ID),
                scriptTitle = optionalString(json, KEY_SCRIPT_TITLE),
                estimatedDurationSeconds = estimated,
                playbackState = playbackState,
                progress = progress.toFloat(),
                elapsedTimeMillis = elapsed,
                remainingTimeMillis = remaining,
                speedMultiplier = speed.toFloat(),
                countdownSecondsRemaining = countdown,
                nearbyText = optionalString(json, KEY_NEARBY_TEXT)?.take(MAX_NEARBY_TEXT_LENGTH),
            ).normalized(),
        )
    }

    private fun decodeCommand(json: JSONObject?): RemoteCommand? {
        if (json == null) return null
        val commandId = boundedString(json, KEY_COMMAND_ID, 64) ?: return null
        return when (json.optString(KEY_COMMAND_TYPE)) {
            COMMAND_START -> {
                val scriptId = boundedString(json, KEY_SCRIPT_ID, 128) ?: return null
                RemoteCommand.StartPlayback(commandId, scriptId)
            }
            COMMAND_PAUSE -> RemoteCommand.PausePlayback(commandId)
            COMMAND_RESUME_NOW -> RemoteCommand.ResumeImmediately(commandId)
            COMMAND_RESUME_COUNTDOWN -> RemoteCommand.ResumeWithCountdown(commandId)
            COMMAND_SEEK -> {
                val delta = json.optDouble(KEY_DELTA, Double.NaN)
                if (!delta.isFinite()) return null
                RemoteCommand.SeekBy(commandId, delta.toFloat())
            }
            COMMAND_SPEED -> {
                val delta = json.optDouble(KEY_DELTA, Double.NaN)
                if (!delta.isFinite()) return null
                RemoteCommand.ChangeSpeed(commandId, delta.toFloat())
            }
            COMMAND_END -> RemoteCommand.EndPlayback(commandId)
            else -> null
        }
    }

    private fun decodeDevice(json: JSONObject?): RemoteDeviceInfo? {
        if (json == null) return null
        val deviceId = boundedString(json, KEY_DEVICE_ID, 64) ?: return null
        val displayName = boundedString(json, KEY_DISPLAY_NAME, 64) ?: return null
        val role = enumOf(RemoteRole::class.java, json.optString(KEY_ROLE)) ?: return null
        return RemoteDeviceInfo(deviceId, displayName, role)
    }

    private fun boundedString(json: JSONObject, key: String, max: Int): String? {
        val value = json.optString(key)
        if (value.isEmpty()) return null
        if (value.length > max) return null
        return value
    }

    private fun optionalString(json: JSONObject, key: String): String? {
        val value = json.optString(key)
        return if (value.isEmpty()) null else value.take(MAX_STRING_LENGTH)
    }

    private fun <T : Enum<T>> enumOf(clazz: Class<T>, name: String): T? {
        if (name.isEmpty()) return null
        val constants = clazz.enumConstants ?: return null
        return constants.firstOrNull { it.name == name }
    }
}

/** Structured decode failures returned by [RemoteJsonCodec.decode]. */
sealed class RemoteDecodeError(message: String? = null) : Throwable(message) {
    data class Malformed(val detail: String) : RemoteDecodeError(detail)
    data class UnknownType(val type: String) : RemoteDecodeError(type)
}
