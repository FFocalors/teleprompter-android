package com.zhy20.teleprompter.feature.prompter

import androidx.compose.ui.text.TextLayoutResult

/**
 * @deprecated Legacy "visual-line nearby text" algorithm. Superseded as the reading source by
 *   [com.zhy20.teleprompter.feature.prompter.reading.PlaybackReadingTracker] (absolute reading
 *   cursor) + [com.zhy20.teleprompter.feature.prompter.reading.ReadingWindowManager] (large
 *   canonical window). The prompter viewport no longer calls these functions; they are kept only
 *   so the original window-selection tests continue to run. Do not reintroduce them into the
 *   controller display path.
 */

/**
 * A reading-text window produced by the playback text layout. The [text] is a contiguous
 * slice of the canonical annotated text (the same string the TextLayoutResult was built
 * from), so character offsets are always consistent — never applied to a re-derived plainText.
 *
 * @param anchorLineIndex the visual line the reading anchor currently crosses.
 * @param text the window text (contiguous slice, preserving only the source's own newlines).
 * @param activeStart absolute offset in [text] where the reading line begins.
 * @param activeEnd absolute offset in [text] where the reading line ends.
 * @param sourceStartOffset absolute offset in the full canonical text where [text] starts.
 * @param sourceEndOffset absolute offset in the full canonical text where [text] ends.
 * @param windowStartLineIndex first visual line covered by this window.
 * @param windowEndLineIndex last visual line covered by this window.
 */
data class PlaybackReadingWindow(
    val anchorLineIndex: Int,
    val text: String,
    val activeStart: Int,
    val activeEnd: Int,
    val sourceStartOffset: Int,
    val sourceEndOffset: Int,
    val windowStartLineIndex: Int,
    val windowEndLineIndex: Int,
)

/**
 * Lightweight, framework-free payload reported to the app layer and network: the window text
 * plus the current reading range relative to it, and the absolute source offsets. Never
 * contains a TextLayoutResult.
 */
data class PlaybackReadingTextUpdate(
    val text: String,
    val activeStart: Int,
    val activeEnd: Int,
    val sourceStartOffset: Int,
    val sourceEndOffset: Int,
)

/**
 * One visual line's character range within the full text. Kept framework-free so the window
 * selection logic is testable on the JVM without a [TextLayoutResult].
 */
data class VisualLineRange(
    val start: Int,
    val endExclusive: Int,
)

/** Max window text length. */
const val MAX_READING_WINDOW_CHARS = 360

/** Advance the window by this many visual lines when the reading line nears the back edge. */
private const val WINDOW_ADVANCE_LINES = 3

/**
 * Builds a reading window around [anchorLineIndex] over [lines], with a hysteresis strategy:
 *  - the window shows about 6 visual lines of context (start/end clamped);
 *  - when the anchor has already advanced into the window, the window keeps its text and only
 *    the active range changes (smooth, no whole-block replacement);
 *  - when a window is created fresh, the anchor sits near the front so the user can see what
 *    is coming; successive reads advance it until it nears the back, then a new window is
 *    created.
 *
 * The returned text is a CONTIGUOUS slice of [fullText] — visual line boundaries are never
 * turned into newlines, so the controller re-flows at its own width without single-character
 * orphans. Only newlines present in [fullText] survive.
 *
 * @param fullText the canonical annotated text (must equal the TextLayoutResult source).
 * @param lines visual line ranges from the same TextLayoutResult.
 * @param anchorLineIndex the visual line the reading anchor crosses.
 * @param windowLines how many visual lines to include in a fresh window (default 6).
 * @param maxChars hard cap on the window text length.
 * @return the window, or null when the document/line set is empty.
 */
fun buildReadingWindow(
    fullText: String,
    lines: List<VisualLineRange>,
    anchorLineIndex: Int,
    windowLines: Int = 6,
    maxChars: Int = MAX_READING_WINDOW_CHARS,
): PlaybackReadingWindow? {
    if (lines.isEmpty() || fullText.isEmpty()) return null
    val last = lines.size - 1
    val anchor = anchorLineIndex.coerceIn(0, last)

    // Fresh window: anchor near the front (about 1 line of look-back), then clamp.
    var start = (anchor - 1).coerceAtLeast(0)
    var end = start + (windowLines - 1)
    if (end > last) {
        start = (last - (windowLines - 1)).coerceAtLeast(0)
        end = last
    }

    val sliceStart = lines[start].start.coerceAtLeast(0)
    val sliceEnd = lines[end].endExclusive.coerceAtMost(fullText.length)
    if (sliceEnd <= sliceStart) return null

    // Active range = the anchor line's own character range, relative to the window.
    val activeSourceStart = lines[anchor].start
    val activeSourceEnd = lines[anchor].endExclusive
    val text = fullText.substring(sliceStart, sliceEnd)
    val activeStart = (activeSourceStart - sliceStart).coerceIn(0, text.length)
    val activeEnd = (activeSourceEnd - sliceStart).coerceIn(activeStart, text.length)
    if (text.isBlank()) return null

    val truncated = if (text.length > maxChars) text.substring(0, maxChars) else text
    return PlaybackReadingWindow(
        anchorLineIndex = anchor,
        text = truncated,
        activeStart = activeStart.coerceAtMost(truncated.length),
        activeEnd = activeEnd.coerceAtMost(truncated.length),
        sourceStartOffset = sliceStart,
        sourceEndOffset = sliceStart + truncated.length,
        windowStartLineIndex = start,
        windowEndLineIndex = end,
    )
}

