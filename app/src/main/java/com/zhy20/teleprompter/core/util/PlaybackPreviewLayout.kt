package com.zhy20.teleprompter.core.util

import com.zhy20.teleprompter.core.model.PlaybackOrientation

/** Pure sizing rules shared by the setup preview and its test coverage. */
object PlaybackPreviewLayout {
    fun aspectRatio(orientation: PlaybackOrientation): Float = when (orientation) {
        PlaybackOrientation.Portrait -> 9f / 16f
        PlaybackOrientation.Landscape -> 16f / 9f
    }

    fun maxVisibleLines(availableHeightDp: Float, fontSizeSp: Int): Int {
        val previewLineHeightDp = (fontSizeSp * .44f).coerceAtLeast(18f)
        return (availableHeightDp / previewLineHeightDp).toInt().coerceIn(3, 12)
    }
}
