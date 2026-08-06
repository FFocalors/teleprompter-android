package com.zhy20.teleprompter.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhy20.teleprompter.core.design.RichScriptText
import com.zhy20.teleprompter.core.design.toComposeTextAlign
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.util.PlaybackEngineState
import com.zhy20.teleprompter.core.util.PlaybackMirrorPolicy
import com.zhy20.teleprompter.core.util.PlaybackPreviewLayout
import com.zhy20.teleprompter.core.util.PlaybackVisualLayer
import com.zhy20.teleprompter.core.util.PrompterLayoutCalculator
import com.zhy20.teleprompter.core.util.PrompterLayoutMetrics
import com.zhy20.teleprompter.feature.prompter.PlaybackReadingTextUpdate
import com.zhy20.teleprompter.feature.prompter.PlaybackReadingWindow
import com.zhy20.teleprompter.feature.prompter.advanceReadingWindowFromLayout
import com.zhy20.teleprompter.feature.prompter.extractReadingWindow
import kotlin.math.floor
import kotlin.math.min

enum class PrompterViewportMode { Playback, Preview }

/** The unscaled target canvas used by the setup preview. */
data class PrompterPreviewTarget(
    val width: Dp,
    val height: Dp,
    val usesLargeLayout: Boolean,
)

/**
 * Shared playback frame. Preview scales the selected device's target canvas while runtime
 * playback uses that real viewport. Both therefore share status space, content geometry,
 * styles and guides.
 */