/**
 * Advances the current window's active range when the reading line moved within the existing
 * window. When the reading line nears the back edge (or moves ahead of the window), a fresh
 * window is produced; otherwise the same [PlaybackReadingWindow] is returned with only the
 * active range updated — this is what keeps the controller from re-rendering the whole block
 * on every line crossing.
 */
fun advanceReadingWindow(
    fullText: String,
    lines: List<VisualLineRange>,
    current: PlaybackReadingWindow,
    newAnchorLineIndex: Int,
    windowLines: Int = 6,
    maxChars: Int = MAX_READING_WINDOW_CHARS,
): PlaybackReadingWindow {
    if (lines.isEmpty() || fullText.isEmpty()) return current
    val last = lines.size - 1
    val anchor = newAnchorLineIndex.coerceIn(0, last)
    val anchorLine = lines[anchor]

    // Still inside the window's line span? Then keep the window text and update only the
    // active range, but if the anchor is past ~70% of the window, slide a fresh window so the
    // reader always sees upcoming context.
    val inWindow = anchor in current.windowStartLineIndex..current.windowEndLineIndex
    if (inWindow) {
        val span = current.windowEndLineIndex - current.windowStartLineIndex
        val relative = anchor - current.windowStartLineIndex
        // Float comparison avoids integer-division truncation of the 70% threshold.
        val nearBack = span <= 0 || relative * 10 >= span * 7
        val activeStart = (anchorLine.start - current.sourceStartOffset).coerceIn(0, current.text.length)
        val activeEnd = (anchorLine.endExclusive - current.sourceStartOffset).coerceIn(activeStart, current.text.length)
        val updated = current.copy(
            anchorLineIndex = anchor,
            activeStart = activeStart,
            activeEnd = activeEnd,
        )
        if (!nearBack) return updated
    }
    return buildReadingWindow(fullText, lines, anchor, windowLines, maxChars) ?: current
}

/**
 * Extracts a reading window from a real [TextLayoutResult], using the given text-local Y as
 * the reading anchor. All offsets come from the same [TextLayoutResult] (and therefore the
 * same canonical annotated text) the visible text was rendered with.
 */
fun extractReadingWindow(
    textLayoutResult: TextLayoutResult,
    localAnchorY: Float,
    windowLines: Int = 6,
    maxChars: Int = MAX_READING_WINDOW_CHARS,
): PlaybackReadingWindow? {
    val lineCount = textLayoutResult.lineCount
    if (lineCount <= 0) return null
    val anchor = textLayoutResult.getLineForVerticalPosition(localAnchorY).coerceIn(0, lineCount - 1)
    val ranges = List(lineCount) { line ->
        VisualLineRange(
            start = textLayoutResult.getLineStart(line),
            endExclusive = textLayoutResult.getLineEnd(line, visibleEnd = true),
        )
    }
    return buildReadingWindow(
        fullText = textLayoutResult.layoutInput.text.toString(),
        lines = ranges,
        anchorLineIndex = anchor,
        windowLines = windowLines,
        maxChars = maxChars,
    )
}

/** Advances a window using a real [TextLayoutResult]; never escapes the layout layer. */
fun advanceReadingWindowFromLayout(
    textLayoutResult: TextLayoutResult,
    current: PlaybackReadingWindow,
    newAnchorLineIndex: Int,
    windowLines: Int = 6,
    maxChars: Int = MAX_READING_WINDOW_CHARS,
): PlaybackReadingWindow? {
    val lineCount = textLayoutResult.lineCount
    if (lineCount <= 0) return null
    val ranges = List(lineCount) { line ->
        VisualLineRange(
            start = textLayoutResult.getLineStart(line),
            endExclusive = textLayoutResult.getLineEnd(line, visibleEnd = true),
        )
    }
    return advanceReadingWindow(
        fullText = textLayoutResult.layoutInput.text.toString(),
        lines = ranges,
        current = current,
        newAnchorLineIndex = newAnchorLineIndex,
        windowLines = windowLines,
        maxChars = maxChars,
    )
}
