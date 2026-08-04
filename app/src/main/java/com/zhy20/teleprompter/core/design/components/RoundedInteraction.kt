package com.zhy20.teleprompter.core.design.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role

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
): Modifier = clip(shape).clickable(
    enabled = enabled,
    role = role,
    onClick = onClick,
)
