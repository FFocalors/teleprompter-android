package com.zhy20.teleprompter.core.util

import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.RhythmMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEngineTest {
    @Test
    fun playbackClockWaitsForTheFirstValidLayoutMeasurement() {
        val settings = PlaybackSettings(countdown = CountdownOption.Off)
        val prepared = PlaybackEngine.prepare(settings, normalDurationSeconds = 100)
        val playing = PlaybackEngine.setPlaybackState(prepared, PlaybackState.Playing, 0L)
        val beforeLayout = PlaybackEngine.tick(playing, seconds(30))

        assertEquals(0L, beforeLayout.elapsedTimeMillis)
        assertEquals(0f, beforeLayout.currentSemanticProgress, 0f)
    }

    @Test
    fun layoutPlacesTheFirstLineNearTheBottomAndEndsAtTheLowerThird() {
        val short = PlaybackLayoutCalculator.calculate(viewportHeightPx = 1_000f, textHeightPx = 420f)
        val exact = PlaybackLayoutCalculator.calculate(viewportHeightPx = 1_000f, textHeightPx = 1_000f)
        val long = PlaybackLayoutCalculator.calculate(viewportHeightPx = 1_000f, textHeightPx = 1_001f)

        assertTrue(short.requiresScrolling)
        assertEquals(820f, short.startOffsetPx, 0.01f)
        assertEquals(250f, short.endOffsetPx, 0.01f)
        assertTrue(exact.requiresScrolling)
        assertTrue(long.requiresScrolling)
        assertEquals(820f, long.startOffsetPx, 0.01f)
        assertEquals(670f, long.endOffsetPx + long.textHeightPx, 0.01f)
    }

    @Test
    fun shortScriptAlsoMovesUpwardAndFinishes() {
        val state = playingState(textHeight = 400f)
        val advanced = PlaybackEngine.tick(state, seconds(500))

        assertTrue(advanced.requiresScrolling)
        assertEquals(PlaybackState.Finished, advanced.playbackState)
        assertEquals(1f, advanced.currentSemanticProgress, 0f)
        assertEquals(500_000L, advanced.elapsedTimeMillis)
        assertFalse(advanced.showAutomaticProgress)
    }

    @Test
    fun automaticProgressOnlyAppearsDuringRealUninterruptedScrolling() {
        val playing = playingState(textHeight = 2_000f)
        val paused = PlaybackEngine.setPlaybackState(playing, PlaybackState.Paused, 0L)
        val adjusting = PlaybackEngine.beginManualAdjustment(playing, 0L)
        val finished = PlaybackEngine.tick(playing, seconds(150))

        assertTrue(playing.showAutomaticProgress)
        assertFalse(paused.showAutomaticProgress)
        assertFalse(adjusting.showAutomaticProgress)
        assertFalse(finished.showAutomaticProgress)
    }

    @Test
    fun positionIsTimeBasedAndIndependentOfFrameCount() {
        val initial = playingState(textHeight = 2_000f)
        val oneFrame = PlaybackEngine.tick(initial, seconds(50))
        val manyFrames = PlaybackEngine.tick(
            PlaybackEngine.tick(
                PlaybackEngine.tick(initial, seconds(10)),
                seconds(25),
            ),
            seconds(50),
        )

        assertEquals(oneFrame.currentSemanticProgress, manyFrames.currentSemanticProgress, 0.0001f)
        assertEquals(oneFrame.currentScrollOffset, manyFrames.currentScrollOffset, 0.01f)
        assertEquals(0.5f, oneFrame.currentSemanticProgress, 0.0001f)
    }

    @Test
    fun pauseResumeAndSpeedChangeKeepPositionContinuous() {
        val initial = playingState(textHeight = 2_000f)
        val atTwenty = PlaybackEngine.tick(initial, seconds(20))
        val paused = PlaybackEngine.setPlaybackState(atTwenty, PlaybackState.Paused, seconds(20))
        val stillPaused = PlaybackEngine.tick(paused, seconds(80))
        val resumed = PlaybackEngine.setPlaybackState(stillPaused, PlaybackState.Playing, seconds(80))
        val atThirtyEffective = PlaybackEngine.tick(resumed, seconds(90))

        assertEquals(atTwenty.currentScrollOffset, stillPaused.currentScrollOffset, 0f)
        assertEquals(0.3f, atThirtyEffective.currentSemanticProgress, 0.0001f)

        val fasterSettings = PlaybackSettings(
            countdown = CountdownOption.Off,
            speedMultiplier = 2f,
            rhythmMode = RhythmMode.Speed,
        )
        val reconfigured = PlaybackEngine.reconfigure(atTwenty, fasterSettings, 100, seconds(20))
        assertEquals(atTwenty.currentScrollOffset, reconfigured.currentScrollOffset, 0f)
        val faster = PlaybackEngine.tick(reconfigured, seconds(30))
        assertEquals(0.4f, faster.currentSemanticProgress, 0.0001f)
    }

    @Test
    fun manualAdjustmentFreezesThenContinuesFromTheNewPosition() {
        val initial = PlaybackEngine.tick(playingState(textHeight = 2_000f), seconds(20))
        val adjusting = PlaybackEngine.beginManualAdjustment(initial, seconds(20))
        val moved = PlaybackEngine.seek(adjusting, 0.6f, seconds(25))
        val frozen = PlaybackEngine.tick(moved, seconds(40))
        val released = PlaybackEngine.endManualAdjustment(frozen, seconds(40))
        val continued = PlaybackEngine.tick(released, seconds(50))

        assertEquals(0.6f, frozen.currentSemanticProgress, 0f)
        assertEquals(0.7f, continued.currentSemanticProgress, 0.0001f)
        assertTrue(continued.currentScrollOffset < frozen.currentScrollOffset)
    }

    @Test
    fun reachingTheEndClampsAndKeepsTheFinalOffset() {
        val initial = playingState(textHeight = 2_000f)
        val finished = PlaybackEngine.tick(initial, seconds(150))
        val later = PlaybackEngine.tick(finished, seconds(300))

        assertEquals(PlaybackState.Finished, finished.playbackState)
        assertEquals(1f, finished.currentSemanticProgress, 0f)
        assertEquals(finished.endOffset, finished.currentScrollOffset, 0f)
        assertEquals(670f, finished.endOffset + 2_000f, 0.01f)
        assertEquals(finished, later)
    }

    @Test
    fun explicitFreshStartSurvivesCountdownButManualSeekDoesNot() {
        val settings = PlaybackSettings(countdown = CountdownOption.ThreeSeconds)
        val prepared = PlaybackEngine.prepare(settings, normalDurationSeconds = 100)
        val countdown = PlaybackEngine.setPlaybackState(prepared, PlaybackState.Countdown(3), 0L)
        val laidOut = PlaybackEngine.updateLayout(countdown, 1_000f, 2_000f, settings, 100, 0L)

        assertTrue(laidOut.isStartingFromBeginning)
        assertEquals(820f, laidOut.currentScrollOffset, 0.01f)

        val manuallyMoved = PlaybackEngine.seek(laidOut, .5f, 0L)

        assertFalse(manuallyMoved.isStartingFromBeginning)
    }

    @Test
    fun invalidMeasurementsAndSpeedNeverCreateNanOrNegativeDistance() {
        val layout = PlaybackLayoutCalculator.calculate(Float.NaN, Float.POSITIVE_INFINITY)
        val settings = PlaybackSettings(speedMultiplier = Float.NaN, countdown = CountdownOption.Off)
        val state = PlaybackEngine.updateLayout(
            PlaybackEngine.setPlaybackState(PlaybackEngine.prepare(settings, 0), PlaybackState.Playing, 0L),
            1_000f,
            2_000f,
            settings,
            0,
            0L,
        )
        val advanced = PlaybackEngine.tick(state, seconds(1))

        assertEquals(0f, layout.totalScrollDistancePx, 0f)
        assertTrue(advanced.currentSemanticProgress.isFinite())
        assertTrue(advanced.currentScrollOffset.isFinite())
        assertTrue(advanced.totalScrollDistance >= 0f)
    }

    private fun playingState(textHeight: Float): PlaybackEngineState {
        val settings = PlaybackSettings(
            countdown = CountdownOption.Off,
            speedMultiplier = 1f,
            rhythmMode = RhythmMode.Speed,
        )
        val prepared = PlaybackEngine.prepare(settings, normalDurationSeconds = 100)
        val playing = PlaybackEngine.setPlaybackState(prepared, PlaybackState.Playing, 0L)
        return PlaybackEngine.updateLayout(playing, 1_000f, textHeight, settings, 100, 0L)
    }

    // ---- reading anchor: live playback initial position ----

    @Test
    fun livePlaybackWithGuideAnchorPlacesFirstLineBelowTheGuide() {
        val settings = PlaybackSettings(countdown = CountdownOption.Off)
        val anchor = PlaybackReadingAnchor(
            viewportFraction = 0.25f,
            initialTextOffsetLines = 1.5f,
            durationMillis = 100_000L,
            normalDurationSeconds = 100,
        )
        val prepared = PlaybackEngine.prepare(settings, 100, readingAnchor = anchor)
        val playing = PlaybackEngine.setPlaybackState(prepared, PlaybackState.Playing, 0L)
        val laidOut = PlaybackEngine.updateLayout(playing, 1_000f, 2_000f, settings, 100, 0L, lineHeightPx = 40f)

        // First line at 25% of the viewport + 1.5 lines (60px).
        assertEquals(310f, laidOut.currentScrollOffset, 0.01f)
        assertEquals(310f, laidOut.startOffset, 0.01f)
        assertEquals(0f, laidOut.currentSemanticProgress, 0f)
    }

    @Test
    fun livePlaybackWithGuideOffPlacesFirstLineAtTopQuarter() {
        val settings = PlaybackSettings(countdown = CountdownOption.Off)
        val anchor = PlaybackReadingAnchor(
            viewportFraction = 0.25f,
            initialTextOffsetLines = 0f,
            durationMillis = 100_000L,
            normalDurationSeconds = 100,
        )
        val prepared = PlaybackEngine.prepare(settings, 100, readingAnchor = anchor)
        val playing = PlaybackEngine.setPlaybackState(prepared, PlaybackState.Playing, 0L)
        val laidOut = PlaybackEngine.updateLayout(playing, 1_000f, 2_000f, settings, 100, 0L, lineHeightPx = 40f)

        assertEquals(250f, laidOut.currentScrollOffset, 0.01f)
        assertEquals(0f, laidOut.currentSemanticProgress, 0f)
    }

    @Test
    fun legacyPreviewLayoutStillUsesBottomEntryRule() {
        // No anchor → the classic 0.82 rule; this is the setup preview behavior.
        val layout = PlaybackLayoutCalculator.calculate(viewportHeightPx = 1_000f, textHeightPx = 2_000f)
        assertEquals(820f, layout.startOffsetPx, 0.01f)
        assertEquals(670f, layout.endOffsetPx + layout.textHeightPx, 0.01f)
    }

    @Test
    fun guideOverlayChangesNeverReanchorTheSession() {
        val settings = PlaybackSettings(countdown = CountdownOption.Off)
        val anchor = PlaybackReadingAnchor(
            viewportFraction = 0.25f,
            initialTextOffsetLines = 1.5f,
            durationMillis = 100_000L,
            normalDurationSeconds = 100,
        )
        val prepared = PlaybackEngine.prepare(settings, 100, readingAnchor = anchor)
        val playing = PlaybackEngine.setPlaybackState(prepared, PlaybackState.Playing, 0L)
        val laidOut = PlaybackEngine.updateLayout(playing, 1_000f, 2_000f, settings, 100, 0L, lineHeightPx = 40f)
        val moved = PlaybackEngine.tick(laidOut, seconds(10))
        val progressBefore = moved.currentSemanticProgress
        val offsetBefore = moved.currentScrollOffset

        // Simulating a guide overlay change: reconfigure with a different guideLinePosition.
        // The anchor is captured, so the layout must NOT change.
        val reconfigured = PlaybackEngine.updateLayout(
            moved, 1_000f, 2_000f,
            settings.copy(guideLinePosition = 0.7f),
            100, seconds(10), lineHeightPx = 40f,
        )

        assertEquals(progressBefore, reconfigured.currentSemanticProgress, 0f)
        assertEquals(offsetBefore, reconfigured.currentScrollOffset, 0.01f)
        assertEquals(laidOut.startOffset, reconfigured.startOffset, 0.01f)
    }

    @Test
    fun readingCursorAnchorCanMoveWithoutChangingPlaybackGeometryOrTiming() {
        val settings = PlaybackSettings(countdown = CountdownOption.Off)
        val anchor = PlaybackReadingAnchor(
            viewportFraction = 0.25f,
            initialTextOffsetLines = 1.5f,
            durationMillis = 100_000L,
            normalDurationSeconds = 100,
        )
        val prepared = PlaybackEngine.prepare(settings, 100, readingAnchor = anchor)
        val playing = PlaybackEngine.setPlaybackState(prepared, PlaybackState.Playing, 0L)
        val laidOut = PlaybackEngine.updateLayout(
            playing,
            1_000f,
            2_000f,
            settings,
            100,
            0L,
            lineHeightPx = 40f,
        )
        val paused = PlaybackEngine.setPlaybackState(
            PlaybackEngine.tick(laidOut, seconds(10)),
            PlaybackState.Paused,
            seconds(10),
        )

        val updated = PlaybackEngine.updateReadingCursorAnchor(paused, 0.7f)

        assertEquals(0.25f, paused.readingCursorAnchorFraction!!, 0f)
        assertEquals(0.7f, updated.readingCursorAnchorFraction!!, 0f)
        assertEquals(paused.readingAnchor, updated.readingAnchor)
        assertEquals(paused.currentSemanticProgress, updated.currentSemanticProgress, 0f)
        assertEquals(paused.currentScrollOffset, updated.currentScrollOffset, 0f)
        assertEquals(paused.startOffset, updated.startOffset, 0f)
        assertEquals(paused.endOffset, updated.endOffset, 0f)
        assertEquals(paused.elapsedTimeMillis, updated.elapsedTimeMillis)
        assertEquals(paused.remainingTimeMillis, updated.remainingTimeMillis)
    }

    private fun seconds(value: Long): Long = value * 1_000_000_000L
}
