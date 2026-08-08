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

/**
 * How the playback layout calculator positions the first line. Preview keeps the classic
 * "enter from the lower edge" rule so the setup page is unchanged; live playback uses the
 * reading anchor captured when playback started (guide line, or top-quarter when off).
 */
enum class PlaybackLayoutMode {
    Preview,
    LivePlayback,
}

/**
 * Immutable logical reading anchor captured when a live playback session starts. Pixel
 * offsets are re-derived on every layout measurement, but the logical anchor never changes
 * while the session is alive — moving or toggling the visual guide line afterwards has no
 * effect on text position or nearby-text selection.
 */
data class PlaybackReadingAnchor(
    /** Fraction of the content viewport the first text baseline starts at. */
    val viewportFraction: Float,
    /** Extra visual lines below the anchor before the first text line (guide-on only). */
    val initialTextOffsetLines: Float,
    /** Total duration in millis captured at session start (drives progress scaling). */
    val durationMillis: Long,
    /** The script's normal (speed-mode) duration in seconds, captured at session start. */
    val normalDurationSeconds: Int,
)

object PlaybackLayoutCalculator {
    /** Places the top of the first line near the bottom while keeping that line readable. */
    private const val InitialTextTopFraction = 0.82f
    private const val FinalTextBottomFraction = 0.67f

    /** Legacy preview/playback entry point; behaves exactly as before (bottom entry). */
    fun calculate(viewportHeightPx: Float, textHeightPx: Float): PlaybackLayoutMetrics =
        calculate(viewportHeightPx, textHeightPx, mode = PlaybackLayoutMode.Preview, readingAnchor = null, lineHeightPx = 0f)

    /**
     * Layout calculation with an explicit mode. [PlaybackLayoutMode.Preview] keeps the
     * original bottom-entry rule; [PlaybackLayoutMode.LivePlayback] uses the captured
     * [PlaybackReadingAnchor]:
     *  - guide on: first line sits [PlaybackReadingAnchor.initialTextOffsetLines] visual
     *    lines below the anchor Y;
     *  - guide off: first line baseline at viewport × 0.25 from the top.
     */
    fun calculate(
        viewportHeightPx: Float,
        textHeightPx: Float,
        mode: PlaybackLayoutMode,
        readingAnchor: PlaybackReadingAnchor?,
        lineHeightPx: Float,
    ): PlaybackLayoutMetrics {
        val viewport = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: 0f
        val text = textHeightPx.takeIf { it.isFinite() && it >= 0f } ?: 0f
        if (viewport == 0f) return PlaybackLayoutMetrics(0f, text, false, 0f, 0f, 0f)
        if (text <= 0.5f) return PlaybackLayoutMetrics(viewport, text, false, 0f, 0f, 0f)

        val start = if (mode == PlaybackLayoutMode.Preview || readingAnchor == null) {
            // Classic rule: the first line enters from the lower edge of the playback viewport.
            viewport * InitialTextTopFraction
        } else {
            // Live playback: anchor Y from the captured fraction, plus the requested visual
            // lines below it (guide-on case), scaled by the real measured line height.
            val line = lineHeightPx.takeIf { it.isFinite() && it > 0f } ?: 0f
            val anchorY = viewport * readingAnchor.viewportFraction.coerceIn(0f, 1f)
            anchorY + readingAnchor.initialTextOffsetLines.coerceAtLeast(0f) * line
        }
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
    /**
     * Layout anchor captured when this session started. Immutable for the life of the session:
     * later guide-line moves/toggles never re-anchor the rendered text.
     */
    val readingAnchor: PlaybackReadingAnchor? = null,
    /**
     * Viewport anchor used only to locate the reading cursor sent to a remote controller.
     * While paused it may follow the visual guide line without changing text layout/progress.
     */
    val readingCursorAnchorFraction: Float? = null,
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
        prepare(settings, normalDurationSeconds, readingAnchor = null)

    /** Prepares a fresh session; [readingAnchor] is captured once and never changes. */
    fun prepare(
        settings: PlaybackSettings,
        normalDurationSeconds: Int,
        readingAnchor: PlaybackReadingAnchor?,
    ): PlaybackEngineState =
        PlaybackEngineState(
            playbackState = PlaybackState.Preparing,
            isStartingFromBeginning = true,
            currentSpeedMultiplier = settings.speedMultiplier,
            configuredTargetDurationMillis = settings.targetDurationSeconds.coerceAtLeast(1) * 1_000L,
            actualScrollDurationMillis = durationMillis(settings, normalDurationSeconds),
            readingAnchor = readingAnchor,
            readingCursorAnchorFraction = readingAnchor?.viewportFraction,
        )

    fun updateLayout(
        state: PlaybackEngineState,
        viewportHeightPx: Float,
        textHeightPx: Float,
        settings: PlaybackSettings,
        normalDurationSeconds: Int,
        nowNanos: Long,
        lineHeightPx: Float = 0f,
    ): PlaybackEngineState {
        val current = tick(state, nowNanos)
        val layout = if (state.readingAnchor != null) {
            PlaybackLayoutCalculator.calculate(
                viewportHeightPx = viewportHeightPx,
                textHeightPx = textHeightPx,
                mode = PlaybackLayoutMode.LivePlayback,
                readingAnchor = state.readingAnchor,
                lineHeightPx = lineHeightPx,
            )
        } else {
            // No anchor (preview path / legacy callers): keep the classic bottom-entry rule.
            PlaybackLayoutCalculator.calculate(viewportHeightPx, textHeightPx)
        }
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

    fun updateReadingCursorAnchor(state: PlaybackEngineState, viewportFraction: Float): PlaybackEngineState {
        if (!viewportFraction.isFinite()) return state
        return state.copy(readingCursorAnchorFraction = viewportFraction.coerceIn(0.15f, 0.75f))
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
