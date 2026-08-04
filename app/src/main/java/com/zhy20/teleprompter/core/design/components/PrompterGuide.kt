package com.zhy20.teleprompter.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.core.model.GuideMode

const val GuideLineTestTag = "prompterGuideLine"
const val GuideHighlightBarTestTag = "prompterGuideHighlightBar"

/** Shared, mutually-exclusive guide renderer used by setup preview and playback. */
@Composable
fun PrompterGuide(
    mode: GuideMode,
    position: Float,
    color: Color,
    modifier: Modifier = Modifier,
    highlightHeight: Dp = 56.dp,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val guideY = maxHeight * position.coerceIn(0.15f, 0.75f)
        when (mode) {
            GuideMode.Off -> Unit
            GuideMode.Line -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .offset(y = guideY)
                    .background(color)
                    .semantics { testTag = GuideLineTestTag },
            )
            GuideMode.HighlightBar -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(highlightHeight)
                    .offset(y = guideY - highlightHeight / 2)
                    .background(color.copy(alpha = .26f))
                    .semantics { testTag = GuideHighlightBarTestTag },
            )
        }
    }
}
