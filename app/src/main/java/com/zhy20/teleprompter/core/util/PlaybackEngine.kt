package com.zhy20.teleprompter.core.util

import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.PlaybackState
import kotlin.math.roundToLong

data class PlaybackLayoutMetrics(
    val viewportHeightPx: Float,
    val textHeightPx: Float,
    val requiresScrolling: Boolean,
    val startOffsetPx: Float,
    val endOffsetPx: Float,
    val totalScrollDistancePx: Float,
)

object PlaybackLayoutCalculator {
    /** Places the top of the first line near the bottom while keeping that line readable. */
    private const val InitialTextTopFraction = 0.82f
    private const val FinalTextBottomFraction = 0.67f

    fun calculate(viewportHeightPx: Float, textHeightPx: Float): PlaybackLayoutMetrics {
        val viewport = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: 0f
        val text = textHeightPx.takeIf { it.isFinite() && it >= 0f } ?: 0f
        if (viewport == 0f) return PlaybackLayoutMetrics(0f, text, false, 0f, 0f, 0f)

        if (text <= 0.5f) {
            return PlaybackLayoutMetrics(viewport, text, false, 0f, 0f, 0f)
        }

        // The document still begins with its first paragraph, but that paragraph enters from
        // the lower edge of the playback viewport before moving upward.
        val start = viewport * InitialTextTopFraction
        val end = viewport * FinalTextBottomFraction - text
        val distance = (start - end).coerceAtLeast(0f)
        return PlaybackLayoutMetrics(viewport, text, distance > 0.5f, start, end, distance)
    }
}

data class PlaybackEngineState(
    val layoutReady: Boolean = false,
    val requiresScrolling: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val elapsedTimeMillis: Long = 0L,
    val currentSemanticProgress: Float = 0f,
    val currentScrollOffset: Float = 0f,
    val totalScrollDistance: Float = 0f,
    val currentSpeedMultiplier: Float = 1f,
    val configuredTargetDurationMillis: Long = 200_000L,
    val actualScrollDurationMillis: Long = 200_000L,
    val startOffset: Float = 0f,
    val endOffset: Float = 0f,
    val isManualAdjusting: Boolean = false,
    /** True only for a newly-created playback, never for pause/resume countdowns. */
    val isStartingFromBeginning: Boolean = false,
    internal val segmentStartedAtNanos: Long? = null,
    internal val segmentStartProgress: Float = 0f,
    internal val segmentStartElapsedMillis: Long = 0L,
) {
    val remainingTimeMillis: Long
        get() = if (requiresScrolling) {
            (actualScrollDurationMillis * (1f - currentSemanticProgress.coerceIn(0f, 1f))).roundToLong()
        } else {
            (actualScrollDurationMillis - elapsedTimeMillis).coerceAtLeast(0L)
        }

    val showAutomaticProgress: Boolean
        get() = layoutReady && requiresScrolling && playbackState == PlaybackState.Playing &&
            !isManualAdjusting && currentSemanticProgress < 1f
}

object PlaybackEngine {
    fun prepare(settings: PlaybackSettings, normalDurationSeconds: Int): PlaybackEngineState =
        PlaybackEngineState(
            playbackState = PlaybackState.Preparing,
            isStartingFromBeginning = true,
            currentSpeedMultiplier = settings.speedMultiplier,
            configuredTargetDurationMillis = settings.targetDurationSeconds.coerceAtLeast(1) * 1_000L,
            actualScrollDurationMillis = durationMillis(settings, normalDurationSeconds),
        )

    fun updateLayout(
        state: PlaybackEngineState,
        viewportHeightPx: Float,
        textHeightPx: Float,
        settings: PlaybackSettings,
        normalDurationSeconds: Int,
        nowNanos: Long,
    ): PlaybackEngineState {
        val current = tick(state, nowNanos)
        val layout = PlaybackLayoutCalculator.calculate(viewportHeightPx, textHeightPx)
        val progress = current.currentSemanticProgress.safeProgress()
        return current.copy(
            layoutReady = viewportHeightPx > 0f && textHeightPx >= 0f,
            requiresScrolling = layout.requiresScrolling,
            currentScrollOffset = offsetFor(progress, layout.startOffsetPx, layout.endOffsetPx),
            totalScrollDistance = layout.totalScrollDistancePx,
            startOffset = layout.startOffsetPx,
            endOffset = layout.endOffsetPx,
            currentSpeedMultiplier = settings.speedMultiplier,
            configuredTargetDurationMillis = settings.targetDurationSeconds.coerceAtLeast(1) * 1_000L,
            actualScrollDurationMillis = durationMillis(settings, normalDurationSeconds),
            segmentStartedAtNanos = if (current.playbackState == PlaybackState.Playing && !current.isManualAdjusting) nowNanos else null,
            segmentStartProgress = progress,
            segmentStartElapsedMillis = current.elapsedTimeMillis,
        )
    }

