package com.zhy20.teleprompter.core.util

/** Geometry shared by the real player and its scaled setup preview. */
data class PrompterLayoutMetrics(
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val statusBandHeightPx: Float,
    val contentViewportHeightPx: Float,
    val contentTopPx: Float,
    val contentBottomPx: Float,
    val textMeasuredHeightPx: Float,
    val lineHeightPx: Float,
    val guidePositionPx: Float,
    val initialOffsetPx: Float,
    val finalOffsetPx: Float,
    val totalScrollDistancePx: Float,
    val requiresScrolling: Boolean,
)

object PrompterLayoutCalculator {
    /**
     * @param readingAnchor when non-null, the live-playback anchor captured at session start
     *   (guide-on: line sits 1.5 lines below the anchor Y; guide-off: first line at 25% from
     *   the top). When null, the classic preview/playback bottom-entry rule applies.
     * @param lineHeightPx real measured line height used to scale the initial text offset.
     */
    fun calculate(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        statusBandHeightPx: Float,
        textMeasuredHeightPx: Float,
        guidePosition: Float,
        readingAnchor: PlaybackReadingAnchor? = null,
        lineHeightPx: Float = 0f,
    ): PrompterLayoutMetrics {
        val width = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 0f
        val height = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: 0f
        val status = statusBandHeightPx.takeIf { it.isFinite() && it >= 0f }?.coerceAtMost(height) ?: 0f
        val contentHeight = (height - status).coerceAtLeast(0f)
        val textHeight = textMeasuredHeightPx.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val playback = if (readingAnchor != null) {
            PlaybackLayoutCalculator.calculate(
                viewportHeightPx = contentHeight,
                textHeightPx = textHeight,
                mode = PlaybackLayoutMode.LivePlayback,
                readingAnchor = readingAnchor,
                lineHeightPx = lineHeightPx,
            )
        } else {
            PlaybackLayoutCalculator.calculate(contentHeight, textHeight)
        }
        val contentTop = status
        return PrompterLayoutMetrics(
            viewportWidthPx = width,
            viewportHeightPx = height,
            statusBandHeightPx = status,
            contentViewportHeightPx = contentHeight,
            contentTopPx = contentTop,
            contentBottomPx = height,
            textMeasuredHeightPx = textHeight,
            lineHeightPx = lineHeightPx.takeIf { it.isFinite() && it > 0f } ?: 0f,
            guidePositionPx = contentTop + contentHeight * guidePosition.coerceIn(.15f, .75f),
            initialOffsetPx = playback.startOffsetPx,
            finalOffsetPx = playback.endOffsetPx,
            totalScrollDistancePx = playback.totalScrollDistancePx,
            requiresScrolling = playback.requiresScrolling,
        )
    }
}
