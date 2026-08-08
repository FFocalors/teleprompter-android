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
    fun playbackStartsWithAtTheGuideAnchorAndKeepsGuideAndOrientationSettings() {
        var now = 0L
        val state = testState { now }
        state.selectScript("1")
        state.updatePlaybackSettings(
            state.playbackSettings.copy(
                countdown = CountdownOption.Off,
                orientation = PlaybackOrientation.Portrait,
                guideMode = GuideMode.Line,
                guideLinePosition = 0.25f,
            ),
        )

        state.beginPlayback("1")
        state.updatePlaybackLayout(viewportHeightPx = 1_000f, textHeightPx = 2_000f)

        // First line starts at the guide anchor (25% of viewport) + 1.5 visual lines below;
        // line height is not measured in this unit test so the offset is the anchor Y alone.
        assertEquals(0f, state.progress, 0f)
        assertEquals(250f, state.playbackSession.currentScrollOffset, 0.01f)
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
        // Script "4" has guide off, so the fresh-start anchor is viewport top at 25%.
        assertEquals(250f, state.playbackSession.currentScrollOffset, 0.01f)

        state.finishCountdown()

        assertEquals(PlaybackState.Playing, state.playbackState)
        assertEquals(0f, state.progress, 0f)
        assertEquals(250f, state.playbackSession.currentScrollOffset, 0.01f)
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

    @Test
    fun guideOverlayChangesDuringPlaybackDoNotMoveTheTextOrResetTiming() {
        var now = 0L
        val state = testState { now }
        state.selectScript("4")
        state.updatePlaybackSettings(
            state.playbackSettings.copy(
                countdown = CountdownOption.Off,
                guideMode = GuideMode.Line,
                guideLinePosition = 0.25f,
            ),
        )
        state.beginPlayback("4")
        state.updatePlaybackLayout(viewportHeightPx = 1_000f, textHeightPx = 4_000f)
        now = 10_000_000_000L
        state.onPlaybackFrame(now)
        val progressBefore = state.progress
        val offsetBefore = state.playbackSession.currentScrollOffset
        val elapsedBefore = state.playbackSession.elapsedTimeMillis
        val remainingBefore = state.playbackSession.remainingTimeMillis
        val cursorAnchorBefore = state.playbackSession.readingCursorAnchorFraction

        // Playing: move the guide line and toggle it off/on — must not touch the session.
        state.updateGuideOverlay(position = 0.7f)
        assertEquals(cursorAnchorBefore, state.playbackSession.readingCursorAnchorFraction)
        assertEquals(progressBefore, state.progress, 0f)
        assertEquals(offsetBefore, state.playbackSession.currentScrollOffset, 0.01f)
        state.onPlaybackEvent(PlaybackEvent.ChangeGuideMode(GuideMode.Off))
        assertEquals(progressBefore, state.progress, 0f)
        assertEquals(offsetBefore, state.playbackSession.currentScrollOffset, 0.01f)
        state.onPlaybackEvent(PlaybackEvent.ChangeGuideMode(GuideMode.HighlightBar))
        assertEquals(progressBefore, state.progress, 0f)
        assertEquals(offsetBefore, state.playbackSession.currentScrollOffset, 0.01f)

        // Paused: same guarantees.
        state.onPlaybackEvent(PlaybackEvent.PausePlayback)
        now = 20_000_000_000L
        state.onPlaybackFrame(now)
        state.updateGuideOverlay(position = 0.5f, mode = GuideMode.Line)
        assertEquals(PlaybackState.Paused, state.playbackState)
        assertEquals(progressBefore, state.progress, 0f)
        assertEquals(offsetBefore, state.playbackSession.currentScrollOffset, 0.01f)
        assertEquals(elapsedBefore, state.playbackSession.elapsedTimeMillis)
        assertEquals(remainingBefore, state.playbackSession.remainingTimeMillis)
        assertEquals(0.5f, state.playbackSession.readingCursorAnchorFraction!!, 0f)
        assertEquals(state.playbackSettings.guideMode, GuideMode.Line)
        assertEquals(0.5f, state.playbackSettings.guideLinePosition, 0f)

        state.onPlaybackEvent(PlaybackEvent.ResumeImmediately)
        assertEquals(0.5f, state.playbackSession.readingCursorAnchorFraction!!, 0f)
    }

    @Test
    fun guideOffStartUsesTopQuarterAnchorAndGuideOnUsesGuideAnchor() {
        val off = testState()
        off.selectScript("4")
        off.updatePlaybackSettings(
            off.playbackSettings.copy(countdown = CountdownOption.Off, guideMode = GuideMode.Off),
        )
        off.beginPlayback("4")
        off.updatePlaybackLayout(viewportHeightPx = 1_000f, textHeightPx = 4_000f)
        assertEquals(250f, off.playbackSession.currentScrollOffset, 0.01f)

        val on = testState()
        on.selectScript("4")
        on.updatePlaybackSettings(
            on.playbackSettings.copy(countdown = CountdownOption.Off, guideMode = GuideMode.Line, guideLinePosition = 0.5f),
        )
        on.beginPlayback("4")
        on.updatePlaybackLayout(viewportHeightPx = 1_000f, textHeightPx = 4_000f)
        // Anchor Y at 50% of the viewport; line height is not measured here, so no +1.5 offset.
        assertEquals(500f, on.playbackSession.currentScrollOffset, 0.01f)
    }

    @Test
    fun guideOverlayChangesNeverMoveTheCapturedReadingAnchor() {
        // Rendered text geometry is computed from the immutable session readingAnchor, so moving
        // or toggling the visual guide line during playback must not change it.
        var now = 0L
        val state = testState { now }
        state.selectScript("4")
        state.updatePlaybackSettings(
            state.playbackSettings.copy(
                countdown = CountdownOption.Off,
                guideMode = GuideMode.Line,
                guideLinePosition = 0.3f,
            ),
        )
        state.beginPlayback("4")
        state.updatePlaybackLayout(viewportHeightPx = 1_000f, textHeightPx = 4_000f)
        val anchor = state.playbackSession.readingAnchor
        assertTrue(anchor != null)
        assertEquals(0.3f, anchor!!.viewportFraction, 0f)

        now = 5_000_000_000L
        state.onPlaybackFrame(now)
        state.updateGuideOverlay(position = 0.7f)
        state.onPlaybackEvent(PlaybackEvent.ChangeGuideMode(GuideMode.Off))
        state.onPlaybackEvent(PlaybackEvent.ChangeGuideMode(GuideMode.HighlightBar))
        state.onPlaybackEvent(PlaybackEvent.ChangeGuideMode(GuideMode.Line))
        state.updateGuideOverlay(position = 0.2f)

        // The anchor captured at session start is untouched.
        assertEquals(anchor, state.playbackSession.readingAnchor)
        // And the visible guide settings changed as expected (visual layer only).
        assertEquals(0.2f, state.playbackSettings.guideLinePosition, 0f)
    }
}

private fun testState(clock: () -> Long = System::nanoTime): AppState = AppState(
    clockNanos = clock,
    initialScripts = FakeData.scripts,
    initialFolders = FakeData.folders,
    initialDefaults = FakeData.defaultPlaybackSettings,
)