    fun reconfigure(
        state: PlaybackEngineState,
        settings: PlaybackSettings,
        normalDurationSeconds: Int,
        nowNanos: Long,
    ): PlaybackEngineState {
        val current = tick(state, nowNanos)
        return current.copy(
            currentSpeedMultiplier = settings.speedMultiplier,
            configuredTargetDurationMillis = settings.targetDurationSeconds.coerceAtLeast(1) * 1_000L,
            actualScrollDurationMillis = durationMillis(settings, normalDurationSeconds),
            segmentStartedAtNanos = if (current.playbackState == PlaybackState.Playing && !current.isManualAdjusting) nowNanos else null,
            segmentStartProgress = current.currentSemanticProgress,
            segmentStartElapsedMillis = current.elapsedTimeMillis,
        )
    }

    fun setPlaybackState(state: PlaybackEngineState, playbackState: PlaybackState, nowNanos: Long): PlaybackEngineState {
        val current = tick(state, nowNanos)
        val running = playbackState == PlaybackState.Playing && !current.isManualAdjusting
        return current.copy(
            playbackState = playbackState,
            segmentStartedAtNanos = if (running) nowNanos else null,
            segmentStartProgress = current.currentSemanticProgress,
            segmentStartElapsedMillis = current.elapsedTimeMillis,
        )
    }

    fun tick(state: PlaybackEngineState, nowNanos: Long): PlaybackEngineState {
        val startedAt = state.segmentStartedAtNanos ?: return state
        if (state.playbackState != PlaybackState.Playing || state.isManualAdjusting) return state
        if (!state.layoutReady) return state
        val deltaMillis = ((nowNanos - startedAt).coerceAtLeast(0L) / 1_000_000L)
        val elapsed = state.segmentStartElapsedMillis + deltaMillis
        if (!state.requiresScrolling) {
            return state.copy(elapsedTimeMillis = elapsed, isStartingFromBeginning = false)
        }

        val duration = state.actualScrollDurationMillis.coerceAtLeast(1L)
        val progress = (state.segmentStartProgress + deltaMillis.toDouble() / duration.toDouble())
            .toFloat().safeProgress()
        val finished = progress >= 1f
        return state.copy(
            playbackState = if (finished) PlaybackState.Finished else state.playbackState,
            elapsedTimeMillis = elapsed,
            currentSemanticProgress = progress,
            currentScrollOffset = offsetFor(progress, state.startOffset, state.endOffset),
            segmentStartedAtNanos = if (finished) null else startedAt,
            isStartingFromBeginning = false,
        )
    }

    fun playFromBeginning(state: PlaybackEngineState, nowNanos: Long): PlaybackEngineState = state.copy(
        playbackState = PlaybackState.Playing,
        elapsedTimeMillis = 0L,
        currentSemanticProgress = 0f,
        currentScrollOffset = state.startOffset,
        isManualAdjusting = false,
        isStartingFromBeginning = true,
        segmentStartedAtNanos = if (state.layoutReady) nowNanos else null,
        segmentStartProgress = 0f,
        segmentStartElapsedMillis = 0L,
    )

    fun seek(state: PlaybackEngineState, progress: Float, nowNanos: Long): PlaybackEngineState {
        val current = tick(state, nowNanos)
        val safe = progress.safeProgress()
        return current.copy(
            currentSemanticProgress = safe,
            currentScrollOffset = offsetFor(safe, current.startOffset, current.endOffset),
            segmentStartedAtNanos = if (current.playbackState == PlaybackState.Playing && !current.isManualAdjusting) nowNanos else null,
            segmentStartProgress = safe,
            segmentStartElapsedMillis = current.elapsedTimeMillis,
            isStartingFromBeginning = false,
        )
    }

    fun beginManualAdjustment(state: PlaybackEngineState, nowNanos: Long): PlaybackEngineState =
        tick(state, nowNanos).copy(isManualAdjusting = true, segmentStartedAtNanos = null)

    fun endManualAdjustment(state: PlaybackEngineState, nowNanos: Long): PlaybackEngineState = state.copy(
        isManualAdjusting = false,
        segmentStartedAtNanos = if (state.playbackState == PlaybackState.Playing) nowNanos else null,
        segmentStartProgress = state.currentSemanticProgress,
        segmentStartElapsedMillis = state.elapsedTimeMillis,
    )

    fun reset(playbackState: PlaybackState = PlaybackState.Exited): PlaybackEngineState =
        PlaybackEngineState(playbackState = playbackState)

    private fun durationMillis(settings: PlaybackSettings, normalDurationSeconds: Int): Long {
        return PlaybackTiming.playbackDurationSeconds(settings, normalDurationSeconds) * 1_000L
    }

    private fun offsetFor(progress: Float, start: Float, end: Float): Float =
        start + (end - start) * progress.safeProgress()

    private fun Float.safeProgress(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f
}
