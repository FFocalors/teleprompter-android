package com.zhy20.teleprompter.remote.session

import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemoteFailureReason
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemoteReadingCursor
import com.zhy20.teleprompter.remote.model.RemoteReadingWindow
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.model.RemoteSessionState
import com.zhy20.teleprompter.remote.pairing.PAIRING_VALIDITY_MILLIS
import com.zhy20.teleprompter.remote.pairing.RemoteCredentialGenerator
import com.zhy20.teleprompter.remote.pairing.RemotePairingPayload
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.protocol.RemoteMessage
import com.zhy20.teleprompter.remote.protocol.RemoteProtocol
import com.zhy20.teleprompter.remote.protocol.RemoteRejectReason
import com.zhy20.teleprompter.remote.protocol.validationError
import com.zhy20.teleprompter.remote.transport.RemoteTransport
import com.zhy20.teleprompter.remote.transport.RemoteTransportEvent
import com.zhy20.teleprompter.remote.transport.WebSocketRemoteTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Default [RemoteSessionRepository] implementing both roles.
 *
 * Prompter role:
 *  - validates ClientHello (version, session, token, expiry, single-controller, device);
 *  - emits ServerAccepted with a fresh resume token, then a full snapshot;
 *  - validates commands against the current playback state, deduplicates by commandId and
 *    returns a CommandResult with the resulting snapshot revision.
 *
 * Controller role:
 *  - connects via the transport, sends ClientHello, waits for ServerAccepted;
 *  - sends commands and consumes results;
 *  - runs a bounded auto-reconnect loop using resume credentials after a drop.
 *
 * Heartbeats run at the application layer here (5s interval, 15s idle timeout) and are
 * cancelled together with the connection lifecycle.
 */
