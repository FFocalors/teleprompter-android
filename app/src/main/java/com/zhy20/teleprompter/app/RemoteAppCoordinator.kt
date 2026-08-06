package com.zhy20.teleprompter.app

import com.zhy20.teleprompter.core.model.PlaybackEvent
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.protocol.RemoteRejectReason
import com.zhy20.teleprompter.remote.session.RemoteCommandResultState
import com.zhy20.teleprompter.remote.session.RemoteSessionEffect
import com.zhy20.teleprompter.remote.session.RemoteSessionRepository
import com.zhy20.teleprompter.remote.session.RemoteSnapshotFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Application-side coordinator that turns [RemoteSessionEffect]s into real business calls on
 * [AppState], and reports each result back to the repository (which sends the CommandResult
 * and its resulting snapshot revision to the controller).
 *
 * The coordinator validates every command against the current playback state before acting —
 * it never force-mutates the page to "satisfy" a command. StartPlayback additionally flows
 * through [startPlaybackHandler] so the visible Setup page can flush its settings, call
 * [AppState.beginPlayback] and navigate before the app confirms; the coordinator only
 * records a success result after that handler reports true.
 */
class RemoteAppCoordinator(
    private val appState: AppState,
    private val repository: RemoteSessionRepository,
    private val scope: CoroutineScope,
    /**
     * Handles a controller-issued start-playback request for a scriptId. The visible Setup
     * page registers itself and flushes before confirming; it returns true only if the app
     * actually moved to the prompter route (beginPlayback + navigate).
     */
    private val startPlaybackHandler: RemoteStartPlaybackHandler,
) {
    /** Monotonic snapshot revision for this session; persists across command handling. */
    private var revision = 0L

    init {
        repository.sessionEffects
            .onEach { effect ->
                when (effect) {
                    is RemoteSessionEffect.ExecuteCommand -> scope.launch { execute(effect.command) }
                }
            }
            .launchIn(scope)
    }

    private suspend fun execute(command: RemoteCommand) {
        val result: RemoteCommandResultState? = when (command) {
            is RemoteCommand.StartPlayback -> executeStartPlayback(command)
            is RemoteCommand.PausePlayback -> {
                if (appState.playbackState == PlaybackState.Playing) {
                    appState.onPlaybackEvent(PlaybackEvent.PausePlayback)
                    null
                } else {
                    RemoteCommandResultState(command.commandId, false, RemoteRejectReason.CommandNotAllowedInState)
                }
            }
            is RemoteCommand.ResumeImmediately -> {
                if (appState.playbackState == PlaybackState.Paused) {
                    appState.onPlaybackEvent(PlaybackEvent.ResumeImmediately)
                    null
                } else {
                    RemoteCommandResultState(command.commandId, false, RemoteRejectReason.CommandNotAllowedInState)
                }
            }
            is RemoteCommand.ResumeWithCountdown -> {
                if (appState.playbackState == PlaybackState.Paused) {
                    appState.onPlaybackEvent(PlaybackEvent.ResumeWithCountdown)
                    null
                } else {
                    RemoteCommandResultState(command.commandId, false, RemoteRejectReason.CommandNotAllowedInState)
                }
            }
            is RemoteCommand.SeekBy -> {
                if (appState.playbackState == PlaybackState.Playing || appState.playbackState == PlaybackState.Paused) {
                    val clamped = command.delta.coerceIn(-1f, 1f)
                    appState.onPlaybackEvent(PlaybackEvent.SeekTo((appState.progress + clamped).coerceIn(0f, 1f)))
                    null
                } else {
                    RemoteCommandResultState(command.commandId, false, RemoteRejectReason.CommandNotAllowedInState)
                }
            }
            is RemoteCommand.ChangeSpeed -> {
                if (appState.playbackState == PlaybackState.Playing || appState.playbackState == PlaybackState.Paused) {
                    if (command.delta > 0f) appState.onPlaybackEvent(PlaybackEvent.IncreaseSpeed)
                    else if (command.delta < 0f) appState.onPlaybackEvent(PlaybackEvent.DecreaseSpeed)
                    null
                } else {
                    RemoteCommandResultState(command.commandId, false, RemoteRejectReason.CommandNotAllowedInState)
                }
            }
            is RemoteCommand.EndPlayback -> {
                when (appState.playbackState) {
                    is PlaybackState.Countdown,
                    PlaybackState.Playing,
                    PlaybackState.Paused,
                    -> {
                        appState.onPlaybackEvent(PlaybackEvent.EndPlayback)
                        null
                    }
                    else -> RemoteCommandResultState(command.commandId, false, RemoteRejectReason.CommandNotAllowedInState)
                }
            }
        }

        if (result != null) {
            repository.recordResult(result.commandId, result)
            return
        }

        val newRevision = publishSnapshot()
        repository.recordResult(
            command.commandId,
            RemoteCommandResultState(
                commandId = command.commandId,
                success = true,
                resultingSnapshotRevision = newRevision,
            ),
        )
    }

    private suspend fun executeStartPlayback(command: RemoteCommand.StartPlayback): RemoteCommandResultState? {
        // Must be on the Setup page for the requested script.
        if (appState.prompterSurface != PrompterSurface.Setup) {
            return RemoteCommandResultState(command.commandId, false, RemoteRejectReason.CommandNotAllowedInState)
        }
        if (appState.selectedScriptId != command.scriptId) {
            return RemoteCommandResultState(command.commandId, false, RemoteRejectReason.ScriptNotFound)
        }
        if (appState.script(command.scriptId).content.blocks.isEmpty()) {
            return RemoteCommandResultState(command.commandId, false, RemoteRejectReason.ScriptNotFound)
        }
        // Delegate to the visible Setup page: flush settings, then navigate. If it reports
        // true, the app is now on the prompter route and the command succeeded.
        val started = startPlaybackHandler.requestStart(command.scriptId)
        if (!started) {
            return RemoteCommandResultState(command.commandId, false, RemoteRejectReason.SetupSaveFailed)
        }
        return null // success path records below
    }

    /** Called after a navigation effect has moved the app to the prompter page. */
    fun markPrompterSurface(scriptId: String) {
        appState.selectScript(scriptId)
        appState.setSurface(PrompterSurface.Prompter)
        publishSnapshot()
    }

    /**
     * Builds and publishes an immutable snapshot from the current real app state, returning
     * its revision. The nearby text is the real guide-line window reported by the playback
     * page (never a static plain-text preview); it is capped to a short plain-text summary so
     * the protocol never transmits the whole rich-text document.
     */
    fun publishSnapshot(): Long {
        val id = appState.selectedScriptId
        if (id.isBlank()) return revision
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
            nearbyText = appState.playbackNearbyText?.take(220),
        )
        repository.updatePrompterSnapshot(snapshot)
        return revision
    }

    fun reset() {
        revision = 0L
    }
}
