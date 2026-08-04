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
    val guidePositionPx: Float,
    val initialOffsetPx: Float,
    val finalOffsetPx: Float,
    val totalScrollDistancePx: Float,
    val requiresScrolling: Boolean,
)

object PrompterLayoutCalculator {
    fun calculate(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        statusBandHeightPx: Float,
        textMeasuredHeightPx: Float,
        guidePosition: Float,
    ): PrompterLayoutMetrics {
        val width = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 0f
        val height = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: 0f
        val status = statusBandHeightPx.takeIf { it.isFinite() && it >= 0f }?.coerceAtMost(height) ?: 0f
        val contentHeight = (height - status).coerceAtLeast(0f)
        val textHeight = textMeasuredHeightPx.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val playback = PlaybackLayoutCalculator.calculate(contentHeight, textHeight)
        val contentTop = status
        return PrompterLayoutMetrics(
            viewportWidthPx = width,
            viewportHeightPx = height,
            statusBandHeightPx = status,
            contentViewportHeightPx = contentHeight,
            contentTopPx = contentTop,
            contentBottomPx = height,
            textMeasuredHeightPx = textHeight,
            guidePositionPx = contentTop + contentHeight * guidePosition.coerceIn(.15f, .75f),
            initialOffsetPx = playback.startOffsetPx,
            finalOffsetPx = playback.endOffsetPx,
            totalScrollDistancePx = playback.totalScrollDistancePx,
            requiresScrolling = playback.requiresScrolling,
        )
    }
}