class DefaultRemoteSessionRepository(
    private val transport: RemoteTransport,
    private val scope: CoroutineScope,
    override val device: RemoteDeviceInfo = RemoteDeviceInfo(
        deviceId = "prompter-local",
        displayName = "Teleprompter",
        role = RemoteRole.Prompter,
    ),
    private val protocolVersion: Int = RemoteProtocol.VERSION,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val nanoTime: () -> Long = System::nanoTime,
    /**
     * Monotonic elapsed-realtime source for the reading-cursor timestamp. Defaults to a JVM-safe
     * monotonic millisecond clock so unit tests do not need [android.os.SystemClock].
     */
    private val nowRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    /**
     * Resolves the LAN IPv4 this device should advertise in the pairing QR (prompter role).
     * Injected so the domain layer never touches Android network APIs.
     */
    private val lanAddressProvider: () -> String? = { null },
) : RemoteSessionRepository, RemoteDeviceInfoHolder {

    override val localDevice: RemoteDeviceInfoHolder get() = this

    private val _sessionState = MutableStateFlow(RemoteSessionState())
    override val sessionState: StateFlow<RemoteSessionState> = _sessionState.asStateFlow()

    private val _snapshot = MutableStateFlow<RemotePrompterSnapshot?>(null)
    override val snapshot: StateFlow<RemotePrompterSnapshot?> = _snapshot.asStateFlow()

    /** Controller-side reading sync state (latest window + latest cursor). */
    private val _readingWindow = MutableStateFlow<RemoteReadingWindow?>(null)
    override val readingWindow: StateFlow<RemoteReadingWindow?> = _readingWindow.asStateFlow()

    private val _readingCursor = MutableStateFlow<RemoteReadingCursor?>(null)
    override val readingCursor: StateFlow<RemoteReadingCursor?> = _readingCursor.asStateFlow()

    /**
     * Highest cursor sequence accepted so far (controller role). Stale/out-of-order cursor
     * messages (sequence <= this) are dropped — the cursor channel is latest-only.
     */
    private var lastCursorSequence = Long.MIN_VALUE

    /** Dedup + cursor gate state for the prompter's outgoing reading channel. */
    private var lastSentWindowRevision: Long? = null
    private var lastSentCursorOffset: Double? = null
    private var cursorSequence = 0L
    private var lastCursorSendNanos = 0L

    private val _incomingCommands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 32)
    override val incomingCommands: Flow<RemoteCommand> = _incomingCommands.asSharedFlow()

    private val _sessionEffects = MutableSharedFlow<RemoteSessionEffect>(extraBufferCapacity = 32)
    override val sessionEffects: Flow<RemoteSessionEffect> = _sessionEffects.asSharedFlow()

    private val commandResults = LinkedHashMap<String, RemoteCommandResultState>()

    /** Pairing credentials for the active prompter session (prompter role). */
    private var prompterCredentials: SessionCredentials? = null

    /** Credentials captured from the scanned QR (controller role). */
    private var controllerCredentials: SessionCredentials? = null

    /** Resume credential after a successful handshake (controller role). */
    private var resumeCredentials: ResumeCredentials? = null

    /** Target the controller last connected to, used for reconnects. */
    private var controllerTarget: Pair<String, Int>? = null

    private var active = false
    private var role: RemoteRole? = null

    /**
     * Set when the user explicitly disconnects (either side). While true, transport
     * disconnects never trigger auto-reconnect, and stale callbacks cannot change the
     * session. Reset on the next fresh connect/start.
     */
    private var userInitiatedDisconnect = false

    /**
     * Monotonic session generation. Any transport event/message tagged with an older
     * generation is ignored so a closed connection cannot pollute a newer session.
     */
    private var sessionGeneration = 0L

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastActivityNanos = System.nanoTime()

    private val seenCommandIds = LinkedHashMap<String, RemoteCommandResultState>()

    init {
        transport.connectionEvents
            .onEach(::handleTransportEvent)
            .launchIn(scope)
        transport.incomingMessages
            .onEach(::handleIncomingMessage)
            .launchIn(scope)
    }

    // ---- role selection ----

    override suspend fun prepare(role: RemoteRole) {
        this.role = role
        userInitiatedDisconnect = false
        (transport as? WebSocketRemoteTransport)?.setRole(
            if (role == RemoteRole.Prompter) WebSocketRemoteTransport.Role.Prompter
            else WebSocketRemoteTransport.Role.Controller,
        )
        _sessionState.value = _sessionState.value.copy(
            role = role,
            status = RemoteConnectionStatus.Ready,
            pairingPayload = null,
            reconnecting = false,
        )
    }

    // ---- prompter ----

    override suspend fun startWaiting() {
        if (role != RemoteRole.Prompter) {
            _sessionState.value = _sessionState.value.copy(status = RemoteConnectionStatus.Failed(RemoteFailureReason.TransportUnavailable))
            return
        }
        // Re-entrant: regenerating a QR (or restarting after a drop) must invalidate the old
        // session and stop any previous server before creating a fresh one.
        active = false
        userInitiatedDisconnect = false
        stopHeartbeat()
        reconnectJob?.cancel()
        reconnectJob = null
        prompterCredentials = null
        _snapshot.value = null
        resetReadingState()
        transport.stop()
        sessionGeneration += 1

        active = true
        val sessionId = RemoteCredentialGenerator.newSessionId()
        val token = RemoteCredentialGenerator.newPairingToken()
        val expires = nowMillis() + PAIRING_VALIDITY_MILLIS
        prompterCredentials = SessionCredentials(sessionId, token, expires)

        _sessionState.value = _sessionState.value.copy(
            status = RemoteConnectionStatus.WaitingForController,
            pairingPayload = null,
            reconnecting = false,
        )
        transport.start()

        val port = transportBoundPort() ?: 8765
        val host = lanAddressProvider() ?: run {
            _sessionState.value = _sessionState.value.copy(
                status = RemoteConnectionStatus.Failed(RemoteFailureReason.NoNetworkAddress),
            )
            transport.stop()
            return
        }
        val payload = RemotePairingPayload(
            protocolVersion = protocolVersion,
            host = host,
            port = port,
            sessionId = sessionId,
            pairingToken = token,
            expiresAtEpochMillis = expires,
        )
        _sessionState.value = _sessionState.value.copy(pairingPayload = payload)
    }

    override suspend fun stopWaiting() {
        active = false
        prompterCredentials = null
        stopHeartbeat()
        transport.stop()
        _sessionState.value = RemoteSessionState(
            status = RemoteConnectionStatus.Disabled,
            role = role,
        )
        _snapshot.value = null
        resetReadingState()
        resetCommandHistory()
    }

    // ---- controller ----

    override suspend fun connectToPrompter(payload: RemotePairingPayload) {
        if (role != RemoteRole.Controller) return
        // A fresh scan always resets the user-initiated flag and stale reconnect state.
        userInitiatedDisconnect = false
        resumeCredentials = null
        reconnectJob?.cancel()
        reconnectJob = null
        sessionGeneration += 1
        controllerCredentials = SessionCredentials(
            sessionId = payload.sessionId,
            pairingToken = payload.pairingToken,
            expiresAtEpochMillis = payload.expiresAtEpochMillis,
        )
        controllerTarget = payload.host to payload.port
        active = true
        _sessionState.value = _sessionState.value.copy(
            status = RemoteConnectionStatus.Connecting,
            reconnecting = false,
            lastCommandError = null,
        )
        transport.start()
        connectTransport(payload.host, payload.port)
    }

    override suspend fun connectManual(host: String, port: Int, sessionId: String, token: String) {
        connectToPrompter(
            RemotePairingPayload(
                protocolVersion = protocolVersion,
                host = host,
                port = port,
                sessionId = sessionId,
                pairingToken = token,
                expiresAtEpochMillis = nowMillis() + PAIRING_VALIDITY_MILLIS,
            ),
        )
    }

    private fun connectTransport(host: String, port: Int) {
        (transport as? WebSocketRemoteTransport)?.connect(host, port)
    }

    override suspend fun disconnect() {
        active = false
        userInitiatedDisconnect = true
        stopHeartbeat()
        reconnectJob?.cancel()
        reconnectJob = null
        resumeCredentials = null
        controllerCredentials = null
        prompterCredentials = null
        controllerTarget = null
        transport.stop()
        _sessionState.value = RemoteSessionState(
            status = RemoteConnectionStatus.Disabled,
            role = role,
        )
        _snapshot.value = null
        resetReadingState()
        resetCommandHistory()
    }

    override suspend fun disconnectController() {
        if (role != RemoteRole.Prompter) return
        userInitiatedDisconnect = true
        stopHeartbeat()
        reconnectJob?.cancel()
        reconnectJob = null
        // Notify the controller, then close its connection. A failed send must not block the
        // local close.
        runCatching { transport.send(RemoteMessage.DisconnectNotice()) }
        runCatching { transport.stop() }
        sessionGeneration += 1
        prompterCredentials = null
        controllerTarget = null
        _snapshot.value = null
        resetReadingState()
        resetCommandHistory()
        // The server keeps running: rotate to a fresh session/token and a new QR.
        startWaiting()
    }

    override suspend fun disconnectFromPrompter() {
        if (role != RemoteRole.Controller) return
        userInitiatedDisconnect = true
        stopHeartbeat()
        reconnectJob?.cancel()
        reconnectJob = null
        runCatching { transport.send(RemoteMessage.DisconnectNotice()) }
        runCatching { transport.stop() }
        sessionGeneration += 1
        resumeCredentials = null
        controllerCredentials = null
        controllerTarget = null
        _snapshot.value = null
        resetReadingState()
        resetCommandHistory()
        _sessionState.value = RemoteSessionState(
            status = RemoteConnectionStatus.Disabled,
            role = role,
        )
    }

    override suspend fun stopHosting() {
        if (role != RemoteRole.Prompter) return
        userInitiatedDisconnect = true
        active = false
        stopHeartbeat()
        reconnectJob?.cancel()
        reconnectJob = null
        runCatching { transport.send(RemoteMessage.DisconnectNotice()) }
        runCatching { transport.stop() }
        sessionGeneration += 1
        prompterCredentials = null
        controllerTarget = null
        _snapshot.value = null
        resetReadingState()
        resetCommandHistory()
        _sessionState.value = RemoteSessionState(
            status = RemoteConnectionStatus.Disabled,
            role = role,
        )
    }

    override suspend fun resetRole() {
        active = false
        userInitiatedDisconnect = true
        stopHeartbeat()
        reconnectJob?.cancel()
        reconnectJob = null
        resumeCredentials = null
        controllerCredentials = null
        prompterCredentials = null
        controllerTarget = null
        role = null
        (transport as? WebSocketRemoteTransport)?.setRole(WebSocketRemoteTransport.Role.Prompter)
        transport.stop()
        sessionGeneration += 1
        _sessionState.value = RemoteSessionState(
            status = RemoteConnectionStatus.Disabled,
            role = null,
        )
        _snapshot.value = null
        resetReadingState()
        resetCommandHistory()
    }

    override suspend fun sendCommand(command: RemoteCommand) {
        val state = sessionState.value
        if (state.status !is RemoteConnectionStatus.Connected) return
        val validation = command.validationError()
        if (validation != null) {
            _sessionState.value = state.copy(lastCommandError = validation)
            return
        }
        _sessionState.value = state.copy(commandInFlight = true, lastCommandError = null)
        transport.send(RemoteMessage.CommandRequest(command))
        _sessionState.value = state.copy(commandInFlight = false)
    }

    private var lastSnapshotSendNanos = 0L

    override fun updatePrompterSnapshot(snapshot: RemotePrompterSnapshot) {
        val normalized = snapshot.normalized()
        if (normalized.revision < (_snapshot.value?.revision ?: 0L)) return
        _snapshot.value = normalized
        val state = sessionState.value
        if (state.status !is RemoteConnectionStatus.Connected) return
        // Throttle high-frequency playback-frame snapshots to at most one per 250 ms, while
        // discrete state changes (pause/start/end/seek/speed) always send immediately.
        val now = nanoTime()
        val wasContinuous = normalized.playbackState.isPlaying
        val shouldThrottle = wasContinuous && (now - lastSnapshotSendNanos) < SNAPSHOT_THROTTLE_NANOS
        if (shouldThrottle) return
        lastSnapshotSendNanos = now
        scope.launch { transport.send(RemoteMessage.SnapshotUpdate(normalized)) }
    }

    /**
     * Sends a reading window. The window is low-frequency: identical [RemoteMessage.ReadingWindowUpdate.windowRevision]
     * values are never resent, so a fresh window is delivered once and the controller keeps it
     * as its local re-layout cache until the next window slides.
     */
    override fun updateReadingWindow(update: RemoteMessage.ReadingWindowUpdate) {
        val state = sessionState.value
        if (state.status !is RemoteConnectionStatus.Connected) return
        if (update.windowRevision == lastSentWindowRevision) return
        lastSentWindowRevision = update.windowRevision
        scope.launch { transport.send(update) }
    }

    /**
     * Sends the absolute reading cursor. The channel is **latest-only**: an identical cursor is
     * never resent, ordinary continuous motion is throttled to roughly 12–20 Hz, and a
     * seek-style jump (delta ≥ [CURSOR_JUMP_THRESHOLD]) bypasses the cadence gate so the
     * controller snaps to the new position immediately. Every sent cursor carries a fresh
     * monotonic [RemoteMessage.ReadingCursorUpdate.sequence].
     */
    override fun updateReadingCursor(update: RemoteMessage.ReadingCursorUpdate) {
        val state = sessionState.value
        if (state.status !is RemoteConnectionStatus.Connected) return
        val prev = lastSentCursorOffset
        if (prev != null && kotlin.math.abs(update.absoluteOffset - prev) < CURSOR_DEDUP_EPSILON) return
        val now = nanoTime()
        val isJump = prev == null || kotlin.math.abs(update.absoluteOffset - prev) >= CURSOR_JUMP_THRESHOLD
        if (!isJump && (now - lastCursorSendNanos) < CURSOR_INTERVAL_NANOS) return
        lastCursorSendNanos = now
        lastSentCursorOffset = update.absoluteOffset
        cursorSequence += 1
        val stamped = update.copy(sequence = cursorSequence, sentAtElapsedRealtimeMillis = nowRealtimeMillis())
        scope.launch { transport.send(stamped) }
    }

    /** Controller-side: drops stale reading state so a new session never shows an old text. */
    private fun resetReadingState() {
        _readingWindow.value = null
        _readingCursor.value = null
        lastCursorSequence = Long.MIN_VALUE
        lastSentWindowRevision = null
        lastSentCursorOffset = null
        lastCursorSendNanos = 0L
    }

    override fun resetCommandHistory() {
        seenCommandIds.clear()
    }

    override fun takeCommandResult(commandId: String): RemoteCommandResultState? =
        commandResults.remove(commandId)

    // ---- transport events ----

    private fun handleTransportEvent(event: RemoteTransportEvent) {
        if (userInitiatedDisconnect) return
        when (event) {
            is RemoteTransportEvent.Connected -> {
                when (role) {
                    RemoteRole.Prompter -> {
                        _sessionState.value = _sessionState.value.copy(
                            status = RemoteConnectionStatus.Connecting,
                        )
                    }
                    RemoteRole.Controller -> {
                        val creds = controllerCredentials ?: return
                        val resume = resumeCredentials
                        val hello = if (resume != null && resume.sessionId == creds.sessionId) {
                            RemoteMessage.ClientHello(
                                protocolVersion = protocolVersion,
                                sessionId = resume.sessionId,
                                pairingToken = resume.resumeToken,
                                device = device.copy(role = RemoteRole.Controller),
                            )
                        } else {
                            RemoteMessage.ClientHello(
                                protocolVersion = protocolVersion,
                                sessionId = creds.sessionId,
                                pairingToken = creds.pairingToken,
                                device = device.copy(role = RemoteRole.Controller),
                            )
                        }
                        scope.launch { transport.send(hello) }
                    }
                    null -> Unit
                }
            }
            is RemoteTransportEvent.Disconnected -> {
                handleDisconnected(event.reason)
            }
        }
    }

    private fun handleDisconnected(reason: RemoteFailureReason?) {
        val current = sessionState.value
        val wasConnected = current.status is RemoteConnectionStatus.Connected ||
            current.status is RemoteConnectionStatus.Connecting
        stopHeartbeat()
        if (!wasConnected) {
            // A failed transport connect (never opened) also lands here.
            if (role == RemoteRole.Controller && current.status is RemoteConnectionStatus.Connecting) {
                _sessionState.value = current.copy(
                    status = RemoteConnectionStatus.Failed(reason ?: RemoteFailureReason.HandshakeFailed),
                    reconnecting = false,
                )
            }
            return
        }
        when (role) {
            RemoteRole.Prompter -> {
                // An explicit disconnectController() already reset the session; ignore the
                // transport's own Disconnected callback from the old connection.
                if (userInitiatedDisconnect) return
                if (prompterCredentials != null) {
                    _sessionState.value = RemoteSessionState(
                        status = RemoteConnectionStatus.WaitingForController,
                        role = role,
                        pairingPayload = current.pairingPayload,
                        snapshot = current.snapshot,
                    )
                } else {
                    _sessionState.value = RemoteSessionState(
                        status = RemoteConnectionStatus.Disabled,
                        role = role,
                    )
                }
            }
            RemoteRole.Controller -> {
                if (userInitiatedDisconnect) {
                    // Explicit disconnect: no reconnect, clear everything.
                    resumeCredentials = null
                    controllerCredentials = null
                    controllerTarget = null
                    _snapshot.value = null
                    _sessionState.value = RemoteSessionState(
                        status = RemoteConnectionStatus.Disabled,
                        role = role,
                    )
                    return
                }
                if (resumeCredentials != null && active) {
                    startReconnect(reason)
                } else {
                    _sessionState.value = RemoteSessionState(
                        status = RemoteConnectionStatus.Failed(reason ?: RemoteFailureReason.HandshakeFailed),
                        role = role,
                        snapshot = current.snapshot,
                        reconnecting = false,
                    )
                }
            }
            null -> Unit
        }
    }

    private fun startReconnect(original: RemoteFailureReason?) {
        val target = controllerTarget
        if (target == null) {
            _sessionState.value = _sessionState.value.copy(
                status = RemoteConnectionStatus.Failed(original ?: RemoteFailureReason.HandshakeFailed),
                reconnecting = false,
            )
            return
        }
        _sessionState.value = _sessionState.value.copy(
            status = RemoteConnectionStatus.Reconnecting(device = null),
            reconnecting = true,
        )
        val backoff = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L)
        reconnectJob = scope.launch {
            val deadline = System.nanoTime() + 30_000L * 1_000_000L
            var attempt = 0
            while (active && !userInitiatedDisconnect && resumeCredentials != null && System.nanoTime() < deadline) {
                delay(backoff[attempt.coerceAtMost(backoff.lastIndex)])
                if (!active || userInitiatedDisconnect || resumeCredentials == null) return@launch
                connectTransport(target.first, target.second)
                attempt++
            }
            if (active && !userInitiatedDisconnect) {
                _sessionState.value = _sessionState.value.copy(
                    status = RemoteConnectionStatus.Failed(original ?: RemoteFailureReason.HandshakeFailed),
                    reconnecting = false,
                )
            }
            reconnectJob = null
        }
    }

    // ---- incoming messages ----

    private fun handleIncomingMessage(message: RemoteMessage) {
        // Stale callbacks from a closed connection must never affect the current session.
        if (userInitiatedDisconnect) return
        touchActivity()
        when (message) {
            is RemoteMessage.ClientHello -> handleClientHello(message)
            is RemoteMessage.ServerAccepted -> handleServerAccepted(message)
            is RemoteMessage.ServerRejected -> handleServerRejected(message)
            is RemoteMessage.CommandRequest -> handleCommandRequest(message)
            is RemoteMessage.CommandResult -> handleCommandResult(message)
            is RemoteMessage.SnapshotUpdate -> handleSnapshot(message.snapshot)
            is RemoteMessage.ReadingWindowUpdate -> handleReadingWindow(message)
            is RemoteMessage.ReadingCursorUpdate -> handleReadingCursor(message)
            is RemoteMessage.HeartbeatPing -> scope.launch { transport.send(RemoteMessage.HeartbeatPong(message.sequence)) }
            is RemoteMessage.HeartbeatPong -> Unit
            is RemoteMessage.DisconnectNotice -> handleDisconnected(RemoteFailureReason.HandshakeFailed)
            is RemoteMessage.ProtocolError -> handleDisconnected(RemoteFailureReason.ProtocolMismatch)
        }
    }

    private fun handleClientHello(hello: RemoteMessage.ClientHello) {
        if (role != RemoteRole.Prompter) return
        val creds = prompterCredentials
        val tokenValid = creds != null && (
            hello.pairingToken == creds.pairingToken ||
                (creds.resumeToken != null && hello.pairingToken == creds.resumeToken)
            )
        val reject = when {
            hello.protocolVersion != protocolVersion -> RemoteRejectReason.ProtocolMismatch
            creds == null || hello.sessionId != creds.sessionId -> RemoteRejectReason.SessionMismatch
            !tokenValid -> RemoteRejectReason.InvalidToken
            nowMillis() > creds.expiresAtEpochMillis -> RemoteRejectReason.TokenExpired
            sessionState.value.status is RemoteConnectionStatus.Connected -> RemoteRejectReason.AlreadyConnected
            else -> null
        }
        if (reject != null) {
            scope.launch { transport.send(RemoteMessage.ServerRejected(reject)) }
            return
        }

        val connectionId = RemoteCredentialGenerator.newConnectionId()
        val resumeToken = RemoteCredentialGenerator.newResumeToken()
        // Consume the pairing token: after a successful handshake only the issued resume
        // token may reconnect, so an old QR can never be reused.
        prompterCredentials = creds?.copy(
            connectionId = connectionId,
            resumeToken = resumeToken,
            pairingToken = "",
        )

        val accepted = RemoteMessage.ServerAccepted(
            connectionId = connectionId,
            prompterDevice = device,
            resumeToken = resumeToken,
            initialSnapshot = _snapshot.value,
        )
        // A (re)connected controller must receive the current reading window + cursor afresh;
        // clear the outgoing dedup so the coordinator's next push re-sends both.
        lastSentWindowRevision = null
        lastSentCursorOffset = null
        lastCursorSendNanos = 0L
        _sessionState.value = RemoteSessionState(
            status = RemoteConnectionStatus.Connected(hello.device),
            snapshot = _snapshot.value,
            role = RemoteRole.Prompter,
            pairingPayload = null,
        )
        scope.launch {
            transport.send(accepted)
            _snapshot.value?.let { transport.send(RemoteMessage.SnapshotUpdate(it)) }
        }
        startHeartbeat()
    }

    private fun handleServerAccepted(accepted: RemoteMessage.ServerAccepted) {
        if (role != RemoteRole.Controller) return
        resumeCredentials = ResumeCredentials(
            sessionId = controllerCredentials?.sessionId ?: "",
            resumeToken = accepted.resumeToken,
            connectionId = accepted.connectionId,
        )
        // A fresh (or re-established) connection must not reuse pre-drop reading state; the
        // prompter re-sends the current window + cursor right after the handshake.
        resetReadingState()
        accepted.initialSnapshot?.let { _snapshot.value = it.normalized() }
        _sessionState.value = RemoteSessionState(
            status = RemoteConnectionStatus.Connected(accepted.prompterDevice),
            snapshot = _snapshot.value,
            role = RemoteRole.Controller,
            reconnecting = false,
            lastCommandError = null,
        )
        startHeartbeat()
    }

    private fun handleServerRejected(rejected: RemoteMessage.ServerRejected) {
        if (role != RemoteRole.Controller) return
        val reason = rejected.reason.toFailureReason()
        _sessionState.value = RemoteSessionState(
            status = RemoteConnectionStatus.Failed(reason),
            role = role,
            snapshot = _snapshot.value,
            reconnecting = false,
            lastCommandError = rejected.message,
        )
        active = false
        stopHeartbeat()
    }

    private fun handleCommandRequest(request: RemoteMessage.CommandRequest) {
        if (role != RemoteRole.Prompter) return
        val command = request.command
        val state = sessionState.value
        if (state.status !is RemoteConnectionStatus.Connected) return

        val existing = seenCommandIds[command.commandId]
        if (existing != null) {
            scope.launch {
                transport.send(
                    RemoteMessage.CommandResult(
                        commandId = existing.commandId,
                        success = existing.success,
                        errorReason = existing.errorReason,
                        errorMessage = existing.errorMessage,
                        resultingSnapshotRevision = existing.resultingSnapshotRevision,
                    ),
                )
            }
            return
        }

        val validation = command.validationError()
        if (validation != null) {
            recordRejected(command.commandId, RemoteRejectReason.InvalidCommand, validation)
            return
        }

        _incomingCommands.tryEmit(command)
        _sessionEffects.tryEmit(RemoteSessionEffect.ExecuteCommand(command))
    }

    private fun handleCommandResult(result: RemoteMessage.CommandResult) {
        if (role != RemoteRole.Controller) return
        val commandId = result.commandId ?: return
        commandResults[commandId] = RemoteCommandResultState(
            commandId = commandId,
            success = result.success,
            errorReason = result.errorReason,
            errorMessage = result.errorMessage,
            resultingSnapshotRevision = result.resultingSnapshotRevision,
        )
        _sessionState.value = _sessionState.value.copy(
            commandInFlight = false,
            lastCommandError = if (result.success) null else result.errorMessage,
        )
    }

    private fun handleSnapshot(snapshot: RemotePrompterSnapshot) {
        val normalized = snapshot.normalized()
        val current = _snapshot.value
        if (current == null || normalized.revision > current.revision) {
            _snapshot.value = normalized
        }
    }

    private fun handleReadingWindow(update: RemoteMessage.ReadingWindowUpdate) {
        if (role != RemoteRole.Controller) return
        val window = RemoteReadingWindow(
            textRevision = update.textRevision,
            windowRevision = update.windowRevision,
            startOffset = update.startOffset,
            endOffset = update.endOffset,
            text = update.text,
        ).normalized() ?: return
        _readingWindow.value = window
        // A fresh window re-anchors the cursor: keep the cursor only if it shares the text.
        val cursor = _readingCursor.value
        if (cursor != null && cursor.textRevision != window.textRevision) {
            _readingCursor.value = null
        }
    }

    private fun handleReadingCursor(update: RemoteMessage.ReadingCursorUpdate) {
        if (role != RemoteRole.Controller) return
        // Latest-only: out-of-order or stale sequences are never applied.
        if (update.sequence <= lastCursorSequence) return
        lastCursorSequence = update.sequence
        // The cursor must share the window's text revision; otherwise wait for the matching
        // window so a stale position is never shown against new text.
        val currentWindow = _readingWindow.value
        if (currentWindow != null && update.textRevision != currentWindow.textRevision) {
            _readingCursor.value = null
            return
        }
        _readingCursor.value = RemoteReadingCursor(
            textRevision = update.textRevision,
            absoluteOffset = update.absoluteOffset,
            sequence = update.sequence,
            sentAtElapsedRealtimeMillis = update.sentAtElapsedRealtimeMillis,
        ).normalized()
    }

    // ---- command result recording ----

    override fun recordResult(commandId: String, result: RemoteCommandResultState) {
        seenCommandIds[commandId] = result
        if (seenCommandIds.size > MAX_COMMAND_CACHE) {
            val oldest = seenCommandIds.keys.firstOrNull()
            if (oldest != null) seenCommandIds.remove(oldest)
        }
        scope.launch {
            transport.send(
                RemoteMessage.CommandResult(
                    commandId = commandId,
                    success = result.success,
                    errorReason = result.errorReason,
                    errorMessage = result.errorMessage,
                    resultingSnapshotRevision = result.resultingSnapshotRevision,
                ),
            )
        }
    }

    private fun recordRejected(commandId: String, reason: RemoteRejectReason, message: String) {
        recordResult(
            commandId,
            RemoteCommandResultState(
                commandId = commandId,
                success = false,
                errorReason = reason,
                errorMessage = message,
            ),
        )
    }

    // ---- heartbeat ----

    private fun startHeartbeat() {
        stopHeartbeat()
        lastActivityNanos = System.nanoTime()
        heartbeatJob = scope.launch {
            while (active && sessionState.value.status is RemoteConnectionStatus.Connected) {
                delay(HEARTBEAT_INTERVAL_MILLIS)
                val idle = System.nanoTime() - lastActivityNanos
                if (idle > HEARTBEAT_TIMEOUT_MILLIS * 1_000_000L) {
                    handleDisconnected(RemoteFailureReason.HandshakeFailed)
                    break
                }
                transport.send(RemoteMessage.HeartbeatPing(0))
                lastActivityNanos = System.nanoTime()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun touchActivity() {
        lastActivityNanos = System.nanoTime()
    }

    // ---- helpers ----

    private fun transportBoundPort(): Int? =
        (transport as? WebSocketRemoteTransport)?.boundPort?.value

    private fun RemoteRejectReason.toFailureReason(): RemoteFailureReason = when (this) {
        RemoteRejectReason.ProtocolMismatch -> RemoteFailureReason.ProtocolMismatch
        RemoteRejectReason.TokenExpired,
        RemoteRejectReason.InvalidToken,
        RemoteRejectReason.SessionMismatch,
        RemoteRejectReason.InvalidDeviceInfo,
        -> RemoteFailureReason.InvalidPairing

        RemoteRejectReason.AlreadyConnected -> RemoteFailureReason.AlreadyConnected
        else -> RemoteFailureReason.Rejected
    }

    private data class ResumeCredentials(
        val sessionId: String,
        val resumeToken: String,
        val connectionId: String,
    )

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 5_000L
        private const val HEARTBEAT_TIMEOUT_MILLIS = 15_000L
        private const val MAX_COMMAND_CACHE = 256
        private const val SNAPSHOT_THROTTLE_NANOS = 250_000_000L // 250 ms
        private const val CURSOR_INTERVAL_NANOS = 60_000_000L // ~16 Hz (12–20 Hz target)
        private const val CURSOR_JUMP_THRESHOLD = 2.0
        private const val CURSOR_DEDUP_EPSILON = 1e-4
    }
}
