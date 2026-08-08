package com.zhy20.teleprompter.remote.model

/**
 * Mirror of the real playback engine state, carried inside a snapshot. This is the playback
 * contract exposed to a controller and never drives playback on the prompter side.
 */
data class RemotePlaybackState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isCountdown: Boolean = false,
    val isFinished: Boolean = false,
    val countdownSecondsRemaining: Int? = null,
)
