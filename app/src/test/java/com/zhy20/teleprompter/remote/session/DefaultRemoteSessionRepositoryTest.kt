package com.zhy20.teleprompter.remote.session

import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemoteFailureReason
import com.zhy20.teleprompter.remote.model.RemotePrompterSurface
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.model.remotePrompterSnapshot
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.protocol.RemoteMessage
import com.zhy20.teleprompter.remote.protocol.RemoteProtocol
import com.zhy20.teleprompter.remote.protocol.RemoteRejectReason
import com.zhy20.teleprompter.remote.transport.FakeRemoteTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRemoteSessionRepositoryTest {

    private val prompterDevice = RemoteDeviceInfo("prompter-1", "tablet", RemoteRole.Prompter)
    private val controllerDevice = RemoteDeviceInfo("ctrl-1", "phone", RemoteRole.Controller)

    private fun TestScope.repo(
        transport: FakeRemoteTransport,
        device: RemoteDeviceInfo = prompterDevice,
        lanAddress: String? = "192.168.137.20",
    ): DefaultRemoteSessionRepository = DefaultRemoteSessionRepository(
        transport = transport,
        scope = backgroundScope,
        device = device,
        lanAddressProvider = { lanAddress },
    )

    private fun snapshot(revision: Long) = remotePrompterSnapshot(
        revision = revision,
        surface = RemotePrompterSurface.Setup,
        scriptId = "1",
        scriptTitle = "台本",
        estimatedDurationSeconds = 200,
    )

    private suspend fun DefaultRemoteSessionRepository.asPrompter() {
        prepare(RemoteRole.Prompter)
        startWaiting()
    }

    private suspend fun DefaultRemoteSessionRepository.asController() {
        prepare(RemoteRole.Controller)
    }

    @Test
    fun startWaitingAsPrompterGeneratesPairingPayload() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)

        repository.startWaiting()

        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.WaitingForController)
        assertTrue(repository.sessionState.value.pairingPayload != null)
        assertTrue(repository.sessionState.value.pairingPayload!!.pairingToken.isNotBlank())
        assertEquals(RemoteProtocol.VERSION, repository.sessionState.value.pairingPayload!!.protocolVersion)
    }

    @Test
    fun correctTokenHandshakeConnects() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()

        transport.injectMessage(
            RemoteMessage.ClientHello(
                protocolVersion = RemoteProtocol.VERSION,
                sessionId = payload.sessionId,
                pairingToken = payload.pairingToken,
                device = controllerDevice,
            ),
        )
        advanceUntilIdle()

        val status = repository.sessionState.value.status
        assertTrue(status is RemoteConnectionStatus.Connected)
        assertEquals("phone", (status as RemoteConnectionStatus.Connected).device.displayName)
        // The accepted response was sent and the pairing token is now consumed.
        val sent = transport.sentMessages.value
        assertTrue(sent.any { it is RemoteMessage.ServerAccepted })
    }

    @Test
    fun wrongTokenIsRejected() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()

        transport.injectMessage(
            RemoteMessage.ClientHello(
                protocolVersion = RemoteProtocol.VERSION,
                sessionId = payload.sessionId,
                pairingToken = "wrong-token",
                device = controllerDevice,
            ),
        )
        advanceUntilIdle()

        assertFalse(repository.sessionState.value.status is RemoteConnectionStatus.Connected)
        val rejected = transport.sentMessages.value.filterIsInstance<RemoteMessage.ServerRejected>().lastOrNull()
        assertEquals(RemoteRejectReason.InvalidToken, rejected?.reason)
    }

    @Test
    fun expiredTokenIsRejected() = runTest(UnconfinedTestDispatcher()) {
        var now = 1_000_000L
        val transport = FakeRemoteTransport()
        val repository = DefaultRemoteSessionRepository(
            transport = transport,
            scope = backgroundScope,
            device = prompterDevice,
            nowMillis = { now },
            lanAddressProvider = { "192.168.137.20" },
        )
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        now = payload.expiresAtEpochMillis + 1
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()

        transport.injectMessage(
            RemoteMessage.ClientHello(
                protocolVersion = RemoteProtocol.VERSION,
                sessionId = payload.sessionId,
                pairingToken = payload.pairingToken,
                device = controllerDevice,
            ),
        )
        advanceUntilIdle()

        val rejected = transport.sentMessages.value.filterIsInstance<RemoteMessage.ServerRejected>().lastOrNull()
        assertEquals(RemoteRejectReason.TokenExpired, rejected?.reason)
    }

    @Test
    fun sessionMismatchIsRejected() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()

        transport.injectMessage(
            RemoteMessage.ClientHello(
                protocolVersion = RemoteProtocol.VERSION,
                sessionId = "another-session",
                pairingToken = payload.pairingToken,
                device = controllerDevice,
            ),
        )
        advanceUntilIdle()

        val rejected = transport.sentMessages.value.filterIsInstance<RemoteMessage.ServerRejected>().lastOrNull()
        assertEquals(RemoteRejectReason.SessionMismatch, rejected?.reason)
    }

    @Test
    fun protocolVersionMismatchIsRejected() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()

        transport.injectMessage(
            RemoteMessage.ClientHello(
                protocolVersion = RemoteProtocol.VERSION + 1,
                sessionId = payload.sessionId,
                pairingToken = payload.pairingToken,
                device = controllerDevice,
            ),
        )
        advanceUntilIdle()

        val rejected = transport.sentMessages.value.filterIsInstance<RemoteMessage.ServerRejected>().lastOrNull()
        assertEquals(RemoteRejectReason.ProtocolMismatch, rejected?.reason)
    }

    @Test
    fun secondControllerWithConsumedTokenIsRejected() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()

        // First controller handshakes successfully (QR token consumed).
        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, payload.sessionId, payload.pairingToken, controllerDevice),
        )
        advanceUntilIdle()
        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.Connected)

        // A second controller reusing the now-consumed QR token must be rejected.
        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, payload.sessionId, payload.pairingToken, controllerDevice),
        )
        advanceUntilIdle()

        val rejected = transport.sentMessages.value.filterIsInstance<RemoteMessage.ServerRejected>().lastOrNull()
        assertEquals(RemoteRejectReason.InvalidToken, rejected?.reason)
    }

    @Test
    fun resumeTokenReconnectWhileConnectedIsRejectedAsAlreadyConnected() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()

        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, payload.sessionId, payload.pairingToken, controllerDevice),
        )
        advanceUntilIdle()
        val resumeToken = transport.sentMessages.value
            .filterIsInstance<RemoteMessage.ServerAccepted>()
            .last().resumeToken

        // A reconnect attempt using the resume token while already connected is rejected.
        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, payload.sessionId, resumeToken, controllerDevice),
        )
        advanceUntilIdle()

        val rejected = transport.sentMessages.value.filterIsInstance<RemoteMessage.ServerRejected>().lastOrNull()
        assertEquals(RemoteRejectReason.AlreadyConnected, rejected?.reason)
    }

    @Test
    fun nonHelloFirstMessageIsNotAccepted() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()

        // A command before any handshake must not connect.
        transport.injectMessage(RemoteMessage.CommandRequest(RemoteCommand.PausePlayback("c1")))
        advanceUntilIdle()

        assertFalse(repository.sessionState.value.status is RemoteConnectionStatus.Connected)
        assertTrue(transport.sentMessages.value.none { it is RemoteMessage.ServerAccepted })
    }

    @Test
    fun duplicateCommandIdIsExecutedOnceAndReturnsPriorResult() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()
        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, payload.sessionId, payload.pairingToken, controllerDevice),
        )
        advanceUntilIdle()

        val command = RemoteCommand.PausePlayback("dup-id")
        var delivered = 0
        val job = backgroundScope.launch { repository.incomingCommands.collect { delivered++ } }
        advanceUntilIdle()

        transport.injectMessage(RemoteMessage.CommandRequest(command))
        advanceUntilIdle()

        assertEquals(1, delivered)

        // Record a result for the command, then re-inject the same command id.
        repository.recordResult(
            "dup-id",
            RemoteCommandResultState("dup-id", success = true, resultingSnapshotRevision = 7L),
        )
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.CommandRequest(command))
        advanceUntilIdle()

        val results = transport.sentMessages.value.filterIsInstance<RemoteMessage.CommandResult>()
        val last = results.last()
        assertEquals(true, last.success)
        assertEquals(7L, last.resultingSnapshotRevision)
        job.cancel()
    }

    @Test
    fun invalidCommandIsRejectedWithResult() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()
        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, payload.sessionId, payload.pairingToken, controllerDevice),
        )
        advanceUntilIdle()

        transport.injectMessage(RemoteMessage.CommandRequest(RemoteCommand.SeekBy("bad", 9f)))
        advanceUntilIdle()

        val result = transport.sentMessages.value.filterIsInstance<RemoteMessage.CommandResult>().lastOrNull()
        assertEquals(false, result?.success)
        assertEquals("bad", result?.commandId)
    }

    @Test
    fun controllerConnectSendsClientHello() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.asController()
        val payload = com.zhy20.teleprompter.remote.pairing.RemotePairingPayload(
            protocolVersion = RemoteProtocol.VERSION,
            host = "127.0.0.1",
            port = 8765,
            sessionId = "s1",
            pairingToken = "tok",
            expiresAtEpochMillis = Long.MAX_VALUE,
        )

        repository.connectToPrompter(payload)
        advanceUntilIdle()
        transport.simulateConnected(prompterDevice)
        advanceUntilIdle()

        val hello = transport.sentMessages.value.filterIsInstance<RemoteMessage.ClientHello>().lastOrNull()
        assertEquals("s1", hello?.sessionId)
        assertEquals("tok", hello?.pairingToken)
    }

    @Test
    fun controllerHandshakeProducesConnectedWithPrompterDevice() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.asController()
        val payload = com.zhy20.teleprompter.remote.pairing.RemotePairingPayload(
            protocolVersion = RemoteProtocol.VERSION,
            host = "127.0.0.1",
            port = 8765,
            sessionId = "s1",
            pairingToken = "tok",
            expiresAtEpochMillis = Long.MAX_VALUE,
        )
        repository.connectToPrompter(payload)
        advanceUntilIdle()
        transport.simulateConnected(prompterDevice)
        advanceUntilIdle()

        transport.injectMessage(
            RemoteMessage.ServerAccepted(
                connectionId = "conn-1",
                prompterDevice = prompterDevice,
                resumeToken = "resume-1",
                initialSnapshot = snapshot(2),
            ),
        )
        advanceUntilIdle()

        val status = repository.sessionState.value.status
        assertTrue(status is RemoteConnectionStatus.Connected)
        assertEquals("tablet", (status as RemoteConnectionStatus.Connected).device.displayName)
        assertEquals(2L, repository.snapshot.value?.revision)
    }

    @Test
    fun serverRejectedFailsTheConnection() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.asController()
        val payload = com.zhy20.teleprompter.remote.pairing.RemotePairingPayload(
            protocolVersion = RemoteProtocol.VERSION,
            host = "127.0.0.1",
            port = 8765,
            sessionId = "s1",
            pairingToken = "bad",
            expiresAtEpochMillis = Long.MAX_VALUE,
        )
        repository.connectToPrompter(payload)
        advanceUntilIdle()
        transport.simulateConnected(prompterDevice)
        advanceUntilIdle()

        transport.injectMessage(RemoteMessage.ServerRejected(RemoteRejectReason.InvalidToken))
        advanceUntilIdle()

        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.Failed)
        assertEquals(
            RemoteFailureReason.InvalidPairing,
            (repository.sessionState.value.status as RemoteConnectionStatus.Failed).reason,
        )
    }

    @Test
    fun commandResultIsConsumedViaTakeCommandResult() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.asController()
        val payload = com.zhy20.teleprompter.remote.pairing.RemotePairingPayload(
            protocolVersion = RemoteProtocol.VERSION,
            host = "127.0.0.1",
            port = 8765,
            sessionId = "s1",
            pairingToken = "tok",
            expiresAtEpochMillis = Long.MAX_VALUE,
        )
        repository.connectToPrompter(payload)
        advanceUntilIdle()
        transport.simulateConnected(prompterDevice)
        advanceUntilIdle()
        transport.injectMessage(
            RemoteMessage.ServerAccepted("conn-1", prompterDevice, "resume-1", snapshot(1)),
        )
        advanceUntilIdle()

        transport.injectMessage(
            RemoteMessage.CommandResult(commandId = "c1", success = true, resultingSnapshotRevision = 3L),
        )
        advanceUntilIdle()

        val result = repository.takeCommandResult("c1")
        assertEquals(true, result?.success)
        assertEquals(3L, result?.resultingSnapshotRevision)
        assertNull(repository.takeCommandResult("c1"))
    }

    @Test
    fun stopWaitingClearsSession() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        repository.updatePrompterSnapshot(snapshot(1))
        advanceUntilIdle()

        repository.stopWaiting()

        assertEquals(RemoteConnectionStatus.Disabled, repository.sessionState.value.status)
        assertNull(repository.snapshot.value)
    }

    // ---- explicit disconnects ----

    @Test
    fun controllerDisconnectFromPrompterDoesNotReconnect() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.asController()
        val payload = com.zhy20.teleprompter.remote.pairing.RemotePairingPayload(
            protocolVersion = RemoteProtocol.VERSION,
            host = "127.0.0.1",
            port = 8765,
            sessionId = "s1",
            pairingToken = "tok",
            expiresAtEpochMillis = Long.MAX_VALUE,
        )
        repository.connectToPrompter(payload)
        advanceUntilIdle()
        transport.simulateConnected(prompterDevice)
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ServerAccepted("conn-1", prompterDevice, "resume-1", snapshot(1)))
        advanceUntilIdle()
        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.Connected)

        repository.disconnectFromPrompter()
        advanceUntilIdle()

        assertEquals(RemoteConnectionStatus.Disabled, repository.sessionState.value.status)
        assertNull(repository.snapshot.value)
        // A late transport Disconnected (from the closed socket) must NOT trigger reconnect.
        transport.simulateDisconnected(RemoteFailureReason.HandshakeFailed)
        advanceUntilIdle()
        assertEquals(RemoteConnectionStatus.Disabled, repository.sessionState.value.status)
    }

    @Test
    fun prompterDisconnectControllerRotatesSessionAndQr() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val firstPayload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()
        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, firstPayload.sessionId, firstPayload.pairingToken, controllerDevice),
        )
        advanceUntilIdle()
        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.Connected)

        repository.disconnectController()
        advanceUntilIdle()

        // Back to waiting with a brand-new session/token (old QR invalidated).
        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.WaitingForController)
        val newPayload = repository.sessionState.value.pairingPayload
        assertTrue(newPayload != null)
        assertTrue(newPayload!!.sessionId != firstPayload.sessionId)
        assertTrue(newPayload.pairingToken != firstPayload.pairingToken)
        // The DisconnectNotice was sent to the old controller.
        assertTrue(transport.sentMessages.value.any { it is RemoteMessage.DisconnectNotice })
    }

    @Test
    fun prompterStopHostingReturnsDisabledAndCloses() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()
        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, payload.sessionId, payload.pairingToken, controllerDevice),
        )
        advanceUntilIdle()

        repository.stopHosting()
        advanceUntilIdle()

        assertEquals(RemoteConnectionStatus.Disabled, repository.sessionState.value.status)
        assertNull(repository.snapshot.value)
        assertNull(repository.sessionState.value.pairingPayload)
        // The old controller cannot resume: a stale Disconnected callback must not change state.
        transport.simulateDisconnected(RemoteFailureReason.HandshakeFailed)
        advanceUntilIdle()
        assertEquals(RemoteConnectionStatus.Disabled, repository.sessionState.value.status)
    }

    @Test
    fun repeatedDisconnectsAreIdempotent() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.asController()
        val payload = com.zhy20.teleprompter.remote.pairing.RemotePairingPayload(
            protocolVersion = RemoteProtocol.VERSION,
            host = "127.0.0.1",
            port = 8765,
            sessionId = "s1",
            pairingToken = "tok",
            expiresAtEpochMillis = Long.MAX_VALUE,
        )
        repository.connectToPrompter(payload)
        advanceUntilIdle()

        repository.disconnectFromPrompter()
        repository.disconnectFromPrompter()
        repository.disconnectFromPrompter()
        advanceUntilIdle()

        assertEquals(RemoteConnectionStatus.Disabled, repository.sessionState.value.status)
    }

    @Test
    fun freshConnectAfterDisconnectResetsUserInitiatedFlag() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.asController()
        val payload = com.zhy20.teleprompter.remote.pairing.RemotePairingPayload(
            protocolVersion = RemoteProtocol.VERSION,
            host = "127.0.0.1",
            port = 8765,
            sessionId = "s1",
            pairingToken = "tok",
            expiresAtEpochMillis = Long.MAX_VALUE,
        )
        repository.connectToPrompter(payload)
        advanceUntilIdle()
        transport.simulateConnected(prompterDevice)
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ServerAccepted("conn-1", prompterDevice, "resume-1", snapshot(1)))
        advanceUntilIdle()

        repository.disconnectFromPrompter()
        advanceUntilIdle()

        // Reconnect with a fresh payload: the user-initiated flag must be cleared so an
        // unexpected drop after this DOES reconnect.
        val freshPayload = payload.copy(sessionId = "s2", pairingToken = "tok2")
        repository.connectToPrompter(freshPayload)
        advanceUntilIdle()
        transport.simulateConnected(prompterDevice)
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ServerAccepted("conn-2", prompterDevice, "resume-2", snapshot(2)))
        advanceUntilIdle()
        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.Connected)

        transport.simulateDisconnected(RemoteFailureReason.HandshakeFailed)
        advanceUntilIdle()
        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.Reconnecting)
    }

    // ---- reading sync (window + absolute cursor) ----

    private suspend fun TestScope.connectedPrompter(transport: FakeRemoteTransport): DefaultRemoteSessionRepository {
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()
        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, payload.sessionId, payload.pairingToken, controllerDevice),
        )
        advanceUntilIdle()
        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.Connected)
        return repository
    }

    private suspend fun TestScope.connectedController(transport: FakeRemoteTransport): DefaultRemoteSessionRepository {
        val repository = repo(transport)
        repository.asController()
        repository.connectToPrompter(
            com.zhy20.teleprompter.remote.pairing.RemotePairingPayload(
                protocolVersion = RemoteProtocol.VERSION,
                host = "127.0.0.1",
                port = 8765,
                sessionId = "s1",
                pairingToken = "tok",
                expiresAtEpochMillis = Long.MAX_VALUE,
            ),
        )
        advanceUntilIdle()
        transport.simulateConnected(prompterDevice)
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ServerAccepted("conn-1", prompterDevice, "resume-1", snapshot(1)))
        advanceUntilIdle()
        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.Connected)
        return repository
    }

    @Test
    fun prompterSendsReadingWindowOncePerWindowRevision() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = connectedPrompter(transport)
        val w1 = RemoteMessage.ReadingWindowUpdate(1, 1, 0, 50, "a".repeat(50))
        repository.updateReadingWindow(w1)
        advanceUntilIdle()
        repository.updateReadingWindow(w1) // same revision -> deduped
        advanceUntilIdle()

        val sent = transport.sentMessages.value.filterIsInstance<RemoteMessage.ReadingWindowUpdate>()
        assertEquals(1, sent.size)
        assertEquals(1L, sent.single().windowRevision)

        val w2 = w1.copy(windowRevision = 2L)
        repository.updateReadingWindow(w2)
        advanceUntilIdle()
        assertEquals(2, transport.sentMessages.value.filterIsInstance<RemoteMessage.ReadingWindowUpdate>().size)
    }

    @Test
    fun prompterCursorIsLatestOnlyDedupedAndJumpBypassesGate() = runTest(UnconfinedTestDispatcher()) {
        // Deterministic clock: the cursor channel must throttle ordinary motion to ~16 Hz and
        // let seek-style jumps pass immediately. Intermediate frames are dropped (latest-only).
        var clockNanos = 0L
        val transport = FakeRemoteTransport()
        val repository = DefaultRemoteSessionRepository(
            transport = transport,
            scope = backgroundScope,
            lanAddressProvider = { "192.168.137.20" },
            nanoTime = { clockNanos },
            nowRealtimeMillis = { clockNanos / 1_000_000L },
        )
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()
        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, payload.sessionId, payload.pairingToken, controllerDevice),
        )
        advanceUntilIdle()

        repository.updateReadingCursor(cursor(1, 100.0, 1))
        advanceUntilIdle()
        repository.updateReadingCursor(cursor(1, 100.0, 2)) // identical -> deduped
        advanceUntilIdle()

        clockNanos = 10_000_000L // 10 ms after the first send -> still inside the 60 ms gate
        repository.updateReadingCursor(cursor(1, 100.5, 3))
        advanceUntilIdle()
        clockNanos = 20_000_000L
        repository.updateReadingCursor(cursor(1, 101.0, 4)) // still < 2.0 from the last sent -> gated
        advanceUntilIdle()

        clockNanos = 30_000_000L
        repository.updateReadingCursor(cursor(1, 300.0, 5)) // seek jump -> sent immediately
        advanceUntilIdle()

        val sent = transport.sentMessages.value.filterIsInstance<RemoteMessage.ReadingCursorUpdate>()
        // Only the first and the jump were transmitted; intermediate frames were dropped and
        // identical cursors were never resent.
        assertEquals(listOf(100.0, 300.0), sent.map { it.absoluteOffset })
        // The sequence is stamped by the repository and is monotonic.
        assertTrue(sent.map { it.sequence }.let { it == it.sorted() })
    }

    @Test
    fun prompterDoesNotSendReadingWhenNotConnected() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)

        repository.updateReadingWindow(RemoteMessage.ReadingWindowUpdate(1, 1, 0, 10, "abc"))
        repository.updateReadingCursor(cursor(1, 5.0, 1))
        advanceUntilIdle()
        assertTrue(transport.sentMessages.value.none { it is RemoteMessage.ReadingWindowUpdate || it is RemoteMessage.ReadingCursorUpdate })
    }

    @Test
    fun controllerStoresReadingWindowAndCursor() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = connectedController(transport)

        transport.injectMessage(RemoteMessage.ReadingWindowUpdate(1, 3, 20, 120, "中".repeat(100)))
        advanceUntilIdle()
        assertEquals(3L, repository.readingWindow.value?.windowRevision)
        assertEquals(20, repository.readingWindow.value?.startOffset)
        assertEquals(120, repository.readingWindow.value?.endOffset)

        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(1, 86.5, 7, 1000L))
        advanceUntilIdle()
        assertEquals(86.5, repository.readingCursor.value?.absoluteOffset ?: -1.0, 1e-6)
        assertEquals(7L, repository.readingCursor.value?.sequence)
    }

    @Test
    fun controllerIgnoresStaleOrRepeatedCursorSequence() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = connectedController(transport)

        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(1, 50.0, 5, 0))
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(1, 60.0, 3, 0)) // stale
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(1, 70.0, 5, 0)) // repeated
        advanceUntilIdle()

        assertEquals(50.0, repository.readingCursor.value?.absoluteOffset ?: -1.0, 1e-6)
    }

    @Test
    fun controllerDropsCursorWithWrongTextRevisionUntilMatchingWindow() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = connectedController(transport)

        transport.injectMessage(RemoteMessage.ReadingWindowUpdate(1, 1, 0, 100, "a".repeat(100)))
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(2, 40.0, 1, 0)) // mismatched text revision
        advanceUntilIdle()
        assertEquals(null, repository.readingCursor.value)

        // The matching window arrives, then the cursor is accepted.
        transport.injectMessage(RemoteMessage.ReadingWindowUpdate(2, 2, 0, 100, "b".repeat(100)))
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(2, 40.0, 2, 0))
        advanceUntilIdle()
        assertEquals(40.0, repository.readingCursor.value?.absoluteOffset ?: -1.0, 1e-6)
        assertEquals(2L, repository.readingWindow.value?.textRevision)
    }

    @Test
    fun controllerResetsReadingStateOnReconnectHandshake() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = connectedController(transport)

        transport.injectMessage(RemoteMessage.ReadingWindowUpdate(1, 1, 0, 50, "a".repeat(50)))
        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(1, 10.0, 1, 0))
        advanceUntilIdle()
        assertTrue(repository.readingWindow.value != null)
        assertTrue(repository.readingCursor.value != null)

        // A re-established connection must not reuse the pre-drop reading state.
        transport.injectMessage(RemoteMessage.ServerAccepted("conn-2", prompterDevice, "resume-2", snapshot(2)))
        advanceUntilIdle()
        assertEquals(null, repository.readingWindow.value)
        assertEquals(null, repository.readingCursor.value)
    }

    @Test
    fun prompterResendsReadingWindowAfterNewControllerHandshake() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.prepare(RemoteRole.Prompter)
        repository.startWaiting()
        val payload = repository.sessionState.value.pairingPayload!!
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()
        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, payload.sessionId, payload.pairingToken, controllerDevice),
        )
        advanceUntilIdle()
        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.Connected)

        val w1 = RemoteMessage.ReadingWindowUpdate(1, 1, 0, 50, "a".repeat(50))
        repository.updateReadingWindow(w1)
        advanceUntilIdle()
        repository.updateReadingWindow(w1) // deduped before the drop
        advanceUntilIdle()
        assertEquals(1, transport.sentMessages.value.filterIsInstance<RemoteMessage.ReadingWindowUpdate>().size)

        // The controller drops; the prompter keeps waiting with a resume credential.
        transport.simulateDisconnected(RemoteFailureReason.HandshakeFailed)
        advanceUntilIdle()
        val resumeToken = transport.sentMessages.value.filterIsInstance<RemoteMessage.ServerAccepted>().last().resumeToken
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()
        transport.injectMessage(
            RemoteMessage.ClientHello(RemoteProtocol.VERSION, payload.sessionId, resumeToken, controllerDevice),
        )
        advanceUntilIdle()
        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.Connected)

        // The same window revision is delivered again: the handshake reset the outgoing dedup.
        repository.updateReadingWindow(w1)
        advanceUntilIdle()
        assertEquals(2, transport.sentMessages.value.filterIsInstance<RemoteMessage.ReadingWindowUpdate>().size)
    }

    @Test
    fun controllerHoldsPendingCursorUntilACoveringWindowArrives() = runTest(UnconfinedTestDispatcher()) {
        // §29: a cursor that arrives outside the current window (reorder or a seek) must be kept
        // as the latest (pending) cursor, never dropped, and applied once a covering window lands.
        val transport = FakeRemoteTransport()
        val repository = connectedController(transport)

        transport.injectMessage(RemoteMessage.ReadingWindowUpdate(1, 1, 0, 700, "a".repeat(700)))
        advanceUntilIdle()
        // Cursor 760 is beyond the current window 0..700 — the repository keeps it (pending).
        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(1, 760.0, 1, 0))
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(1, 780.0, 2, 0))
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(1, 810.0, 3, 0))
        advanceUntilIdle()
        assertEquals(810.0, repository.readingCursor.value?.absoluteOffset ?: -1.0, 1e-6)

        // The covering window arrives later; the latest pending cursor must be applied.
        transport.injectMessage(RemoteMessage.ReadingWindowUpdate(1, 2, 500, 1200, "b".repeat(700)))
        advanceUntilIdle()
        assertEquals(2L, repository.readingWindow.value?.windowRevision)
        assertEquals(810.0, repository.readingCursor.value?.absoluteOffset ?: -1.0, 1e-6)
    }

    @Test
    fun controllerAppliesWindowEvenWhenCursorArrivesBetweenWindows() = runTest(UnconfinedTestDispatcher()) {
        // A window update must be applied regardless of cursor interleaving, and a cursor that
        // follows lands on the newest window.
        val transport = FakeRemoteTransport()
        val repository = connectedController(transport)

        transport.injectMessage(RemoteMessage.ReadingWindowUpdate(1, 1, 0, 700, "a".repeat(700)))
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(1, 100.0, 1, 0))
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ReadingWindowUpdate(1, 2, 200, 900, "c".repeat(700)))
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.ReadingCursorUpdate(1, 300.0, 2, 0))
        advanceUntilIdle()

        assertEquals(2L, repository.readingWindow.value?.windowRevision)
        assertEquals(300.0, repository.readingCursor.value?.absoluteOffset ?: -1.0, 1e-6)
    }

    private fun cursor(textRevision: Long, absoluteOffset: Double, sequence: Long) =
        RemoteMessage.ReadingCursorUpdate(textRevision, absoluteOffset, sequence, 0L)
}
