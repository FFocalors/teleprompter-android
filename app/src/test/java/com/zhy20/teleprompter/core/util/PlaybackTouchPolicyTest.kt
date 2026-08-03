package com.zhy20.teleprompter.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTouchPolicyTest {
    @Test
    fun centralTap_isAllowedWhilePlayingAndControlsHidden() {
        assertTrue(
            PlaybackTouchPolicy.allowsPlaybackGesture(
                widthPx = 1_080f,
                heightPx = 2_200f,
                density = 3f,
                x = 540f,
                y = 1_100f,
                isPlaying = true,
                controlsVisible = false,
            ),
        )
    }

    @Test
    fun edgeTapDoubleTapAndDragStart_areRejected() {
        val policy = { x: Float, y: Float ->
            PlaybackTouchPolicy.allowsPlaybackGesture(1_080f, 2_200f, 3f, x, y, true, false)
        }

        assertFalse(policy(12f, 1_100f))
        assertFalse(policy(540f, 18f))
        assertFalse(policy(1_060f, 1_100f))
    }

    @Test
    fun pausedOrVisibleControls_restoreFullContentToControlsInsteadOfRootGestures() {
        assertFalse(PlaybackTouchPolicy.allowsPlaybackGesture(1_080f, 2_200f, 3f, 540f, 1_100f, false, false))
        assertFalse(PlaybackTouchPolicy.allowsPlaybackGesture(1_080f, 2_200f, 3f, 540f, 1_100f, true, true))
    }
}
