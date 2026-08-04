package com.zhy20.teleprompter.core.util

import android.content.pm.ActivityInfo
import com.zhy20.teleprompter.core.model.PlaybackOrientation

enum class PlaybackVisualLayer { ScriptContent, Status, Guide, Controls }

object PlaybackMirrorPolicy {
    fun scaleX(mirrorEnabled: Boolean, layer: PlaybackVisualLayer): Float =
        if (mirrorEnabled && layer == PlaybackVisualLayer.ScriptContent) -1f else 1f
}

fun PlaybackOrientation.requestedActivityOrientation(): Int = when (this) {
    PlaybackOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    PlaybackOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}
