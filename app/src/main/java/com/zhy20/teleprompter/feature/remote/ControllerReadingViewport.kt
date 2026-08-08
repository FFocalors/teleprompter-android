package com.zhy20.teleprompter.feature.remote

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.feature.prompter.reading.ComposeReadingLayout
import com.zhy20.teleprompter.feature.prompter.reading.ControllerReadingViewportMath
import com.zhy20.teleprompter.remote.model.RemoteReadingCursor
import com.zhy20.teleprompter.remote.model.RemoteReadingWindow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val ControllerViewportTag = "ControllerReadingViewport"

/** The controller viewport keeps the reading position near the top of the fixed area. */
private const val ReadingAnchorFraction = 0.28f

/** Cursor moves larger than this (in px) are treated as a seek and snap instead of animating. */
private const val SnapThresholdLines = 3f

/** Test tag on the fixed-height area surface of [ControllerReadingViewport]. */
const val ControllerReadingViewportAreaTag = "controllerReadingViewportArea"

/** Debug/test metrics proving that the cached text is laid out beyond the clipped viewport. */
val ControllerReadingWindowLengthKey = SemanticsPropertyKey<Int>("ControllerReadingWindowLength")
val ControllerReadingLineCountKey = SemanticsPropertyKey<Int>("ControllerReadingLineCount")
val ControllerReadingLayoutHeightKey = SemanticsPropertyKey<Int>("ControllerReadingLayoutHeight")
val ControllerReadingViewportHeightKey = SemanticsPropertyKey<Int>("ControllerReadingViewportHeight")
val ControllerReadingTranslationYKey = SemanticsPropertyKey<Int>("ControllerReadingTranslationY")

private var SemanticsPropertyReceiver.controllerReadingWindowLength by ControllerReadingWindowLengthKey
private var SemanticsPropertyReceiver.controllerReadingLineCount by ControllerReadingLineCountKey
private var SemanticsPropertyReceiver.controllerReadingLayoutHeight by ControllerReadingLayoutHeightKey
private var SemanticsPropertyReceiver.controllerReadingViewportHeight by ControllerReadingViewportHeightKey
private var SemanticsPropertyReceiver.controllerReadingTranslationY by ControllerReadingTranslationYKey

/**
 * The controller's "当前朗读文本" viewport.
 *
 * It holds the latest [RemoteReadingWindow] (a large contiguous slice of the prompter's
 * canonical text) and re-flows it at the **controller's own width/font**, so tablet visual line
 * breaks never leak to the phone. The high-frequency [RemoteReadingCursor] is mapped onto that
 * local layout ([ControllerReadingViewportMath]) and the text is smoothly translated so the
 * reading position stays near the fixed reading anchor (~28% of the area). No second playback
 * clock exists: the phone only renders positions the prompter sends.
 *
 * The area height is fixed (≈5–6 phone lines): short windows do not shrink, long windows never
 * stretch the card, and the progress bar/time rows below never jump.
 */
