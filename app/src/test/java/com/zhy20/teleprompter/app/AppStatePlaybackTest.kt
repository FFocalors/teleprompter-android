package com.zhy20.teleprompter.app

import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.PlaybackTextAlignment
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStatePlaybackTest {
    @Test
    fun startPlayback_keepsTheSavedOrientationAndTextAlignment() {
        val state = AppState()
        state.selectScript("1")
        state.updatePlaybackSettings(
            state.playbackSettings.copy(
                orientation = PlaybackOrientation.Portrait,
                textAlignment = PlaybackTextAlignment.End,
            ),
        )

        state.beginPlayback("1")

        assertEquals(PlaybackOrientation.Portrait, state.playbackSettings.orientation)
        assertEquals(PlaybackTextAlignment.End, state.playbackSettings.textAlignment)
    }
}
