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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.zhy20.teleprompter.BuildConfig
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
import com.zhy20.teleprompter.feature.prompter.reading.ComposeReadingLayout
import com.zhy20.teleprompter.feature.prompter.reading.PlaybackReadingTracker
import com.zhy20.teleprompter.feature.prompter.reading.ReadingCursorSample
import com.zhy20.teleprompter.feature.prompter.reading.ReadingWindow
import com.zhy20.teleprompter.feature.prompter.reading.ReadingWindowManager
import kotlin.math.abs
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
 * Assigns a fresh, stable revision to each canonical document instance seen by the playback
 * viewport. The reading cursor/window are tagged with it so the controller can discard stale
 * positions when the script text changes.
 */
private val canonicalTextRevisionCounter = java.util.concurrent.atomic.AtomicLong(0L)

private const val PlaybackViewportTag = "PlaybackViewport"

/**
 * DIAG (RemoteReadingDiag): prompter-side real reading-cursor tracing. Plain @Volatile fields
 * (NOT Compose state) so the throttle never causes a recomposition. Only active under
 * BuildConfig.DEBUG; never logs text content, tokens, session or IP.
 */
private const val RemoteReadingDiagTag = "RemoteReadingDiag"
@Volatile private var lastCursorDiagNanos = 0L
@Volatile private var lastCursorDiagOffset = Double.NaN
@Volatile private var lastCursorDiagState: String? = null

/** A cursor + window computed for the current frame (kept as one immutable value). */
private data class ReadingFrame(
    val cursor: ReadingCursorSample,
    val window: ReadingWindow,
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
    onReadingCursor: (ReadingCursorSample?) -> Unit = {},
    onReadingWindow: (ReadingWindow?) -> Unit = {},
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

        // Real reading-position source. Text geometry always uses the stable session-start
        // readingAnchor. The cursor has an independent viewport anchor so moving an enabled
        // guide while paused can update the controller without shifting the rendered text.
        // The cursor is a continuous absolute UTF-16 offset into the canonical text
        // (PlaybackReadingTracker); the window is a large contiguous slice of that same text
        // (ReadingWindowManager) that slides with hysteresis. The controller re-flows the
        // window at its own width, so visual lines are never used as a cross-device unit.
        val textRevision by remember(document) { mutableLongStateOf(canonicalTextRevisionCounter.incrementAndGet()) }
        val windowManager = remember(document) { ReadingWindowManager() }
        var lastReportedWindowRevision by remember { mutableLongStateOf(-1L) }
        val readingAnchor = session?.readingAnchor
        val readingCursorAnchorFraction = session?.readingCursorAnchorFraction
        val readingFrame = remember(
            mode,
            playbackTextLayout,
            readingAnchor,
            readingCursorAnchorFraction,
            contentOffset,
            contentHeightPx,
            document,
        ) {
            if (mode != PrompterViewportMode.Playback) return@remember null
            val layout = playbackTextLayout ?: return@remember null
            if (contentHeightPx <= 0) return@remember null
            val anchorFraction = readingCursorAnchorFraction ?: readingAnchor?.viewportFraction ?: 0.25f
            // The text is offset by contentOffset (graphicsLayer translationY), so the reading
            // anchor's text-local Y is exactly anchorViewportY - contentOffset.
            val anchorViewportY = contentHeightPx * anchorFraction.coerceIn(0f, 1f)
            val anchorLocalY = anchorViewportY - contentOffset
            val readingLayout = ComposeReadingLayout(layout)
            val cursor = PlaybackReadingTracker.computeCursor(readingLayout, anchorLocalY, textRevision)
            val window = windowManager.update(readingLayout.text, textRevision, cursor.absoluteOffset)
            ReadingFrame(cursor, window)
        }
        // Push the cursor on every frame. The network layer is latest-only, so intermediate
        // per-frame values are dropped and only the most recent is transmitted at ~12–20 Hz.
        // DIAG: log the REAL cursor (already fully computed by PlaybackReadingTracker) at most
        // every 500 ms during normal playback, immediately on seek (offset jump >= 2.0), on
        // playback-state change (pause/resume) and on window-revision change.
        SideEffect {
            val c = readingFrame?.cursor
            onReadingCursor(c)
            if (c != null && BuildConfig.DEBUG) {
                val now = System.nanoTime()
                val stateName = session?.playbackState.toString()
                val offsetJump = lastCursorDiagOffset.isNaN() || abs(c.absoluteOffset - lastCursorDiagOffset) >= 2.0
                val stateChanged = lastCursorDiagState != stateName
                val windowChanged = readingFrame?.window?.revision != lastReportedWindowRevision
                val throttled = (now - lastCursorDiagNanos) >= 500_000_000L
                if (offsetJump || stateChanged || windowChanged || throttled) {
                    lastCursorDiagNanos = now
                    lastCursorDiagOffset = c.absoluteOffset
                    lastCursorDiagState = stateName
                    android.util.Log.d(
                        RemoteReadingDiagTag,
                        "CURSOR elapsed=${session?.elapsedTimeMillis} progress=${session?.currentSemanticProgress} " +
                            "offset=${c.absoluteOffset} line=${c.lineIndex} textRev=${c.textRevision}",
                    )
                }
            }
        }
        // Report the window on every frame too: AppState stores it by structural equality, so
        // this is a no-op while the window is stable and guarantees a freshly slid window
        // reaches the app/network layer the moment it is produced (no LaunchedEffect-key
        // fragility that could freeze the window at its initial value).
        SideEffect {
            val w = readingFrame?.window
            if (w != null && w.revision != lastReportedWindowRevision) {
                lastReportedWindowRevision = w.revision
                android.util.Log.d(
                    PlaybackViewportTag,
                    "ReadingWindow created: revision=${w.revision} textRevision=${w.textRevision} start=${w.startOffset} end=${w.endOffset}",
                )
            }
            onReadingWindow(w)
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
