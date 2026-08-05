package com.zhy20.teleprompter.app

import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.data.fake.FakeData
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemoteSessionState
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.session.RemoteCommandToEffect
import com.zhy20.teleprompter.remote.session.RemoteNavigationEffect
import com.zhy20.teleprompter.remote.session.RemoteSessionEffect
import com.zhy20.teleprompter.remote.session.RemoteSessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    @Test
    fun startPlaybackCommandDrivesRealNavigationAndBusinessCalls() = runTest(UnconfinedTestDispatcher()) {
        val state = AppState(
            initialScripts = FakeData.scripts,
            initialFolders = FakeData.folders,
            initialDefaults = FakeData.defaultPlaybackSettings,
        )
        val effects = MutableSharedFlow<RemoteSessionEffect>(extraBufferCapacity = 8)
        val repository = StubRemoteSessionRepository(effects)

        var navigatedScriptId: String? = null
        val coordinator = RemoteAppCoordinator(
            appState = state,
            repository = repository,
            scope = backgroundScope,
            navigator = { effect ->
                when (effect) {
                    is RemoteNavigationEffect.StartPrompter -> {
                        state.beginPlayback(effect.scriptId)
                        navigatedScriptId = effect.scriptId
                    }
                }
            },
        )

        effects.emit(RemoteSessionEffect.ExecuteCommand(RemoteCommand.StartPlayback("cmd", "1")))
        advanceUntilIdle()

        assertEquals("1", navigatedScriptId)
        assertTrue(state.playbackState is PlaybackState.Countdown || state.playbackState == PlaybackState.Playing)
    }
}

private class StubRemoteSessionRepository(
    override val sessionEffects: Flow<RemoteSessionEffect>,
) : RemoteSessionRepository {
    override val sessionState: StateFlow<RemoteSessionState> = MutableStateFlow(RemoteSessionState())
    override val snapshot: StateFlow<RemotePrompterSnapshot?> = MutableStateFlow(null)
    override val incomingCommands: Flow<RemoteCommand> = MutableSharedFlow()
    override suspend fun startWaiting() = Unit
    override suspend fun stopWaiting() = Unit
    override suspend fun disconnect() = Unit
    override suspend fun sendCommand(command: RemoteCommand) = Unit
    override fun updatePrompterSnapshot(snapshot: RemotePrompterSnapshot) = Unit
    override fun resetCommandHistory() = Unit
}
