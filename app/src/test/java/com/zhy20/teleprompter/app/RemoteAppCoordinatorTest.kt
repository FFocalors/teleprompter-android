package com.zhy20.teleprompter.app

import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.data.fake.FakeData
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.model.RemoteSessionState
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.protocol.RemoteRejectReason
import com.zhy20.teleprompter.remote.session.RemoteCommandResultState
import com.zhy20.teleprompter.remote.session.RemoteCommandToEffect
import com.zhy20.teleprompter.remote.session.RemoteDeviceInfoHolder
import com.zhy20.teleprompter.remote.session.RemoteNavigationEffect
import com.zhy20.teleprompter.remote.session.RemoteSessionEffect
import com.zhy20.teleprompter.remote.session.RemoteSessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommandToEffectTest {

    @Test
    fun startPlaybackMapsToStartPrompterNavigation() {
        val effect = RemoteCommandToEffect.map(RemoteCommand.StartPlayback("cmd", "7"))
        assertTrue(effect is RemoteNavigationEffect.StartPrompter)
        assertEquals("7", (effect as RemoteNavigationEffect.StartPrompter).scriptId)
    }

    @Test
    fun nonNavigationCommandsMapToNull() {
        assertNull(RemoteCommandToEffect.map(RemoteCommand.PausePlayback("cmd")))
        assertNull(RemoteCommandToEffect.map(RemoteCommand.ResumeImmediately("cmd")))
        assertNull(RemoteCommandToEffect.map(RemoteCommand.ResumeWithCountdown("cmd")))
        assertNull(RemoteCommandToEffect.map(RemoteCommand.SeekBy("cmd", 0.03f)))
        assertNull(RemoteCommandToEffect.map(RemoteCommand.ChangeSpeed("cmd", 0.1f)))
        assertNull(RemoteCommandToEffect.map(RemoteCommand.EndPlayback("cmd")))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteAppCoordinatorTest {

    private fun appState(): AppState = AppState(
        initialScripts = FakeData.scripts,
        initialFolders = FakeData.folders,
        initialDefaults = FakeData.defaultPlaybackSettings,
    )

    @Test
    fun startPlaybackOnSetupFlushesThenNavigatesAndRecordsSuccess() = runTest(UnconfinedTestDispatcher()) {
        val state = appState()
        state.selectScript("1")
        state.setSurface(PrompterSurface.Setup)

        val effects = MutableSharedFlow<RemoteSessionEffect>(extraBufferCapacity = 8)
        val repository = StubRemoteSessionRepository(effects)
        val handler = RemoteStartPlaybackHandler()

        val coordinator = RemoteAppCoordinator(state, repository, backgroundScope, handler)

        // Simulate the setup page collecting the request and completing it successfully.
        backgroundScope.launch {
            val request = handler.awaitRequest()
            state.beginPlayback(request.scriptId)
            state.setSurface(PrompterSurface.Prompter)
            handler.complete(request, true)
        }

        effects.emit(RemoteSessionEffect.ExecuteCommand(RemoteCommand.StartPlayback("cmd", "1")))
        advanceUntilIdle()

        assertTrue(state.playbackState is PlaybackState.Countdown || state.playbackState == PlaybackState.Playing)
        assertTrue(repository.recorded.any { it.commandId == "cmd" && it.success })
    }

    @Test
    fun startPlaybackWhenNotOnSetupIsRejected() = runTest(UnconfinedTestDispatcher()) {
        val state = appState()
        state.selectScript("1")
        state.setSurface(PrompterSurface.Library)

        val effects = MutableSharedFlow<RemoteSessionEffect>(extraBufferCapacity = 8)
        val repository = StubRemoteSessionRepository(effects)
        val handler = RemoteStartPlaybackHandler()
        val coordinator = RemoteAppCoordinator(state, repository, backgroundScope, handler)

        effects.emit(RemoteSessionEffect.ExecuteCommand(RemoteCommand.StartPlayback("cmd", "1")))
        advanceUntilIdle()

        val result = repository.recorded.first { it.commandId == "cmd" }
        assertEquals(false, result.success)
        assertEquals(RemoteRejectReason.CommandNotAllowedInState, result.errorReason)
    }

    @Test
    fun startPlaybackWithWrongScriptIsRejectedAsScriptNotFound() = runTest(UnconfinedTestDispatcher()) {
        val state = appState()
        state.selectScript("1")
        state.setSurface(PrompterSurface.Setup)

        val effects = MutableSharedFlow<RemoteSessionEffect>(extraBufferCapacity = 8)
        val repository = StubRemoteSessionRepository(effects)
        val handler = RemoteStartPlaybackHandler()
        val coordinator = RemoteAppCoordinator(state, repository, backgroundScope, handler)

        effects.emit(RemoteSessionEffect.ExecuteCommand(RemoteCommand.StartPlayback("cmd", "999")))
        advanceUntilIdle()

        val result = repository.recorded.first { it.commandId == "cmd" }
        assertEquals(false, result.success)
        assertEquals(RemoteRejectReason.ScriptNotFound, result.errorReason)
    }

    @Test
    fun setupSaveFailureRejectsStartPlayback() = runTest(UnconfinedTestDispatcher()) {
        val state = appState()
        state.selectScript("1")
        state.setSurface(PrompterSurface.Setup)

        val effects = MutableSharedFlow<RemoteSessionEffect>(extraBufferCapacity = 8)
        val repository = StubRemoteSessionRepository(effects)
        val handler = RemoteStartPlaybackHandler()

        val coordinator = RemoteAppCoordinator(state, repository, backgroundScope, handler)

        backgroundScope.launch {
            val request = handler.awaitRequest()
            handler.complete(request, false) // flush failed
        }

        effects.emit(RemoteSessionEffect.ExecuteCommand(RemoteCommand.StartPlayback("cmd", "1")))
        advanceUntilIdle()

        val result = repository.recorded.first { it.commandId == "cmd" }
        assertEquals(false, result.success)
        assertEquals(RemoteRejectReason.SetupSaveFailed, result.errorReason)
    }

    @Test
    fun pauseWhenNotPlayingIsRejected() = runTest(UnconfinedTestDispatcher()) {
        val state = appState()
        state.selectScript("1")
        state.setSurface(PrompterSurface.Setup)

        val effects = MutableSharedFlow<RemoteSessionEffect>(extraBufferCapacity = 8)
        val repository = StubRemoteSessionRepository(effects)
        val handler = RemoteStartPlaybackHandler()
        val coordinator = RemoteAppCoordinator(state, repository, backgroundScope, handler)

        effects.emit(RemoteSessionEffect.ExecuteCommand(RemoteCommand.PausePlayback("cmd")))
        advanceUntilIdle()

        val result = repository.recorded.first { it.commandId == "cmd" }
        assertEquals(false, result.success)
        assertEquals(RemoteRejectReason.CommandNotAllowedInState, result.errorReason)
    }

    @Test
    fun pauseWhilePlayingSucceedsAndRecordsRevision() = runTest(UnconfinedTestDispatcher()) {
        val state = appState()
        // Script "4" has countdown Off, so beginPlayback lands directly in Playing.
        state.selectScript("4")
        state.updatePlaybackSettings(state.playbackSettings.copy(countdown = com.zhy20.teleprompter.core.model.CountdownOption.Off))
        state.beginPlayback("4")
        state.setSurface(PrompterSurface.Prompter)

        val effects = MutableSharedFlow<RemoteSessionEffect>(extraBufferCapacity = 8)
        val repository = StubRemoteSessionRepository(effects)
        val handler = RemoteStartPlaybackHandler()
        val coordinator = RemoteAppCoordinator(state, repository, backgroundScope, handler)

        effects.emit(RemoteSessionEffect.ExecuteCommand(RemoteCommand.PausePlayback("cmd")))
        advanceUntilIdle()

        val result = repository.recorded.first { it.commandId == "cmd" }
        assertEquals(true, result.success)
        assertTrue(result.resultingSnapshotRevision != null && result.resultingSnapshotRevision!! >= 1L)
        assertEquals(PlaybackState.Paused, state.playbackState)
    }

    @Test
    fun duplicateStartPlaybackEmitsRequestPerDistinctCommand() = runTest(UnconfinedTestDispatcher()) {
        // Note: command deduplication by commandId is the repository's job (verified in
        // DefaultRemoteSessionRepositoryTest). Here we verify the coordinator forwards each
        // distinct ExecuteCommand to the start handler and completes both.
        val state = appState()
        state.selectScript("1")
        state.setSurface(PrompterSurface.Setup)

        val effects = MutableSharedFlow<RemoteSessionEffect>(extraBufferCapacity = 8)
        val repository = StubRemoteSessionRepository(effects)
        val handler = RemoteStartPlaybackHandler()

        val coordinator = RemoteAppCoordinator(state, repository, backgroundScope, handler)

        var handled = 0
        backgroundScope.launch {
            repeat(2) {
                val request = handler.awaitRequest()
                handled++
                handler.complete(request, true)
            }
        }

        effects.emit(RemoteSessionEffect.ExecuteCommand(RemoteCommand.StartPlayback("cmd-1", "1")))
        effects.emit(RemoteSessionEffect.ExecuteCommand(RemoteCommand.StartPlayback("cmd-2", "1")))
        advanceUntilIdle()

        assertEquals(2, handled)
        assertEquals(2, repository.recorded.count { it.success && it.commandId.startsWith("cmd-") })
    }
}

class StubRemoteSessionRepository(
    override val sessionEffects: Flow<RemoteSessionEffect>,
) : RemoteSessionRepository {
    override val sessionState: StateFlow<RemoteSessionState> = MutableStateFlow(RemoteSessionState())
    override val snapshot: StateFlow<RemotePrompterSnapshot?> = MutableStateFlow(null)
    override val incomingCommands: Flow<RemoteCommand> = MutableSharedFlow()

    private val holder = object : RemoteDeviceInfoHolder {
        override val device = com.zhy20.teleprompter.remote.model.RemoteDeviceInfo(
            "stub",
            "stub",
            RemoteRole.Prompter,
        )
    }
    override val localDevice: RemoteDeviceInfoHolder = holder

    val recorded = mutableListOf<RemoteCommandResultState>()

    override suspend fun prepare(role: RemoteRole) = Unit
    override suspend fun startWaiting() = Unit
    override suspend fun stopWaiting() = Unit
    override suspend fun connectToPrompter(payload: com.zhy20.teleprompter.remote.pairing.RemotePairingPayload) = Unit
    override suspend fun connectManual(host: String, port: Int, sessionId: String, token: String) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun disconnectController() = Unit
    override suspend fun disconnectFromPrompter() = Unit
    override suspend fun stopHosting() = Unit
    override suspend fun resetRole() = Unit
    override suspend fun sendCommand(command: RemoteCommand) = Unit
    override fun updatePrompterSnapshot(snapshot: RemotePrompterSnapshot) = Unit
    override fun resetCommandHistory() = Unit
    override fun takeCommandResult(commandId: String): RemoteCommandResultState? = null
    override fun recordResult(commandId: String, result: RemoteCommandResultState) {
        recorded += result
    }
}
