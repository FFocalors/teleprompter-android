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
import com.zhy20.teleprompter.remote.transport.FakeRemoteTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRemoteSessionRepositoryTest {

    private val controllerDevice = RemoteDeviceInfo("ctrl-1", "phone", RemoteRole.Controller)

    /**
     * Uses [UnconfinedTestDispatcher] so the repository's collectors subscribe eagerly and
     * [FakeRemoteTransport] emissions are delivered immediately.
     */
    private fun TestScope.repo(transport: FakeRemoteTransport): DefaultRemoteSessionRepository =
        DefaultRemoteSessionRepository(transport, backgroundScope)

    private fun snapshot(revision: Long) = remotePrompterSnapshot(
        revision = revision,
        surface = RemotePrompterSurface.Setup,
        scriptId = "1",
        scriptTitle = "台本",
        estimatedDurationSeconds = 200,
    )

    @Test
    fun startWaitingTransitionsToWaitingForController() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)

        repository.startWaiting()

        assertEquals(RemoteConnectionStatus.WaitingForController, repository.sessionState.value.status)
    }

    @Test
    fun connectionMovesStateToConnectedWithDevice() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.startWaiting()
        advanceUntilIdle()
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()

        val status = repository.sessionState.value.status
        assertTrue(status is RemoteConnectionStatus.Connected)
        assertEquals("phone", (status as RemoteConnectionStatus.Connected).device.displayName)
    }

    @Test
    fun disconnectReturnsToDisabledAndClearsSnapshot() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.startWaiting()
        advanceUntilIdle()
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()
        repository.updatePrompterSnapshot(snapshot(1))
        advanceUntilIdle()

        repository.disconnect()

        assertEquals(RemoteConnectionStatus.Disabled, repository.sessionState.value.status)
        assertNull(repository.snapshot.value)
    }

    @Test
    fun stopWaitingStopsForwardingMessages() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.startWaiting()
        advanceUntilIdle()
        repository.stopWaiting()

        val commands = mutableListOf<RemoteCommand>()
        val job = backgroundScope.launch { repository.incomingCommands.collect { commands.add(it) } }
        advanceUntilIdle()
        transport.injectMessage(RemoteMessage.Command(RemoteCommand.PausePlayback("c1")))
        advanceUntilIdle()

        assertTrue(commands.isEmpty())
        job.cancel()
    }

    @Test
    fun duplicateCommandIdIsDeliveredOnlyOnce() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.startWaiting()
        advanceUntilIdle()

        val delivered = mutableListOf<RemoteCommand>()
        val job = backgroundScope.launch { repository.incomingCommands.collect { delivered.add(it) } }
        advanceUntilIdle()

        val command = RemoteCommand.PausePlayback("same-id")
        transport.injectMessage(RemoteMessage.Command(command))
        transport.injectMessage(RemoteMessage.Command(command))
        advanceUntilIdle()

        assertEquals(listOf("same-id"), delivered.map { it.commandId })
        job.cancel()
    }

    @Test
    fun invalidCommandIsRejectedWithResultAndNotEmitted() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.startWaiting()
        advanceUntilIdle()

        val delivered = mutableListOf<RemoteCommand>()
        val job = backgroundScope.launch { repository.incomingCommands.collect { delivered.add(it) } }
        advanceUntilIdle()

        transport.injectMessage(RemoteMessage.Command(RemoteCommand.SeekBy("bad", 9f)))
        advanceUntilIdle()

        val sent = transport.sentMessages.value
        val result = sent.filterIsInstance<RemoteMessage.CommandResult>().lastOrNull()
        assertEquals(false, result?.accepted)
        assertEquals("bad", result?.commandId)
        assertTrue(delivered.isEmpty())
        job.cancel()
    }

    @Test
    fun validCommandIsEmittedAsExecuteEffect() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.startWaiting()
        advanceUntilIdle()

        val delivered = mutableListOf<RemoteCommand>()
        val job = backgroundScope.launch { repository.incomingCommands.collect { delivered.add(it) } }
        advanceUntilIdle()

        transport.injectMessage(RemoteMessage.Command(RemoteCommand.PausePlayback("c1")))
        advanceUntilIdle()

        assertEquals(listOf("c1"), delivered.map { it.commandId })
        job.cancel()
    }

    @Test
    fun snapshotIsForwardedToTransportWhenConnected() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.startWaiting()
        advanceUntilIdle()
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()

        repository.updatePrompterSnapshot(snapshot(3))
        advanceUntilIdle()

        val sent = transport.sentMessages.value
        val snapshotMessage = sent.filterIsInstance<RemoteMessage.Snapshot>().lastOrNull()
        assertEquals(3L, snapshotMessage?.snapshot?.revision)
    }

    @Test
    fun staleSnapshotRevisionIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.startWaiting()
        repository.updatePrompterSnapshot(snapshot(5))
        advanceUntilIdle()

        repository.updatePrompterSnapshot(snapshot(3))

        assertEquals(5L, repository.snapshot.value?.revision)
    }

    @Test
    fun unsupportedProtocolVersionFailsWithStructuredReason() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.startWaiting()
        advanceUntilIdle()

        transport.injectMessage(
            RemoteMessage.Hello(
                protocolVersion = RemoteProtocol.VERSION + 1,
                role = RemoteRole.Controller,
                device = controllerDevice,
            ),
        )
        advanceUntilIdle()

        assertTrue(repository.sessionState.value.status is RemoteConnectionStatus.Failed)
        assertEquals(
            RemoteFailureReason.ProtocolMismatch,
            (repository.sessionState.value.status as RemoteConnectionStatus.Failed).reason,
        )
    }

    @Test
    fun sendCommandWhenDisconnectedIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)

        repository.sendCommand(RemoteCommand.PausePlayback("c1"))
        advanceUntilIdle()

        assertEquals(0, transport.sentMessages.value.size)
    }

    @Test
    fun sendCommandSendsProtocolMessageWhenConnected() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.startWaiting()
        advanceUntilIdle()
        transport.simulateConnected(controllerDevice)
        advanceUntilIdle()

        repository.sendCommand(RemoteCommand.PausePlayback("c1"))
        advanceUntilIdle()

        val sent = transport.sentMessages.value
        assertTrue(sent.any { it is RemoteMessage.Command && it.command.commandId == "c1" })
    }

    @Test
    fun incomingSnapshotUpdatesSnapshotFlow() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeRemoteTransport()
        val repository = repo(transport)
        repository.startWaiting()
        advanceUntilIdle()

        transport.injectMessage(RemoteMessage.Snapshot(snapshot(2)))
        advanceUntilIdle()

        assertEquals(2L, repository.snapshot.value?.revision)
    }
}
