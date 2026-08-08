package com.zhy20.teleprompter.remote.transport

import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemoteFailureReason
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.protocol.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * In-memory transport used for this phase and unit tests. No network is involved: tests can
 * inject incoming messages, read the messages a client sent, and simulate connection,
 * disconnection and connection failures without any Android dependency.
 *
 * When [loopback] is enabled every message sent is echoed back as an incoming message, which
 * lets the same device play both the controller and prompter roles for the demo. When
 * [autoConnectDelayMillis] is positive, [start] schedules a fake controller connection after
 * that delay.
 */
class FakeRemoteTransport(
    private val autoConnectDelayMillis: Long = 0L,
    private val loopback: Boolean = false,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val controllerDevice: RemoteDeviceInfo = RemoteDeviceInfo(
        deviceId = "demo-controller",
        displayName = "shooting-phone",
        role = RemoteRole.Controller,
    ),
) : RemoteTransport {
    private val _connectionEvents = MutableSharedFlow<RemoteTransportEvent>(extraBufferCapacity = 16)
    private val _incomingMessages = MutableSharedFlow<RemoteMessage>(extraBufferCapacity = 64)
    private val _sentMessages = MutableStateFlow<List<RemoteMessage>>(emptyList())

    private var started = false

    override val connectionEvents: SharedFlow<RemoteTransportEvent> = _connectionEvents.asSharedFlow()
    override val incomingMessages: SharedFlow<RemoteMessage> = _incomingMessages.asSharedFlow()

    /** The messages sent through [send] since the last read, in order. */
    val sentMessages: StateFlow<List<RemoteMessage>> = _sentMessages.asStateFlow()

    override suspend fun start() {
        started = true
        if (autoConnectDelayMillis > 0L) {
            scope.launch {
                delay(autoConnectDelayMillis)
                if (started) _connectionEvents.tryEmit(RemoteTransportEvent.Connected(controllerDevice))
            }
        }
    }

    override suspend fun stop() {
        started = false
    }

    override suspend fun send(message: RemoteMessage) {
        check(started) { "FakeRemoteTransport is not started" }
        _sentMessages.value = _sentMessages.value + message
        if (loopback) _incomingMessages.tryEmit(message)
    }

    /** Test/demo helper: emits a connection event. */
    fun simulateConnected(device: RemoteDeviceInfo = controllerDevice) {
        _connectionEvents.tryEmit(RemoteTransportEvent.Connected(device))
    }

    /** Test/demo helper: emits a disconnection event. */
    fun simulateDisconnected(reason: RemoteFailureReason? = null) {
        _connectionEvents.tryEmit(RemoteTransportEvent.Disconnected(reason))
    }

    /** Test/demo helper: pushes an incoming message as if a peer sent it. */
    fun injectMessage(message: RemoteMessage) {
        _incomingMessages.tryEmit(message)
    }
}
