package com.zhy20.teleprompter.feature.prompter.reading

import com.zhy20.teleprompter.core.util.PlaybackLayoutCalculator
import com.zhy20.teleprompter.core.util.PlaybackLayoutMode
import com.zhy20.teleprompter.core.util.PlaybackReadingAnchor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

/**
 * DIAGNOSTIC (§22) — simulates the EXACT prompter-side reading-frame geometry loop that
 * PrompterViewport runs every frame, driven by the REAL PlaybackLayoutCalculator + engine
 * scroll offsets:
 *
 *   contentOffset = startOffset + (endOffset - startOffset) * progress
 *   anchorLocalY  = contentHeightPx * anchorFraction - contentOffset
 *   cursor        = PlaybackReadingTracker.computeCursor(layout, anchorLocalY, textRevision)
 *   window        = windowManager.update(layout.text, textRevision, cursor.absoluteOffset)
 *
 * This is the ONE coupling not covered by existing unit tests (tracker, manager and the
 * repo/transport end-to-end are each tested in isolation). It answers the device question:
 * for a realistic script/viewport, AT WHAT progress does window revision 1 slide to rev2,
 * rev3, ... and is a slide expected while the reported progress is only ~13%?
 */
class ReadingGeometryProgressionDiagTest {

    private val statusHeightPx = 64f
    private val contentHeightPx = 800f
    private val viewportPx = statusHeightPx + contentHeightPx
    private val lineHeightPx = 30f
    /** Approx Chinese chars per visual line at a phone width / 25sp. */
    private val charsPerLine = 28

    private fun canonicalText(length: Int): String =
        (0 until length).joinToString("") { index ->
            if (index > 0 && index % 25 == 0) "\n" else "文"
        }

    /**
     * Builds a layout where every visual line holds [charsPerLine] chars at [lineHeightPx] px,
     * one line per row (a coarse but monotonic model of the real text layout). Line *ends* are
     * clamped to the text length; trailing blank wrap-lines are dropped.
     */
    private fun layoutFor(text: String): FakeReadingLayout {
        val lineCount = ceil(text.length.toDouble() / charsPerLine).toInt().coerceAtLeast(1)
        val lines = (0 until lineCount).map { i ->
            val start = i * charsPerLine
            val end = (start + charsPerLine).coerceAtMost(text.length)
            FakeVisualLine(i * lineHeightPx, (i + 1) * lineHeightPx, start, end)
        }
        return FakeReadingLayout(lines, text.length)
    }

    /** Walks progress 0→1 and records exactly when each new window revision is produced. */
    private fun slideTrace(
        textLength: Int,
        anchor: PlaybackReadingAnchor,
        step: Double = 0.005,
    ): List<Triple<Double, ReadingWindow, Double>> {
        val text = canonicalText(textLength)
        val layout = layoutFor(text)
        val textHeight = layout.lineCount * lineHeightPx
        val metrics = PlaybackLayoutCalculator.calculate(
            viewportHeightPx = viewportPx,
            textHeightPx = textHeight,
            mode = PlaybackLayoutMode.LivePlayback,
            readingAnchor = anchor,
            lineHeightPx = lineHeightPx,
        )
        val textRevision = 1L
        val manager = ReadingWindowManager()
        val events = mutableListOf<Triple<Double, ReadingWindow, Double>>()
        var lastRevision = -1L

        var progress = 0.0
        while (progress <= 1.0) {
            val contentOffset = metrics.startOffsetPx + (metrics.endOffsetPx - metrics.startOffsetPx) * progress.toFloat()
            val anchorViewportY = contentHeightPx * anchor.viewportFraction.coerceIn(0f, 1f)
            val anchorLocalY = anchorViewportY - contentOffset
            val cursor = PlaybackReadingTracker.computeCursor(layout, anchorLocalY, textRevision)
            val window = manager.update(text, textRevision, cursor.absoluteOffset)
            if (window.revision != lastRevision) {
                lastRevision = window.revision
                val span = (window.endOffset - window.startOffset).coerceAtLeast(1)
                val ratio = (cursor.absoluteOffset - window.startOffset) / span
                events.add(Triple(progress, window, ratio))
            }
            progress += step
        }
        return events
    }

    private fun report(label: String, trace: List<Triple<Double, ReadingWindow, Double>>) {
        println("==== $label ====")
        println("window-slide progress | rev | start..end | cursor-ratio-at-slide")
        for ((p, w, ratio) in trace) {
            println(
                "  progress=${"%.3f".format(p)}  rev=${w.revision}  " +
                    "start=${w.startOffset} end=${w.endOffset}  ratio@slide=${"%.3f".format(ratio)}",
            )
        }
        println("total windows: ${trace.size}")
    }