@Composable
fun PrompterViewport(
    document: ScriptContent,
    settings: PlaybackSettings,
    mode: PrompterViewportMode,
    foreground: Color,
    guideColor: Color,
    modifier: Modifier = Modifier,
    session: PlaybackEngineState? = null,
    previewTarget: PrompterPreviewTarget? = null,
    scriptTestTag: String? = null,
    onLayoutMeasured: (PrompterLayoutMetrics) -> Unit = {},
    statusContent: @Composable BoxScope.(PrompterLayoutMetrics, Float) -> Unit = { _, _ -> },
    onNearbyTextChanged: (PlaybackReadingTextUpdate?) -> Unit = {},
) {
    val density = LocalDensity.current
    var statusHeightPx by remember { mutableIntStateOf(0) }
    var contentWidthPx by remember { mutableIntStateOf(0) }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var fullTextHeightPx by remember { mutableIntStateOf(0) }

    // Playback-only: the real TextLayoutResult of the visible document, used to locate the
    // guide line's visual line. The guide Y is derived deterministically from contentOffset +
    // contentHeightPx + guideLinePosition (the same rule PrompterGuide draws with), so no
    // LayoutCoordinates bookkeeping is needed.
    var playbackTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    BoxWithConstraints(modifier.clipToBounds()) {
        val virtualWidth = previewTarget?.width?.value ?: if (settings.orientation == PlaybackOrientation.Portrait) 360f else 640f
        val virtualHeight = previewTarget?.height?.value ?: if (settings.orientation == PlaybackOrientation.Portrait) 640f else 360f
        val scale = when (mode) {
            PrompterViewportMode.Playback -> 1f
            PrompterViewportMode.Preview -> min(maxWidth.value / virtualWidth, maxHeight.value / virtualHeight)
                .coerceAtLeast(.01f)
        }
        val usesLargeLayout = when (mode) {
            PrompterViewportMode.Playback -> PlaybackPreviewLayout.usesLargeLayout(maxWidth.value)
            PrompterViewportMode.Preview -> previewTarget?.usesLargeLayout
                ?: PlaybackPreviewLayout.usesLargeLayout(virtualWidth)
        }
        val statusBandHeight = (if (usesLargeLayout) 64.dp else 56.dp) * scale
        val horizontalPadding = (if (usesLargeLayout) 72.dp else 28.dp) * scale
        val textStyle = TextStyle(
            fontSize = (settings.fontSize * scale).sp,
            lineHeight = (settings.fontSize * 1.18f * scale).sp,
        )
        val metrics = PrompterLayoutCalculator.calculate(
            viewportWidthPx = contentWidthPx.toFloat(),
            viewportHeightPx = (statusHeightPx + contentHeightPx).toFloat(),
            statusBandHeightPx = statusHeightPx.toFloat(),
            textMeasuredHeightPx = fullTextHeightPx.toFloat(),
            guidePosition = settings.guideLinePosition,
            readingAnchor = session?.readingAnchor,
            lineHeightPx = with(density) { textStyle.lineHeight.toPx() },
        )
        val previewMaxLines = with(density) {
            val lineHeightPx = textStyle.lineHeight.toPx().coerceAtLeast(1f)
            floor(contentHeightPx / lineHeightPx).toInt().coerceAtLeast(1)
        }
        val contentOffset = when (mode) {
            PrompterViewportMode.Playback -> session?.let { currentSession ->
                // A fresh playback has an explicit identity; elapsed time is not a reliable way
                // to distinguish it from a restored or resume-countdown session.
                if (currentSession.isStartingFromBeginning) {
                    currentSession.startOffset
                } else {
                    currentSession.currentScrollOffset
                }
            } ?: 0f
            // The preview is an editing aid. It always shows the beginning of the script while
            // retaining the player's visual geometry and only ellipsizing its final visible line.
            PrompterViewportMode.Preview -> 0f
        }
        val contentVisible = mode == PrompterViewportMode.Preview || session?.layoutReady == true

        LaunchedEffect(metrics) {
            if (metrics.contentViewportHeightPx > 0f && metrics.textMeasuredHeightPx >= 0f) {
                onLayoutMeasured(metrics)
            }
        }

        // Real reading-text location. The reading anchor is the STABLE one captured at session
        // start (session.readingAnchor), never the live guideLinePosition — so moving or
        // toggling the visual guide line during playback cannot make the controller's text
        // jump to a different passage. A hysteresis window keeps the text block stable while
        // the active range advances inside it.
        var lastWindow by remember { mutableStateOf<PlaybackReadingWindow?>(null) }
        val readingAnchor = session?.readingAnchor
        val readingWindowState = remember(
            mode,
            playbackTextLayout,
            readingAnchor,
            contentOffset,
            contentHeightPx,
            document,
        ) {
            if (mode != PrompterViewportMode.Playback) return@remember null
            val layout = playbackTextLayout ?: return@remember null
            if (contentHeightPx <= 0) return@remember null
            val anchorFraction = readingAnchor?.viewportFraction ?: 0.25f
            val anchorViewportY = contentHeightPx * anchorFraction.coerceIn(0f, 1f)
            val anchorLocalY = anchorViewportY - contentOffset
            // Determine the reading line, then either slide the existing window or build a
            // fresh one with the hysteresis policy.
            val lineCount = layout.lineCount
            if (lineCount <= 0) return@remember null
            val anchorLine = layout.getLineForVerticalPosition(anchorLocalY).coerceIn(0, lineCount - 1)
            val prev = lastWindow
            if (prev != null) {
                advanceReadingWindowFromLayout(layout, prev, anchorLine)
            } else {
                extractReadingWindow(layout, anchorLocalY)
            }
        }
        LaunchedEffect(readingWindowState) {
            val window = readingWindowState
            if (window == null) {
                if (lastWindow != null) {
                    lastWindow = null
                    onNearbyTextChanged(null)
                }
                return@LaunchedEffect
            }
            if (window == lastWindow) return@LaunchedEffect
            lastWindow = window
            onNearbyTextChanged(
                PlaybackReadingTextUpdate(
                    text = window.text,
                    activeStart = window.activeStart,
                    activeEnd = window.activeEnd,
                    sourceStartOffset = window.sourceStartOffset,
                    sourceEndOffset = window.sourceEndOffset,
                ),
            )
        }

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusBandHeight)
                    .onSizeChanged { statusHeightPx = it.height },
            ) {
                statusContent(metrics, scale)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .onSizeChanged {
                        contentWidthPx = it.width
                        contentHeightPx = it.height
                    },
            ) {
                if (mode == PrompterViewportMode.Preview) {
                    // Measure the complete document first. The visible preview may ellipsize,
                    // but its initial offset must still follow the real long/short rule.
                    RichScriptText(
                        document = document,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding)
                            // wrapContentHeight defaults to CenterVertically. With an unbounded
                            // long document that would center the oversized child and make the
                            // first visible frame appear halfway through the script.
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .graphicsLayer { alpha = 0f },
                        color = foreground,
                        style = textStyle,
                        textAlign = settings.textAlignment.toComposeTextAlign(),
                        overflow = TextOverflow.Clip,
                        onTextLayout = { fullTextHeightPx = it.size.height },
                    )
                }
                RichScriptText(
                    document = document,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .graphicsLayer {
                            translationY = contentOffset
                            scaleX = PlaybackMirrorPolicy.scaleX(settings.mirrorEnabled, PlaybackVisualLayer.ScriptContent)
                            alpha = if (contentVisible) 1f else 0f
                        }
                        .then(if (scriptTestTag == null) Modifier else Modifier.semantics { testTag = scriptTestTag }),
                    color = foreground,
                    style = textStyle,
                    maxLines = if (mode == PrompterViewportMode.Preview) previewMaxLines else Int.MAX_VALUE,
                    textAlign = settings.textAlignment.toComposeTextAlign(),
                    overflow = if (mode == PrompterViewportMode.Preview) TextOverflow.Ellipsis else TextOverflow.Clip,
                    onTextLayout = { result ->
                        if (mode == PrompterViewportMode.Playback) {
                            fullTextHeightPx = result.size.height
                            playbackTextLayout = result
                        }
                    },
                )
                PrompterGuide(
                    mode = settings.guideMode,
                    position = settings.guideLinePosition,
                    color = guideColor,
                    highlightHeight = 56.dp * scale,
                )
            }
        }
    }
}
