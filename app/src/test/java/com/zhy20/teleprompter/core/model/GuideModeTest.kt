package com.zhy20.teleprompter.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideModeTest {
    @Test
    fun everyModeProducesOneMutuallyExclusiveVisualState() {
        assertEquals(GuideVisualState(false, false), GuideMode.Off.visualState())
        assertEquals(GuideVisualState(true, false), GuideMode.Line.visualState())
        assertEquals(GuideVisualState(false, true), GuideMode.HighlightBar.visualState())
        GuideMode.entries.forEach { mode ->
            val visual = mode.visualState()
            assertFalse(visual.lineVisible && visual.highlightBarVisible)
        }
    }

    @Test
    fun legacyFlagsConvertOnceAtTheBoundary() {
        assertEquals(GuideMode.Off, guideModeFromLegacy(enabled = false, highlighted = true))
        assertEquals(GuideMode.Line, guideModeFromLegacy(enabled = true, highlighted = false))
        assertEquals(GuideMode.HighlightBar, guideModeFromLegacy(enabled = true, highlighted = true))
        assertTrue(PlaybackSettings().guideMode.visualState().highlightBarVisible)
    }
}