    @Test
    fun guideOff_1500charScript_traceWindowSlides() {
        val anchor = PlaybackReadingAnchor(viewportFraction = 0.25f, initialTextOffsetLines = 0f, durationMillis = 0L, normalDurationSeconds = 0)
        val trace = slideTrace(textLength = 1500, anchor = anchor)
        report("guide-off / 1500 chars / progress 0..1", trace)
        // At the reported device state (~13% progress) no slide is expected unless the trace
        // shows a rev-2 slide at or below 13%.
        val at13 = trace.firstOrNull { it.first >= 0.13 }
        println("first slide >= 13% progress: ${at13?.let { "rev ${it.second.revision} at ${it.first}" } ?: "none"}")
        assertTrue("window must produce at least the initial window", trace.size >= 1)
    }

    @Test
    fun guideOff_5000charScript_traceWindowSlides() {
        val anchor = PlaybackReadingAnchor(viewportFraction = 0.25f, initialTextOffsetLines = 0f, durationMillis = 0L, normalDurationSeconds = 0)
        val trace = slideTrace(textLength = 5000, anchor = anchor)
        report("guide-off / 5000 chars / progress 0..1", trace)
        // Long scripts must reach the document end: the last window endOffset == text length.
        assertTrue("long script must reach a terminal window", trace.last().second.endOffset == 5000)
    }

    @Test
    fun guideOn_1500charScript_traceWindowSlides() {
        val anchor = PlaybackReadingAnchor(viewportFraction = 0.5f, initialTextOffsetLines = 1.5f, durationMillis = 0L, normalDurationSeconds = 0)
        val trace = slideTrace(textLength = 1500, anchor = anchor)
        report("guide-on / 1500 chars / progress 0..1", trace)
        assertTrue(trace.size >= 1)
    }

    /**
     * DIRECT REPRO of the churn defect discovered in [slideTrace]: while the cursor ratio is
     * below the backward threshold (0.18) and the window is already pinned at the document
     * start, [ReadingWindowManager.update] rebuilds the window EVERY call and increments the
     * revision even though the range is byte-for-byte identical. A caller that treats
     * "windowRevision changed" as "the window slid" will therefore emit (and the controller
     * will re-render) dozens of identical windows before the first real slide.
     */
    @Test
    fun backwardRebuildChurnsRevisionWhileWindowCantMove() {
        val text = canonicalText(1500)
        val manager = ReadingWindowManager()
        val w1 = manager.update(text, textRevision = 1L, absoluteCursor = 0.0)
        val same = mutableListOf<ReadingWindow>()
        // Cursor walks up to just below the 0.18 backward threshold (0.18 * 701 ≈ 126).
        for (cursor in 0..120L step 10L) {
            val w = manager.update(text, textRevision = 1L, absoluteCursor = cursor.toDouble())
            if (w.revision != w1.revision) same.add(w)
        }
        assertTrue("churn must produce multiple revisions", same.size >= 3)
        val allIdenticalRange = same.all { it.startOffset == w1.startOffset && it.endOffset == w1.endOffset }
        assertTrue(
            "BUG CONFIRMED: revisions ${same.map { it.revision }} all have range " +
                "${w1.startOffset}..${w1.endOffset} but the manager returned a NEW window each call",
            allIdenticalRange,
        )
    }

    @Test
    fun geometryMatchesEngineStartLine() {
        // Sanity: at progress 0 with guide-off, the anchor sits near the FIRST line (top of the
        // text is pushed just below the anchor), so the cursor starts near 0.
        val text = canonicalText(1500)
        val layout = layoutFor(text)
        val metrics = PlaybackLayoutCalculator.calculate(
            viewportHeightPx = viewportPx,
            textHeightPx = layout.lineCount * lineHeightPx,
            mode = PlaybackLayoutMode.LivePlayback,
            readingAnchor = PlaybackReadingAnchor(0.25f, 0f, 0L, 0),
            lineHeightPx = lineHeightPx,
        )
        val contentOffset = metrics.startOffsetPx
        val anchorLocalY = contentHeightPx * 0.25f - contentOffset
        val cursor = PlaybackReadingTracker.computeCursor(layout, anchorLocalY, 1L)
        assertTrue("cursor at progress 0 must be near the text start, got ${cursor.absoluteOffset}", cursor.absoluteOffset < charsPerLine * 2.0)
    }
}
