package com.zhy20.teleprompter.feature.prompter.reading

import androidx.compose.ui.text.TextLayoutResult

/**
 * A pure, framework-free view of a text layout's visual lines. Only the fields the reading
 * tracker needs, so the core algorithms are JVM-testable without a real [TextLayoutResult].
 *
 * All offsets are **UTF-16 code unit offsets** — the same indexing used by Kotlin [String]
 * and Compose [TextLayoutResult]. This is the one coordinate system shared by the prompter's
 * layout, the reading cursor, the reading window and the controller's own re-layout.
 */
interface ReadingLayout {
    val lineCount: Int
    val textLength: Int

    /** Top edge (px) of a visual line, in text-local coordinates. */
    fun lineTop(line: Int): Float

    /** Bottom edge (px) of a visual line, in text-local coordinates. */
    fun lineBottom(line: Int): Float

    /** UTF-16 offset of the first character of the line. */
    fun lineStart(line: Int): Int

    /** UTF-16 offset just past the last visible character of the line. */
    fun lineEnd(line: Int): Int
}

/** Adapts a real Compose [TextLayoutResult] into a [ReadingLayout]. */
class ComposeReadingLayout(
    private val result: TextLayoutResult,
) : ReadingLayout {
    override val lineCount: Int get() = result.lineCount
    override val textLength: Int get() = result.layoutInput.text.length

    override fun lineTop(line: Int): Float = result.getLineTop(line)
    override fun lineBottom(line: Int): Float = result.getLineBottom(line)
    override fun lineStart(line: Int): Int = result.getLineStart(line)
    override fun lineEnd(line: Int): Int = result.getLineEnd(line, visibleEnd = true)

    /** The canonical text this layout was built from. */
    val text: String get() = result.layoutInput.text.toString()
}

/**
 * A snapshot of the reading position expressed as a continuous absolute UTF-16 offset.
 *
 * The [absoluteOffset] is a [Double] so it can represent sub-character positions produced by
 * the real text motion (e.g. 186.2, 186.8, 187.4) instead of jumping whole lines. It is NOT a
 * claim that the speaker reads character-by-character at constant speed — it is simply the
 * reading position expressed in text coordinates, driven by the real playback geometry.
 */
data class ReadingCursorSample(
    val textRevision: Long,
    /** Continuous absolute UTF-16 offset in the canonical text, in `0.0 .. textLength`. */
    val absoluteOffset: Double,
    val lineIndex: Int,
    val lineStartOffset: Int,
    val lineEndOffset: Int,
)

/**
 * Computes the current reading line and a continuous absolute cursor from a real text layout.
 *
 * Line selection rule (the fix for "old text lingers after passing the anchor"):
 * the current reading line is the **first visual line whose bottom edge is below the reading
 * anchor Y**. When the anchor sits in the vertical gap between two lines (or two paragraphs),
 * the previous line is already "read" and the next line becomes current with a line progress
 * of 0 — the old line is never kept as the active reading line.
 *
 * The line lookup is a binary search over `lineBottom` (monotonic), so long documents do not
 * cost an O(N) scan per frame.
 */
object PlaybackReadingTracker {

    /**
     * @param layout the real text layout (canonical text offsets).
     * @param anchorLocalY the reading anchor Y in the text's local coordinates.
     * @param textRevision revision of the canonical text the layout was built from.
     */
    fun computeCursor(
        layout: ReadingLayout,
        anchorLocalY: Float,
        textRevision: Long,
    ): ReadingCursorSample {
        val length = layout.textLength
        if (layout.lineCount <= 0 || length <= 0) {
            return ReadingCursorSample(textRevision, 0.0, 0, 0, 0)
        }

        val line = findReadingLine(layout, anchorLocalY)
        val top = layout.lineTop(line)
        val bottom = layout.lineBottom(line)
        val lineHeight = bottom - top
        // If the anchor is in the gap above this line, lineProgress clamps to 0.
        val progress = if (lineHeight <= 0f) 0f else ((anchorLocalY - top) / lineHeight).coerceIn(0f, 1f)

        val start = layout.lineStart(line)
        val end = layout.lineEnd(line)
        val span = (end - start).coerceAtLeast(0)
        val offset = start + span * progress.toDouble()
        return ReadingCursorSample(
            textRevision = textRevision,
            absoluteOffset = offset.coerceIn(0.0, length.toDouble()),
            lineIndex = line,
            lineStartOffset = start,
            lineEndOffset = end,
        )
    }

    /** First visual line whose bottom edge is below [anchorLocalY]; binary search. */
    fun findReadingLine(
        layout: ReadingLayout,
        anchorLocalY: Float,
    ): Int {
        val count = layout.lineCount
        if (count <= 0) return 0
        var low = 0
        var high = count - 1
        var result = count
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (layout.lineBottom(mid) > anchorLocalY) {
                result = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return if (result == count) count - 1 else result
    }
}
