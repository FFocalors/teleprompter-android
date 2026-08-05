package com.zhy20.teleprompter.remote.transport

import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemotePrompterSurface
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.model.remotePrompterSnapshot
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.protocol.RemoteJsonCodec
import com.zhy20.teleprompter.remote.protocol.RemoteMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real localhost WebSocket integration tests: a true prompter server transport and a true
 * controller client transport exchange protocol messages over an actual socket (no fake).
 */
class WebSocketRemoteTransportIntegrationTest {

    @Test
    fun realSocketHandshakeCommandSnapshotAndDisconnect() = runRealTest {
        // Bind to port 0 so the OS assigns a free port (avoids cross-test collisions).
        val prompterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = WebSocketRemoteTransport(
            role = WebSocketRemoteTransport.Role.Prompter,
            bindPort = 0,
            scope = prompterScope,
        )
        server.start()

        // Wait for the server to bind its port.
        val port = withTimeout(5_000) {
            var candidate: Int? = null
            while (candidate == null) {
                candidate = server.boundPort.value
                if (candidate == null) kotlinx.coroutines.delay(50)
            }
            candidate
        }
        assertTrue(port > 0)

        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = WebSocketRemoteTransport(
            role = WebSocketRemoteTransport.Role.Controller,
            scope = clientScope,
        )
        client.start()

        val serverConnected = CompletableDeferred<Unit>()
        val serverMessages = mutableListOf<RemoteMessage>()
        val serverJob = launch { server.incomingMessages.collect { serverMessages.add(it) } }
        val serverEventsJob = launch { server.connectionEvents.collect { if (it is RemoteTransportEvent.Connected) serverConnected.complete(Unit) } }
        val clientConnected = CompletableDeferred<Unit>()
        val clientMessages = mutableListOf<RemoteMessage>()
        val clientJob = launch { client.incomingMessages.collect { clientMessages.add(it) } }
        val clientEventsJob = launch { client.connectionEvents.collect { if (it is RemoteTransportEvent.Connected) clientConnected.complete(Unit) } }
        // Let the collectors subscribe before the async connect emits its Connected event,
        // otherwise the no-replay SharedFlow drops it.
        delay(100)

        client.connect("127.0.0.1", port)
        withTimeout(5_000) { serverConnected.await() }
        // The client socket must be OPEN before send() (Java-WebSocket throws otherwise).
        withTimeout(5_000) { clientConnected.await() }

        // Handshake: the controller transport sends a ClientHello; the server receives it.
        client.send(
            RemoteMessage.ClientHello(
                protocolVersion = 2,
                sessionId = "s1",
                pairingToken = "t".repeat(32),
                device = RemoteDeviceInfo("d", "手机", RemoteRole.Controller),
            ),
        )
        withTimeout(5_000) {
            while (serverMessages.none { it is RemoteMessage.ClientHello }) {
                kotlinx.coroutines.delay(20)
            }
        }
        val hello = serverMessages.filterIsInstance<RemoteMessage.ClientHello>().first()
        server.send(
            RemoteMessage.ServerAccepted(
                connectionId = "conn-1",
                prompterDevice = RemoteDeviceInfo("p", "tablet", RemoteRole.Prompter),
                resumeToken = "resume-1",
                initialSnapshot = null,
            ),
        )
        withTimeout(5_000) {
            while (clientMessages.none { it is RemoteMessage.ServerAccepted }) {
                kotlinx.coroutines.delay(20)
            }
        }
        assertEquals("conn-1", (clientMessages.filterIsInstance<RemoteMessage.ServerAccepted>().first()).connectionId)

        // Command round trip: client sends a command, server replies with a result.
        client.send(RemoteMessage.CommandRequest(RemoteCommand.PausePlayback("cmd-1")))
        withTimeout(5_000) {
            while (serverMessages.none { it is RemoteMessage.CommandRequest }) {
                kotlinx.coroutines.delay(20)
            }
        }
        val received = serverMessages.filterIsInstance<RemoteMessage.CommandRequest>().first()
        assertEquals("cmd-1", received.command.commandId)

        val snapshot: RemotePrompterSnapshot = remotePrompterSnapshot(
            revision = 4,
            surface = RemotePrompterSurface.Playing,
            scriptId = "1",
            scriptTitle = "校长采访开场",
            progress = 0.5f,
            elapsedTimeMillis = 100_000L,
            remainingTimeMillis = 100_000L,
            speedMultiplier = 1f,
        )
        server.send(RemoteMessage.SnapshotUpdate(snapshot))
        withTimeout(5_000) {
            while (clientMessages.none { it is RemoteMessage.SnapshotUpdate }) {
                kotlinx.coroutines.delay(20)
            }
        }
        assertEquals(4L, (clientMessages.filterIsInstance<RemoteMessage.SnapshotUpdate>().last()).snapshot.revision)

        // Heartbeat ping/pong across the real socket.
        client.send(RemoteMessage.HeartbeatPing(7))
        withTimeout(5_000) {
            while (serverMessages.none { it is RemoteMessage.HeartbeatPing }) {
                kotlinx.coroutines.delay(20)
            }
        }

        // Clean disconnect.
        server.stop()
        client.stop()
        serverJob.cancel()
        serverEventsJob.cancel()
        clientJob.cancel()
        clientEventsJob.cancel()
    }

    @Test
    fun serverNotRunningFailsClientConnect() = runRealTest {
        // Grab a free port and close it so nothing is listening when the client connects.
        val port = java.net.ServerSocket(0).use { it.localPort }
        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = WebSocketRemoteTransport(role = WebSocketRemoteTransport.Role.Controller, scope = clientScope)
        client.start()
        val disconnected = CompletableDeferred<RemoteTransportEvent.Disconnected>()
        val job = launch { client.connectionEvents.collect { e -> if (e is RemoteTransportEvent.Disconnected) disconnected.complete(e) } }
        // Give the collector a moment to subscribe.
        delay(50)
        client.connect("127.0.0.1", port)
        val event = withTimeout(8_000) { disconnected.await() }
        assertTrue(event.reason != null)
        job.cancel()
    }

    @Test
    fun codecRoundTripOverTheWireIsStable() {
        val message = RemoteMessage.ClientHello(
            protocolVersion = 2,
            sessionId = "s1",
            pairingToken = "t".repeat(32),
            device = RemoteDeviceInfo("d", "手机", RemoteRole.Controller),
        )
        val json = RemoteJsonCodec.encode(message)
        val decoded = RemoteJsonCodec.decode(json).getOrThrow()
        assertEquals(message, decoded)
    }
}

/** Runs the block inside a runBlocking CoroutineScope so launch/withTimeout resolve. */
private fun runRealTest(block: suspend CoroutineScope.() -> Unit) {
    kotlinx.coroutines.runBlocking { block() }
}
