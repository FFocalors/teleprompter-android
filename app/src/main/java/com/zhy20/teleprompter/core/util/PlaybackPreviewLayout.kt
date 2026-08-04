package com.zhy20.teleprompter.core.util

import com.zhy20.teleprompter.core.model.PlaybackOrientation
import kotlin.math.max
import kotlin.math.min

/** Pure sizing rules shared by the setup preview and its test coverage. */
object PlaybackPreviewLayout {
    const val LargeViewportBreakpointDp = 700f

    data class TargetViewport(
        val widthDp: Float,
        val heightDp: Float,
    ) {
        val aspectRatio: Float get() = widthDp / heightDp
        val usesLargeLayout: Boolean get() = widthDp >= LargeViewportBreakpointDp
    }

    fun aspectRatio(orientation: PlaybackOrientation): Float = when (orientation) {
        PlaybackOrientation.Portrait -> 9f / 16f
        PlaybackOrientation.Landscape -> 16f / 9f
    }

    /**
     * The preview is a scaled canvas of the device that will actually run playback. This keeps
     * wrapping, spacing and the large-screen layout breakpoint aligned with the player.
     */
    fun targetViewport(
        currentWidthDp: Int,
        currentHeightDp: Int,
        orientation: PlaybackOrientation,
    ): TargetViewport {
        val smallerSide = min(currentWidthDp, currentHeightDp).coerceAtLeast(1).toFloat()
        val largerSide = max(currentWidthDp, currentHeightDp).coerceAtLeast(1).toFloat()
        return when (orientation) {
            PlaybackOrientation.Portrait -> TargetViewport(smallerSide, largerSide)
            PlaybackOrientation.Landscape -> TargetViewport(largerSide, smallerSide)
        }
    }

    fun usesLargeLayout(viewportWidthDp: Float): Boolean = viewportWidthDp >= LargeViewportBreakpointDp

    fun maxVisibleLines(availableHeightDp: Float, fontSizeSp: Int): Int {
        val previewLineHeightDp = (fontSizeSp * .44f).coerceAtLeast(18f)
        return (availableHeightDp / previewLineHeightDp).toInt().coerceIn(3, 12)
    }
}
