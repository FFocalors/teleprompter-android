package com.zhy20.teleprompter.app

import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.GuideMode
import com.zhy20.teleprompter.core.model.PlaybackEvent
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.PlaybackTextAlignment
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.zhy20.teleprompter.data.fake.FakeData

class AppStatePlaybackTest {
    @Test
    fun contentChangeRefreshesTheCachedAndDisplayedNormalDuration() {
        val state = testState()
        val content = ScriptContent(
            listOf(ScriptBlock.Paragraph("p", listOf(ScriptSpan("字".repeat(255))))),
        )

        state.updateScript("1", "更新后的台本", content)

        assertEquals(60, state.script("1").normalEstimatedDurationSeconds)
        assertEquals(60, state.normalEstimatedDurationSeconds("1"))
    }

    @Test
    fun startPlayback_keepsTheSavedOrientationAndTextAlignment() {
        val state = testState()
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

    @Test
    fun playbackStartsWithTheFirstLineAtTheBottomAndKeepsGuideAndOrientationSettings() {
        var now = 0L
        val state = testState { now }
        state.selectScript("1")
        state.updatePlaybackSettings(
            state.playbackSettings.copy(
                countdown = CountdownOption.Off,
                orientation = PlaybackOrientation.Portrait,
                guideMode = GuideMode.Line,
            ),
        )

        state.beginPlayback("1")
        state.updatePlaybackLayout(viewportHeightPx = 1_000f, textHeightPx = 2_000f)

        assertEquals(0f, state.progress, 0f)
        assertEquals(820f, state.playbackSession.currentScrollOffset, 0.01f)
        assertEquals(PlaybackState.Playing, state.playbackState)
        assertEquals(PlaybackOrientation.Portrait, state.playbackSettings.orientation)
        assertEquals(GuideMode.Line, state.playbackSettings.guideMode)

        now = 20_000_000_000L
        state.onPlaybackFrame(now)
        val beforePause = state.progress
        state.onPlaybackEvent(PlaybackEvent.PausePlayback)
        now = 80_000_000_000L
        state.onPlaybackFrame(now)
        assertEquals(beforePause, state.progress, 0f)
    }

    @Test
    fun remoteStartAndFreshCountdownBothResetAnyPreviousPosition() {
        var now = 0L
        val state = testState { now }
        state.selectScript("4")
        state.updatePlaybackSettings(
            state.playbackSettings.copy(countdown = CountdownOption.ThreeSeconds),
        )
        state.beginPlayback("4")
        state.updatePlaybackLayout(viewportHeightPx = 1_000f, textHeightPx = 4_000f)
        state.onPlaybackEvent(PlaybackEvent.SeekTo(.6f))

        state.onPlaybackEvent(PlaybackEvent.StartPlayback)
        state.updatePlaybackLayout(viewportHeightPx = 1_000f, textHeightPx = 4_000f)

        assertEquals(PlaybackState.Countdown(3), state.playbackState)
        assertTrue(state.playbackSession.isStartingFromBeginning)
        assertEquals(0f, state.progress, 0f)
        assertEquals(820f, state.playbackSession.currentScrollOffset, 0.01f)

        state.finishCountdown()

        assertEquals(PlaybackState.Playing, state.playbackState)
        assertEquals(0f, state.progress, 0f)
        assertEquals(820f, state.playbackSession.currentScrollOffset, 0.01f)
    }

    @Test
    fun resumeCountdownKeepsThePausedPositionInsteadOfBecomingANewStart() {
        var now = 0L
        val state = testState { now }
        state.selectScript("4")
        state.updatePlaybackSettings(state.playbackSettings.copy(countdown = CountdownOption.Off))
        state.beginPlayback("4")
        state.updatePlaybackLayout(viewportHeightPx = 1_000f, textHeightPx = 4_000f)
        now = 20_000_000_000L
        state.onPlaybackFrame(now)
        state.onPlaybackEvent(PlaybackEvent.PausePlayback)
        val pausedProgress = state.progress

        state.onPlaybackEvent(PlaybackEvent.ResumeWithCountdown)
        assertFalse(state.playbackSession.isStartingFromBeginning)
        state.finishCountdown()

        assertEquals(pausedProgress, state.progress, 0f)
        assertEquals(PlaybackState.Playing, state.playbackState)
    }

    @Test
    fun resumeCountdownCanBeCancelledWithoutMovingThePausedPosition() {
        val state = testState()
        state.selectScript("4")
        state.updatePlaybackSettings(state.playbackSettings.copy(countdown = CountdownOption.Off))
        state.beginPlayback("4")
        state.updatePlaybackLayout(viewportHeightPx = 1_000f, textHeightPx = 4_000f)
        state.onPlaybackEvent(PlaybackEvent.SeekTo(.6f))
        state.onPlaybackEvent(PlaybackEvent.PausePlayback)
        val pausedProgress = state.progress

        state.onPlaybackEvent(PlaybackEvent.ResumeWithCountdown)
        state.onPlaybackEvent(PlaybackEvent.CancelResumeCountdown)

        assertEquals(PlaybackState.Paused, state.playbackState)
        assertEquals(pausedProgress, state.progress, 0f)
    }

    @Test
    fun finishedPlaybackCanBeMovedBackWithManualProgress() {
        val state = testState()
        state.selectScript("4")
        state.updatePlaybackSettings(state.playbackSettings.copy(countdown = CountdownOption.Off))
        state.beginPlayback("4")
        state.updatePlaybackLayout(viewportHeightPx = 1_000f, textHeightPx = 4_000f)
        state.onPlaybackEvent(PlaybackEvent.SeekTo(1f))
        state.onPlaybackEvent(PlaybackEvent.EndPlayback)

        state.beginManualProgressAdjustment()
        state.onPlaybackEvent(PlaybackEvent.SeekTo(.72f))
        state.endManualProgressAdjustment()

        assertEquals(PlaybackState.Finished, state.playbackState)
        assertEquals(.72f, state.progress, 0f)
    }
}

private fun testState(clock: () -> Long = System::nanoTime): AppState = AppState(
    clockNanos = clock,
    initialScripts = FakeData.scripts,
    initialFolders = FakeData.folders,
    initialDefaults = FakeData.defaultPlaybackSettings,
)
