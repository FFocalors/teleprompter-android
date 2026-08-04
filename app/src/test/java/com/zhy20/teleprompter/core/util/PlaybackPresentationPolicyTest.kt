package com.zhy20.teleprompter.core.util

import android.content.pm.ActivityInfo
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPresentationPolicyTest {
    @Test
    fun mirrorOnlyTransformsTheScriptLayer() {
        assertEquals(-1f, PlaybackMirrorPolicy.scaleX(true, PlaybackVisualLayer.ScriptContent))
        assertEquals(1f, PlaybackMirrorPolicy.scaleX(true, PlaybackVisualLayer.Status))
        assertEquals(1f, PlaybackMirrorPolicy.scaleX(true, PlaybackVisualLayer.Guide))
        assertEquals(1f, PlaybackMirrorPolicy.scaleX(true, PlaybackVisualLayer.Controls))
        assertEquals(1f, PlaybackMirrorPolicy.scaleX(false, PlaybackVisualLayer.ScriptContent))
    }

    @Test
    fun orientationMapsToAnActualLockedActivityRequest() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, PlaybackOrientation.Portrait.requestedActivityOrientation())
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, PlaybackOrientation.Landscape.requestedActivityOrientation())
    }
}
