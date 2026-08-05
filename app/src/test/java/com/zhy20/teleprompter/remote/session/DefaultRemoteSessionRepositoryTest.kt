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
}
