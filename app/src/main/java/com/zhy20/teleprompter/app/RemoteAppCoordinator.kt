package com.zhy20.teleprompter.app

import com.zhy20.teleprompter.core.model.PlaybackEvent
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.protocol.RemoteMessage
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

    /** Outgoing reading-channel dedup so a window is sent once and identical cursors are skipped. */
    private var lastSentWindowTextRevision: Long? = null
    private var lastSentWindowRevision: Long? = null
    private var lastSentCursorOffset: Double? = null

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
     * its revision.
     *
     * The legacy [RemotePrompterSnapshot.nearbyText]/[RemotePrompterSnapshot.readingText]
     * fields are deliberately left unpopulated: the controller's current-reading text now
     * comes exclusively from [pushReadingState] (absolute reading window + cursor).
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
            nearbyText = null,
            readingText = null,
        )
        repository.updatePrompterSnapshot(snapshot)
        return revision
    }

    /**
     * Pushes the latest reading window + cursor to a connected controller. The window is sent
     * only when it actually changed; the cursor is latest-only (identical values are skipped and
     * the repository throttles ordinary motion to ~12–20 Hz while seek jumps pass immediately).
     *
     * @param force when true (e.g. right after a (re)connect) the current window and cursor are
     *   re-sent even if unchanged locally, so the controller never depends on pre-drop cache.
     */
    fun pushReadingState(force: Boolean = false) {
        val window = appState.playbackReadingWindow
        val windowChanged = force || window != null &&
            (window.textRevision != lastSentWindowTextRevision || window.revision != lastSentWindowRevision)
        if (window != null && windowChanged) {
            lastSentWindowTextRevision = window.textRevision
            lastSentWindowRevision = window.revision
            repository.updateReadingWindow(
                RemoteMessage.ReadingWindowUpdate(
                    textRevision = window.textRevision,
                    windowRevision = window.revision,
                    startOffset = window.startOffset,
                    endOffset = window.endOffset,
                    text = window.text,
                ),
            )
        }
        val cursor = appState.playbackReadingCursor
        if (cursor != null && (force || cursorChanged(cursor.absoluteOffset))) {
            lastSentCursorOffset = cursor.absoluteOffset
            repository.updateReadingCursor(
                RemoteMessage.ReadingCursorUpdate(
                    textRevision = cursor.textRevision,
                    absoluteOffset = cursor.absoluteOffset,
                    sequence = 0L, // stamped by the repository
                    sentAtElapsedRealtimeMillis = 0L, // stamped by the repository
                ),
            )
        }
    }

    private fun cursorChanged(offset: Double): Boolean {
        val prev = lastSentCursorOffset ?: return true
        return kotlin.math.abs(offset - prev) >= 1e-4
    }

    fun reset() {
        revision = 0L
        lastSentWindowTextRevision = null
        lastSentWindowRevision = null
        lastSentCursorOffset = null
    }
}
