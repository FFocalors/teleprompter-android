package com.zhy20.teleprompter.remote.session

import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.pairing.RemotePairingPayload
import com.zhy20.teleprompter.remote.protocol.RemoteMessage
import com.zhy20.teleprompter.remote.protocol.RemoteProtocol
import com.zhy20.teleprompter.remote.transport.WebSocketRemoteTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end reading-window slide over a REAL localhost WebSocket: a prompter repository pushes
 * several ReadingWindowUpdates (revisions 1,2,3, same textRevision) and the controller repository
 * must apply each one. This is the regression for "ReadingWindow only works at initialization" —
 * every subsequent window must arrive and update the controller's current window.
 */
class ReadingWindowEndToEndTest {

    private val prompterDevice = RemoteDeviceInfo("prompter-e2e", "tablet", RemoteRole.Prompter)
    private val controllerDevice = RemoteDeviceInfo("ctrl-e2e", "phone", RemoteRole.Controller)

    @Test
    fun slidingWindowsReachTheControllerOverARealSocket() = runBlockingWithTimeout {
        // --- prompter (real server) ---
        val prompterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prompterTransport = WebSocketRemoteTransport(bindPort = 0, scope = prompterScope)
        val prompter = DefaultRemoteSessionRepository(
            transport = prompterTransport,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            device = prompterDevice,
            lanAddressProvider = { "127.0.0.1" },
        )
        prompter.prepare(RemoteRole.Prompter)
        prompter.startWaiting()
        val port = withTimeout(5_000) {
            var candidate: Int? = null
            while (candidate == null) {
                candidate = prompterTransport.boundPort.value
                if (candidate == null) delay(50)
            }
            candidate
        }
        assertTrue(port > 0)
        val pairing = prompter.sessionState.value.pairingPayload!!

        // --- controller (real client) ---
        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val clientTransport = WebSocketRemoteTransport(scope = clientScope)
        val controller = DefaultRemoteSessionRepository(
            transport = clientTransport,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            device = controllerDevice,
        )
        controller.prepare(RemoteRole.Controller)
        controller.connectToPrompter(
            RemotePairingPayload(
                protocolVersion = RemoteProtocol.VERSION,
                host = "127.0.0.1",
                port = port,
                sessionId = pairing.sessionId,
                pairingToken = pairing.pairingToken,
                expiresAtEpochMillis = Long.MAX_VALUE,
            ),
        )

        // Wait for the full handshake to complete on both sides.
        withTimeout(10_000) {
            while (prompter.sessionState.value.status !is RemoteConnectionStatus.Connected ||
                controller.sessionState.value.status !is RemoteConnectionStatus.Connected
            ) {
                delay(50)
            }
        }

        // Window revision 1.
        prompter.updateReadingWindow(RemoteMessage.ReadingWindowUpdate(3, 1, 0, 100, "a".repeat(100)))
        withTimeout(5_000) {
            while (controller.readingWindow.value?.windowRevision != 1L) delay(20)
        }

        // Window revision 2 — the same textRevision (3), only the window slid forward.
        prompter.updateReadingWindow(RemoteMessage.ReadingWindowUpdate(3, 2, 200, 300, "b".repeat(100)))
        withTimeout(5_000) {
            while (controller.readingWindow.value?.windowRevision != 2L) delay(20)
        }

        // Window revision 3.
        prompter.updateReadingWindow(RemoteMessage.ReadingWindowUpdate(3, 3, 400, 500, "c".repeat(100)))
        withTimeout(5_000) {
            while (controller.readingWindow.value?.windowRevision != 3L) delay(20)
        }

        // The controller must be tracking the latest window, not stuck at revision 1.
        assertEquals(3L, controller.readingWindow.value?.windowRevision)
        assertEquals(400, controller.readingWindow.value?.startOffset)
        assertEquals(500, controller.readingWindow.value?.endOffset)
        assertEquals("c".repeat(100), controller.readingWindow.value?.text)

        prompter.stopHosting()
    }
}

/** Runs the block with a generous global timeout so a hang fails loudly. */
private fun runBlockingWithTimeout(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
    kotlinx.coroutines.runBlocking {
        withTimeout(30_000) { block() }
    }
}