@Composable
fun ControllerReadingViewport(
    window: RemoteReadingWindow?,
    cursor: RemoteReadingCursor?,
    cursorUpdates: StateFlow<RemoteReadingCursor?>? = null,
    modifier: Modifier = Modifier,
    placeholder: String,
    semanticsTag: String,
) {
    val streamedCursor = cursorUpdates?.collectAsStateWithLifecycle()?.value ?: cursor
    val density = LocalDensity.current
    val lineHeightPx = with(density) { MaterialTheme.typography.bodyLarge.lineHeight.toPx() }
    val snapThresholdPx = lineHeightPx * SnapThresholdLines

    BoxWithConstraints(modifier) {
        // Narrow screens / very large fonts fall back to 4 visible lines to keep the fixed
        // area readable without overflowing the card.
        val narrowRows = if (maxWidth < 360.dp || lineHeightPx > 28f * density.density) 4 else 6
        val viewportHeightPx = lineHeightPx * narrowRows

        Surface(
            color = AppColors.Secondary.copy(alpha = .25f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(with(density) { viewportHeightPx.toDp() })
                .semantics { testTag = ControllerReadingViewportAreaTag },
        ) {
            if (window == null || window.text.isBlank()) {
                Text(
                    text = placeholder,
                    modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.md)
                        .semantics { testTag = semanticsTag },
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                // Rebuild the big AnnotatedString only when the window slides; cursor updates
                // never touch it.
                val annotated = remember(window.textRevision, window.windowRevision) {
                    AnnotatedString(window.text)
                }
                // Reset the layout when the window changes so a stale layout is never mapped.
                var textLayout by remember(window.textRevision, window.windowRevision) {
                    mutableStateOf<TextLayoutResult?>(null)
                }
                // Translation is local to a specific laid-out window. A new window starts with
                // fresh motion state and is snapped from its absolute cursor after layout.
                val scroll = remember(window.textRevision, window.windowRevision) { Animatable(0f) }
                var lastAppliedTextRevision by remember { mutableLongStateOf(-1L) }
                var lastAppliedWindowRevision by remember { mutableLongStateOf(-1L) }

                LaunchedEffect(
                    window.textRevision,
                    window.windowRevision,
                    streamedCursor?.sequence,
                    streamedCursor?.absoluteOffset,
                    textLayout,
                ) {
                    val w = window
                    val c = streamedCursor
                    val layout = textLayout
                    if (c == null || layout == null) return@LaunchedEffect
                    // Never apply a cursor that does not share the window's canonical text.
                    if (c.textRevision != w.textRevision) return@LaunchedEffect
                    // The cursor may briefly be outside the current window (window/cursor
                    // reorder, or a seek whose covering window has not arrived yet). Hold the
                    // last good translation — this is the controller's pending cursor; it is
                    // applied the moment a covering window arrives.
                    if (c.absoluteOffset < w.startOffset || c.absoluteOffset > w.endOffset) return@LaunchedEffect
                    val target = ControllerReadingViewportMath.targetTranslationY(
                        layout = ComposeReadingLayout(layout),
                        windowStart = w.startOffset,
                        windowText = w.text,
                        absoluteCursor = c.absoluteOffset,
                        viewportHeight = viewportHeightPx,
                        anchorFraction = ReadingAnchorFraction,
                    )
                    val current = scroll.value
                    val windowJustChanged = w.textRevision != lastAppliedTextRevision ||
                        w.windowRevision != lastAppliedWindowRevision
                    lastAppliedTextRevision = w.textRevision
                    lastAppliedWindowRevision = w.windowRevision
                    if (windowJustChanged || abs(target - current) > snapThresholdPx) {
                        // A fresh window (or a seek) re-anchors immediately from the absolute
                        // cursor, so the old window's local translation is never applied to the
                        // new window and the reading line does not jump to the top/bottom.
                        scroll.snapTo(target)
                        android.util.Log.d(
                            ControllerViewportTag,
                            "ReadingWindow applied: revision=${w.windowRevision} " +
                                "relativeCursor=${(c.absoluteOffset - w.startOffset).toInt()} " +
                                "targetY=${target.toInt()}",
                        )
                    } else {
                        scroll.animateTo(target, tween(durationMillis = 70, easing = LinearEasing))
                    }
                }

                Box(Modifier.fillMaxSize().clipToBounds()) {
                    Text(
                        text = annotated,
                        modifier = Modifier
                            .fillMaxWidth()
                            // The fixed outer Box is only an observation viewport. Remove its
                            // max-height constraint while measuring the cached text so every
                            // visual line participates in TextLayoutResult.
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .padding(horizontal = AppSpacing.md)
                            .graphicsLayer { translationY = scroll.value }
                            .semantics {
                                testTag = semanticsTag
                                controllerReadingWindowLength = window.text.length
                                controllerReadingLineCount = textLayout?.lineCount ?: 0
                                controllerReadingLayoutHeight = textLayout?.size?.height ?: 0
                                controllerReadingViewportHeight = viewportHeightPx.roundToInt()
                                controllerReadingTranslationY = scroll.value.roundToInt()
                            },
                        color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        onTextLayout = { result ->
                            val previous = textLayout
                            textLayout = result
                            if (previous?.lineCount != result.lineCount ||
                                previous?.size?.height != result.size.height
                            ) {
                                android.util.Log.d(
                                    ControllerViewportTag,
                                    "ReadingWindow layout: revision=${window.windowRevision} " +
                                        "windowLength=${window.text.length} lineCount=${result.lineCount} " +
                                        "layoutHeight=${result.size.height} " +
                                        "viewportHeight=${viewportHeightPx.roundToInt()}",
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
