package com.zhy20.teleprompter.app

import com.zhy20.teleprompter.core.model.PlaybackEvent
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.core.model.currentNormalEstimatedDurationSeconds
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.session.RemoteCommandToEffect
import com.zhy20.teleprompter.remote.session.RemoteNavigationEffect
import com.zhy20.teleprompter.remote.session.RemoteSessionEffect
import com.zhy20.teleprompter.remote.session.RemoteSessionRepository
import com.zhy20.teleprompter.remote.session.RemoteSnapshotFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Application-side coordinator that turns [RemoteSessionEffect]s into real business calls on
 * [AppState] and real navigation. It holds neither a NavController nor the transport; the
 * [navigator] callback performs navigation, and [RemoteSessionRepository.updatePrompterSnapshot]
 * publishes fresh snapshots back to the controller.
 *
 * This is the single place where remote commands reach the existing playback engine — the
 * remote layer never mutates [AppState] directly.
 */
class RemoteAppCoordinator(
    private val appState: AppState,
    private val repository: RemoteSessionRepository,
    private val scope: CoroutineScope,
    private val navigator: (RemoteNavigationEffect) -> Unit,
) {
    /** Monotonic snapshot revision for this session; persists across command handling. */
    private var revision = 0L

    init {
        repository.sessionEffects
            .onEach(::handleEffect)
            .launchIn(scope)
    }

    private fun handleEffect(effect: RemoteSessionEffect) {
        when (effect) {
            is RemoteSessionEffect.ExecuteCommand -> execute(effect.command)
        }
    }

    private fun execute(command: RemoteCommand) {
        when (command) {
            is RemoteCommand.StartPlayback -> {
                // Navigation effect: the navigator verifies the setup page, saves current
                // settings, calls beginPlayback and navigates to the prompter route.
                RemoteCommandToEffect.map(command)?.let { effect -> navigator(effect) }
                publishSnapshot()
            }
            is RemoteCommand.PausePlayback -> appState.onPlaybackEvent(PlaybackEvent.PausePlayback)
            is RemoteCommand.ResumeImmediately -> appState.onPlaybackEvent(PlaybackEvent.ResumeImmediately)
            is RemoteCommand.ResumeWithCountdown -> appState.onPlaybackEvent(PlaybackEvent.ResumeWithCountdown)
            is RemoteCommand.SeekBy -> {
                val clamped = command.delta.coerceIn(-1f, 1f)
                appState.onPlaybackEvent(PlaybackEvent.SeekTo((appState.progress + clamped).coerceIn(0f, 1f)))
            }
            is RemoteCommand.ChangeSpeed -> {
                if (command.delta > 0f) appState.onPlaybackEvent(PlaybackEvent.IncreaseSpeed)
                else if (command.delta < 0f) appState.onPlaybackEvent(PlaybackEvent.DecreaseSpeed)
            }
            is RemoteCommand.EndPlayback -> appState.onPlaybackEvent(PlaybackEvent.EndPlayback)
        }
        publishSnapshot()
    }

    /** Called after a navigation effect has moved the app to the prompter page. */
    fun markPrompterSurface(scriptId: String) {
        appState.selectScript(scriptId)
        appState.setSurface(PrompterSurface.Prompter)
        publishSnapshot()
    }

    /**
     * Builds and publishes an immutable snapshot from the current real app state. The full
     * nearby text is reduced to a short plain-text summary (140 chars) so the protocol never
     * transmits the whole rich-text document.
     */
    fun publishSnapshot() {
        val id = appState.selectedScriptId
        if (id.isBlank()) return
        revision += 1
        val script = appState.script(id)
        val snapshot = RemoteSnapshotFactory.fromPlaybackState(
            revision = revision,
            surface = appState.prompterSurface,
            scriptId = if (id == "new") null else id,
            scriptTitle = script.title.ifBlank { null },
            estimatedDurationSeconds = appState.normalEstimatedDurationSeconds(id),
            playbackState = appState.playbackState,
            session = appState.playbackSession,
            speedMultiplier = appState.playbackSettings.speedMultiplier,
            nearbyText = script.plainTextPreview.take(140),
        )
        repository.updatePrompterSnapshot(snapshot)
    }

    fun reset() {
        revision = 0L
    }
}
