package com.zhy20.teleprompter.feature.prompter

import com.zhy20.teleprompter.core.model.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackControlBarModeTest {
    @Test
    fun pausedControlsCanHideAndShowWithoutChangingPlaybackState() {
        assertEquals(
            ControlBarMode.Paused,
            controlBarModeFor(PlaybackState.Paused, controlsVisible = true),
        )
        assertEquals(
            ControlBarMode.Hidden,
            controlBarModeFor(PlaybackState.Paused, controlsVisible = false),
        )
    }

    @Test
    fun countdownAlwaysUsesTheFullScreenOverlayInsteadOfTheControlBar() {
        assertEquals(
            ControlBarMode.Hidden,
            controlBarModeFor(PlaybackState.Countdown(3), controlsVisible = true),
        )
    }

    @Test
    fun finishedPlaybackKeepsControlsAvailableForPositionAdjustment() {
        assertEquals(
            ControlBarMode.Finished,
            controlBarModeFor(PlaybackState.Finished, controlsVisible = true),
        )
    }
}
