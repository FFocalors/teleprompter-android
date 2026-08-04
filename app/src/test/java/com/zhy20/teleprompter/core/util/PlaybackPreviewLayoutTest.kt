package com.zhy20.teleprompter.core.util

import com.zhy20.teleprompter.core.model.PlaybackOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPreviewLayoutTest {
    @Test
    fun previewAspectRatio_matchesTheRequestedOrientation() {
        assertEquals(9f / 16f, PlaybackPreviewLayout.aspectRatio(PlaybackOrientation.Portrait))
        assertEquals(16f / 9f, PlaybackPreviewLayout.aspectRatio(PlaybackOrientation.Landscape))
    }

    @Test
    fun visibleLineCount_leavesRoomForShortAndLongPreviewContent() {
        assertEquals(3, PlaybackPreviewLayout.maxVisibleLines(28f, 100))
        assertTrue(PlaybackPreviewLayout.maxVisibleLines(640f, 48) in 3..12)
        assertEquals(12, PlaybackPreviewLayout.maxVisibleLines(2_000f, 32))
    }

    @Test
    fun targetViewportMatchesTheSelectedPlaybackOrientationAndBreakpoint() {
        val portrait = PlaybackPreviewLayout.targetViewport(393, 854, PlaybackOrientation.Portrait)
        val landscape = PlaybackPreviewLayout.targetViewport(393, 854, PlaybackOrientation.Landscape)
        val tabletPortrait = PlaybackPreviewLayout.targetViewport(1_280, 800, PlaybackOrientation.Portrait)

        assertEquals(393f, portrait.widthDp, 0f)
        assertEquals(854f, landscape.widthDp, 0f)
        assertFalse(portrait.usesLargeLayout)
        assertTrue(landscape.usesLargeLayout)
        assertTrue(tabletPortrait.usesLargeLayout)
    }
}
