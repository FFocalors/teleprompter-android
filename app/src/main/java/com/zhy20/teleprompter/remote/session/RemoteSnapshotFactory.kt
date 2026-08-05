package com.zhy20.teleprompter.remote.session

import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.core.util.PlaybackEngineState
import com.zhy20.teleprompter.remote.model.RemotePlaybackState
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemotePrompterSurface
import com.zhy20.teleprompter.remote.model.remotePrompterSnapshot

/**
 * Builds an immutable [RemotePrompterSnapshot] from the real app/playback state. It is the
 * only mapping from the app-internal world into the remote protocol model and never mutates
 * the playback engine.
 */
object RemoteSnapshotFactory {

    fun fromPlaybackState(
        revision: Long,
        surface: PrompterSurface,
        scriptId: String?,
        scriptTitle: String?,
        estimatedDurationSeconds: Int?,
        playbackState: PlaybackState,
        session: PlaybackEngineState,
        speedMultiplier: Float,
        nearbyText: String?,
    ): RemotePrompterSnapshot {
        val remoteSurface = surface.toRemoteSurface(playbackState)
        val countdownSeconds = (playbackState as? PlaybackState.Countdown)?.secondsRemaining
        return remotePrompterSnapshot(
            revision = revision,
            surface = remoteSurface,
            scriptId = scriptId,
            scriptTitle = scriptTitle,
            estimatedDurationSeconds = estimatedDurationSeconds,
            playbackState = RemotePlaybackState(
                isPlaying = playbackState == PlaybackState.Playing,
                isPaused = playbackState == PlaybackState.Paused,
                isCountdown = playbackState is PlaybackState.Countdown,
                isFinished = playbackState == PlaybackState.Finished,
                countdownSecondsRemaining = countdownSeconds,
            ),
            progress = session.currentSemanticProgress,
            elapsedTimeMillis = session.elapsedTimeMillis,
            remainingTimeMillis = session.remainingTimeMillis,
            speedMultiplier = speedMultiplier,
            countdownSecondsRemaining = countdownSeconds,
            nearbyText = nearbyText,
        )
    }
}

/**
 * Maps the app surface + playback state into the remote surface. The prompter page wins over
 * the declared app surface so a controller sees the true playback surface while playing.
 */
private fun PrompterSurface.toRemoteSurface(playbackState: PlaybackState): RemotePrompterSurface = when {
    this == PrompterSurface.Prompter -> when (playbackState) {
        is PlaybackState.Countdown -> RemotePrompterSurface.Countdown
        PlaybackState.Playing -> RemotePrompterSurface.Playing
        PlaybackState.Paused -> RemotePrompterSurface.Paused
        PlaybackState.Finished, PlaybackState.Exited -> RemotePrompterSurface.Finished
        else -> RemotePrompterSurface.Playing
    }
    this == PrompterSurface.Setup -> RemotePrompterSurface.Setup
    this == PrompterSurface.Editor -> RemotePrompterSurface.Editor
    else -> RemotePrompterSurface.Library
}
