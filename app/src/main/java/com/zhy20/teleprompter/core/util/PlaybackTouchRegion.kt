package com.zhy20.teleprompter.core.util

/**
 * Maps only app-content coordinates. Android system navigation remains outside this policy.
 * The values are density-aware so the dead zone neither disappears on phones nor dominates tablets.
 */
data class PlaybackTouchRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

object PlaybackTouchPolicy {
    fun centralRegion(widthPx: Float, heightPx: Float, density: Float): PlaybackTouchRegion {
        val horizontal = (widthPx * 0.085f).coerceIn(32f * density, 96f * density).coerceAtMost(widthPx * .24f)
        val vertical = (heightPx * 0.065f).coerceIn(24f * density, 72f * density).coerceAtMost(heightPx * .24f)
        return PlaybackTouchRegion(horizontal, vertical, widthPx - horizontal, heightPx - vertical)
    }

    fun allowsPlaybackGesture(
        widthPx: Float,
        heightPx: Float,
        density: Float,
        x: Float,
        y: Float,
        isPlaying: Boolean,
        controlsVisible: Boolean,
    ): Boolean = isPlaying && !controlsVisible && centralRegion(widthPx, heightPx, density).contains(x, y)
}
