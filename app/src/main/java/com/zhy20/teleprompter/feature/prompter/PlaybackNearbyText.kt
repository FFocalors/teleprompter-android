package com.zhy20.teleprompter.feature.prompter

import androidx.compose.ui.text.TextLayoutResult

/**
 * A small, immutable window of text around the visual line the guide line currently crosses.
 * This is derived from the real [TextLayoutResult] of the playback document — never from a
 * progress percentage or the static plain-text preview.
 */
data class PlaybackNearbyTextState(
    /** Index of the visual line the guide line crosses. */
    val anchorLineIndex: Int,
    /**
     * Plain text of the window. The character range is taken from the ORIGINAL text so
     * visual line boundaries (auto-wraps) are not inserted as newlines; only newlines that
     * actually exist in the source survive. The controller re-flows the text at its own width.
     */
    val text: String,
)

/**
 * One visual line's character range within the full text. Kept framework-free so the window
 * selection logic is testable on the JVM without a [TextLayoutResult].
 */
data class VisualLineRange(
    val start: Int,
    val endExclusive: Int,
)

/**
 * Pure selection of the nearby-text window around [anchorLineIndex] over a list of visual
 * line ranges. The returned text is a CONTIGUOUS slice of [fullText] from the first selected
 * line's start to the last selected line's end — visual line boundaries are never turned
 * into newline characters, so the controller never sees "line1\nline2\nline3" from
 * auto-wrapping. Only newlines already present in [fullText] (paragraph breaks and explicit
 * user line breaks) survive.
 *
 * @param windowLines how many lines to include around the anchor (default 3: prev/current/next).
 * @param maxChars hard cap on the returned text length.
 * @return null when the text is empty.
 */
fun selectNearbyTextWindow(
    fullText: String,
    lines: List<VisualLineRange>,
    anchorLineIndex: Int,
    windowLines: Int = 3,
    maxChars: Int = 220,
): PlaybackNearbyTextState? {
    if (lines.isEmpty() || fullText.isEmpty()) return null
    val anchor = anchorLineIndex.coerceIn(0, lines.size - 1)
    val last = lines.size - 1

    // Ideal window around the anchor, then shift to fit within [0, last] so the window keeps
    // its size at the start/end instead of shrinking.
    val half = windowLines / 2
    var start = anchor - half
    var end = anchor + (windowLines - half - 1)
    if (start < 0) {
        end += -start
        start = 0
    }
    if (end > last) {
        start -= (end - last)
        start = start.coerceAtLeast(0)
        end = last
    }

    val sliceStart = lines[start].start.coerceAtLeast(0)
    val sliceEnd = lines[end].endExclusive.coerceAtMost(fullText.length)
    if (sliceEnd <= sliceStart) return null

    // Contiguous slice: preserves only the source's own newlines.
    val raw = fullText.substring(sliceStart, sliceEnd).trim()
    if (raw.isEmpty()) return null

    val truncated = if (raw.length > maxChars) raw.substring(0, maxChars) else raw
    return PlaybackNearbyTextState(anchorLineIndex = anchor, text = truncated)
}

/**
 * Pure extraction of the nearby-text window from a real [TextLayoutResult].
 *
 * @param localGuideY the guide line's Y position in the text's local coordinates.
 * @param windowLines how many lines to include around the anchor (default 3: prev/current/next).
 * @param maxChars hard cap on the returned text length.
 */
fun extractNearbyTextWindow(
    textLayoutResult: TextLayoutResult,
    localGuideY: Float,
    windowLines: Int = 3,
    maxChars: Int = 220,
): PlaybackNearbyTextState? {
    val lineCount = textLayoutResult.lineCount
    if (lineCount <= 0) return null

    // The visual line the guide line crosses. Clamp so out-of-range Y still yields a line.
    val anchor = textLayoutResult.getLineForVerticalPosition(localGuideY).coerceIn(0, lineCount - 1)

    val ranges = List(lineCount) { line ->
        VisualLineRange(
            start = textLayoutResult.getLineStart(line),
            endExclusive = textLayoutResult.getLineEnd(line, visibleEnd = true),
        )
    }
    return selectNearbyTextWindow(
        fullText = textLayoutResult.layoutInput.text.toString(),
        lines = ranges,
        anchorLineIndex = anchor,
        windowLines = windowLines,
        maxChars = maxChars,
    )
}
