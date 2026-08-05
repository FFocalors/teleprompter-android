package com.zhy20.teleprompter.remote.session

import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemoteFailureReason
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.model.RemoteSessionState
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.protocol.RemoteMessage
import com.zhy20.teleprompter.remote.protocol.RemoteProtocol
import com.zhy20.teleprompter.remote.protocol.RemoteProtocolErrorCode
import com.zhy20.teleprompter.remote.protocol.validationError
import com.zhy20.teleprompter.remote.transport.RemoteTransport
import com.zhy20.teleprompter.remote.transport.RemoteTransportEvent
import kotlinx.coroutines.CoroutineScope
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
 * Default [RemoteSessionRepository] built on a [RemoteTransport].
 *
 * Responsibilities:
 *  - owns the transport lifecycle (start/stop/send);
 *  - maps transport events into the [RemoteConnectionStatus] state machine;
 *  - validates, deduplicates and surfaces incoming controller commands;
 *  - publishes prompter snapshots back through the transport;
 *  - emits [RemoteSessionEffect]s for the application coordinator (it never navigates).
 */
class DefaultRemoteSessionRepository(
    private val transport: RemoteTransport,
    private val scope: CoroutineScope,
    private val ownDevice: RemoteDeviceInfo = RemoteDeviceInfo(
        deviceId = "prompter-local",
        displayName = "Teleprompter",
        role = RemoteRole.Prompter,
    ),
    private val protocolVersion: Int = RemoteProtocol.VERSION,
) : RemoteSessionRepository {

    private val _sessionState = MutableStateFlow(RemoteSessionState())
    override val sessionState: StateFlow<RemoteSessionState> = _sessionState.asStateFlow()

    private val _snapshot = MutableStateFlow<RemotePrompterSnapshot?>(null)
    override val snapshot: StateFlow<RemotePrompterSnapshot?> = _snapshot.asStateFlow()

    private val _incomingCommands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 32)
    override val incomingCommands: Flow<RemoteCommand> = _incomingCommands.asSharedFlow()

    private val _sessionEffects = MutableSharedFlow<RemoteSessionEffect>(extraBufferCapacity = 32)
    override val sessionEffects: Flow<RemoteSessionEffect> = _sessionEffects.asSharedFlow()

    private val seenCommandIds = LinkedHashSet<String>()

    /** Becomes true while a session is being awaited/active; gates message processing. */
    private var active = false

    init {
        transport.connectionEvents
            .onEach(::handleTransportEvent)
            .launchIn(scope)
        transport.incomingMessages
            .onEach(::handleIncomingMessage)
            .launchIn(scope)
    }

    override suspend fun startWaiting() {
        active = true
        _sessionState.value = RemoteSessionState(status = RemoteConnectionStatus.WaitingForController)
        transport.start()
    }

    override suspend fun stopWaiting() {
        active = false
        transport.stop()
        _sessionState.value = RemoteSessionState(status = RemoteConnectionStatus.Disabled)
        _snapshot.value = null
        resetCommandHistory()
    }

    override suspend fun disconnect() {
        active = false
        transport.stop()
        _sessionState.value = RemoteSessionState(status = RemoteConnectionStatus.Disabled)
        _snapshot.value = null
        resetCommandHistory()
    }

    override suspend fun sendCommand(command: RemoteCommand) {
        val state = sessionState.value
        if (state.status !is RemoteConnectionStatus.Connected) return
        val validation = command.validationError()
        if (validation != null) return
        _sessionState.value = state.copy(commandInFlight = true)
        transport.send(RemoteMessage.Command(command))
        _sessionState.value = state.copy(commandInFlight = false)
    }

    override fun updatePrompterSnapshot(snapshot: RemotePrompterSnapshot) {
        val normalized = snapshot.normalized()
        if (normalized.revision < (_snapshot.value?.revision ?: 0L)) return
        _snapshot.value = normalized
        val state = sessionState.value
        if (state.status is RemoteConnectionStatus.Connected) {
            scope.launch { transport.send(RemoteMessage.Snapshot(normalized)) }
        }
    }

    override fun resetCommandHistory() {
        seenCommandIds.clear()
    }

    private fun handleTransportEvent(event: RemoteTransportEvent) {
        when (event) {
            is RemoteTransportEvent.Connected -> {
                _sessionState.value = RemoteSessionState(
                    status = RemoteConnectionStatus.Connected(event.device),
                    snapshot = _snapshot.value,
                )
            }
            is RemoteTransportEvent.Disconnected -> {
                val current = sessionState.value.status
                if (current is RemoteConnectionStatus.Connected) {
                    _sessionState.value = if (event.reason == null) {
                        RemoteSessionState(status = RemoteConnectionStatus.Disabled, snapshot = _snapshot.value)
                    } else {
                        RemoteSessionState(
                            status = RemoteConnectionStatus.Reconnecting(device = current.device),
                            snapshot = _snapshot.value,
                        )
                    }
                }
            }
        }
    }

    private fun handleIncomingMessage(message: RemoteMessage) {
        if (!active) return
        when (message) {
            is RemoteMessage.Hello -> {
                if (message.protocolVersion != protocolVersion) {
                    _sessionState.value = RemoteSessionState(
                        status = RemoteConnectionStatus.Failed(RemoteFailureReason.ProtocolMismatch),
                        snapshot = _snapshot.value,
                    )
                    scope.launch {
                        transport.send(
                            RemoteMessage.ProtocolError(
                                code = RemoteProtocolErrorCode.UnsupportedVersion,
                                message = "Unsupported protocol version: ${message.protocolVersion}",
                            ),
                        )
                    }
                    scope.launch { transport.stop() }
                }
            }
            is RemoteMessage.Command -> {
                val command = message.command
                if (command.commandId in seenCommandIds) return
                seenCommandIds.add(command.commandId)

                val validation = command.validationError()
                if (validation != null) {
                    scope.launch {
                        transport.send(
                            RemoteMessage.CommandResult(
                                commandId = command.commandId,
                                accepted = false,
                                errorMessage = validation,
                            ),
                        )
                    }
                    return
                }
                _incomingCommands.tryEmit(command)
                _sessionEffects.tryEmit(RemoteSessionEffect.ExecuteCommand(command))
            }
            is RemoteMessage.Snapshot -> _snapshot.value = message.snapshot.normalized()
            is RemoteMessage.CommandResult -> Unit // reserved for the prompter role acknowledgement
            is RemoteMessage.Heartbeat -> Unit // keep-alive; no action needed this phase
            is RemoteMessage.ProtocolError -> {
                _sessionState.value = RemoteSessionState(
                    status = RemoteConnectionStatus.Failed(RemoteFailureReason.ProtocolMismatch),
                    snapshot = _snapshot.value,
                )
            }
        }
    }
}
