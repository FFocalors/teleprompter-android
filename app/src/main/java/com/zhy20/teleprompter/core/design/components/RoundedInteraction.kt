package com.zhy20.teleprompter.core.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.zhy20.teleprompter.core.design.AppMotion

/**
 * Transform-only press feedback. Pressing is immediate (the equivalent of gsap.set), while the
 * release returns with GSAP's default 0.5 s power1.out tween.
 */
@Composable
fun Modifier.motionPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed && AppMotion.animationsEnabled()) AppMotion.PressedScale else 1f,
        animationSpec = if (pressed || !enabled) snap() else AppMotion.defaultSpec(),
        label = "controlPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Keeps the pointer indication inside the same rounded outline as the visible control.
 * Surface/Card does not clip an outer clickable modifier by default, which otherwise makes a
 * desktop hover/press layer look like a square even when the control itself has rounded corners.
 */
@Composable
fun Modifier.roundedClickable(
    shape: Shape,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    return motionPress(interactionSource = interactionSource, enabled = enabled)
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}
