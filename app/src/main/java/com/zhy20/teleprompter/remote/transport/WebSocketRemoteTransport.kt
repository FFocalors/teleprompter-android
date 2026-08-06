package com.zhy20.teleprompter.remote.transport

import com.zhy20.teleprompter.remote.model.RemoteFailureReason
import com.zhy20.teleprompter.remote.protocol.RemoteJsonCodec
import com.zhy20.teleprompter.remote.protocol.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.java_websocket.WebSocket
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

/**
 * Real LAN transport backed by Java-WebSocket.
 *
 * As prompter: runs a [WebSocketServer] bound to `0.0.0.0` and accepts exactly one active
 * controller connection. As controller: opens a [org.java_websocket.client.WebSocketClient]
 * to a target host/port.
 *
 * The [Role] is *not* fixed at construction: the same device can be a prompter or a
 * controller depending on the session, so [setRole] is called by the repository whenever a
 * role is chosen and any prior server/client is torn down first. This class stays a *dumb
 * frame layer*: handshake validation, heartbeats, reconnect policy and command execution all
 * live in the repository.
 */
class WebSocketRemoteTransport(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val bindPort: Int = 8765,
) : RemoteTransport {

    enum class Role { Prompter, Controller }

    @Volatile
    private var role: Role = Role.Prompter

    private val _connectionEvents = MutableSharedFlow<RemoteTransportEvent>(extraBufferCapacity = 16)
    private val _incomingMessages = MutableSharedFlow<RemoteMessage>(extraBufferCapacity = 64)
    private val _boundPort = MutableStateFlow<Int?>(null)
    private var boundPortReady = kotlinx.coroutines.CompletableDeferred<Boolean>()

    override val connectionEvents: SharedFlow<RemoteTransportEvent> = _connectionEvents.asSharedFlow()
    override val incomingMessages: SharedFlow<RemoteMessage> = _incomingMessages.asSharedFlow()

    /** The port the server bound to (prompter role), or null. */
    val boundPort: StateFlow<Int?> = _boundPort.asStateFlow()

    private var server: WebSocketServer? = null
    private var controller: org.java_websocket.client.WebSocketClient? = null
    private var activeConnection: WebSocket? = null
    @Volatile
    private var started = false

    /** Switches the transport to [role], tearing down the previous server/client. */
    fun setRole(role: Role) {
        if (this.role == role) return
        this.role = role
        runCatching { server?.stop(1_000) }
        runCatching { controller?.close() }
        server = null
        controller = null
        activeConnection = null
        _boundPort.value = null
        boundPortReady = kotlinx.coroutines.CompletableDeferred()
    }

    override suspend fun start() {
        started = true
        when (role) {
            Role.Prompter -> startServer()
            Role.Controller -> Unit // connect(host, port) drives the client
        }
        if (role == Role.Prompter) {
            // Wait for the server thread to actually bind (or fail) so the repository can
            // read a valid port immediately after start().
            kotlinx.coroutines.withTimeoutOrNull(5_000) { boundPortReady.await() }
        }
    }

    override suspend fun stop() {
        started = false
        activeConnection = null
        runCatching { server?.stop(1_000) }
        runCatching { controller?.close() }
        server = null
        controller = null
    }

    /** Controller role: opens an async client to [host]:[port] with a 5s connect timeout. */
    fun connect(host: String, port: Int) {
        if (!started) return
        val uri = runCatching { java.net.URI("ws://$host:$port/") }.getOrNull() ?: return
        runCatching { controller?.close() }
        val client = object : org.java_websocket.client.WebSocketClient(uri, Draft_6455(), emptyMap(), 5_000) {
            override fun onOpen(handshake: org.java_websocket.handshake.ServerHandshake) {
                _connectionEvents.tryEmit(RemoteTransportEvent.Connected(null))
            }

            override fun onMessage(message: String) {
                decodeAndForward(message)
            }

            override fun onClose(code: Int, reason: String, remote: Boolean) {
                _connectionEvents.tryEmit(
                    RemoteTransportEvent.Disconnected(
                        if (started) RemoteFailureReason.HandshakeFailed else null,
                    ),
                )
            }

            override fun onError(ex: Exception) {
                _connectionEvents.tryEmit(RemoteTransportEvent.Disconnected(RemoteFailureReason.ConnectionTimeout))
            }
        }
        controller = client
        // Non-blocking connect: onOpen/onError/onClose drive the transport events, so a
        // caller coroutine is never blocked and event collectors can keep running.
        try {
            client.connect()
        } catch (e: Exception) {
            _connectionEvents.tryEmit(RemoteTransportEvent.Disconnected(RemoteFailureReason.ConnectionTimeout))
        }
    }

    override suspend fun send(message: RemoteMessage) {
        if (!started) return
        val payload = runCatching { RemoteJsonCodec.encode(message) }.getOrNull() ?: return
        when (role) {
            Role.Prompter -> {
                val conn = activeConnection
                if (conn != null && conn.isOpen) runCatching { conn.send(payload) }
            }
            Role.Controller -> {
                val client = controller
                if (client != null && client.isOpen) runCatching { client.send(payload) }
            }
        }
    }

    private fun startServer() {
        // Regenerating a QR calls start() again: stop the previous server so the port can
        // be re-bound cleanly.
        runCatching { server?.stop(1_000) }
        server = null
        activeConnection = null

        // Try the requested port, then a small fallback range if it is taken (spec: no
        // unbounded scan). Port 0 lets the OS pick a free port.
        val attempts = buildList {
            add(bindPort)
            if (bindPort != 0) addAll((bindPort + 1)..(bindPort + 5))
        }
        var lastError: Exception? = null
        for (attempt in attempts.distinct()) {
            val ws: WebSocketServer = object : WebSocketServer(InetSocketAddress(attempt), 1) {
                override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                    if (activeConnection != null && activeConnection !== conn) {
                        conn.close(4004, "Only one controller allowed")
                        return
                    }
                    activeConnection = conn
                    _connectionEvents.tryEmit(RemoteTransportEvent.Connected(null))
                }

                override fun onMessage(conn: WebSocket, message: String) {
                    decodeAndForward(message)
                }

                override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
                    if (activeConnection === conn) {
                        activeConnection = null
                        _connectionEvents.tryEmit(
                            RemoteTransportEvent.Disconnected(
                                if (started) RemoteFailureReason.HandshakeFailed else null,
                            ),
                        )
                    }
                }

                override fun onError(conn: WebSocket, ex: Exception) {
                    _connectionEvents.tryEmit(RemoteTransportEvent.Disconnected(RemoteFailureReason.TransportUnavailable))
                }

                override fun onStart() {
                    // The server thread has bound the socket here, so the real port is known.
                    _boundPort.value = port
                    boundPortReady.complete(true)
                }
            }
            ws.setConnectionLostTimeout(0) // application-layer heartbeat is managed by the repository
            server = ws
            try {
                ws.start()
                return
            } catch (e: Exception) {
                lastError = e
                server = null
                _boundPort.value = null
                if (!boundPortReady.isCompleted) boundPortReady.complete(false)
            }
        }
        if (!boundPortReady.isCompleted) boundPortReady.complete(false)
        _connectionEvents.tryEmit(RemoteTransportEvent.Disconnected(RemoteFailureReason.PortUnavailable))
    }

    private fun decodeAndForward(raw: String) {
        val result = RemoteJsonCodec.decode(raw)
        result.fold(
            onSuccess = { _incomingMessages.tryEmit(it) },
            onFailure = {
                _incomingMessages.tryEmit(
                    RemoteMessage.ProtocolError(
                        code = com.zhy20.teleprompter.remote.protocol.RemoteProtocolErrorCode.MalformedMessage,
                        message = "Malformed message",
                    ),
                )
            },
        )
    }
}
