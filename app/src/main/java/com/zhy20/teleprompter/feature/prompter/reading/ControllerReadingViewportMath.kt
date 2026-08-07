package com.zhy20.teleprompter.feature.prompter.reading

import kotlin.math.floor

/**
 * Pure math mapping the prompter's absolute reading cursor onto the controller's **own**
 * [ReadingLayout]. Visual line indices are never shared across devices: the controller maps the
 * absolute UTF-16 offset through its local re-flow to a local cursor Y, then computes the text
 * translation that holds the cursor at the viewport's reading anchor. Framework-free so the
 * geometry is JVM-testable with a fake layout.
 */
object ControllerReadingViewportMath {

    /**
     * Clamps [offset] into `0..text.length` and rounds down to a UTF-16-safe Int that never
     * lands inside a surrogate pair.
     */
    fun safeFloorOffset(offset: Double, text: String): Int {
        var i = floor(offset).toInt().coerceIn(0, text.length)
        // If the floored index is a low surrogate, back up to its high surrogate so the offset
        // never splits a pair (substring/getLineForOffset stay well-formed).
        if (i > 0 && i < text.length && Character.isLowSurrogate(text[i])) i -= 1
        return i
    }

    /**
     * Finds the visual line containing [offset] via binary search over line start/end.
     * An offset at or past the text end returns the last line; an offset before the start
     * returns the first line.
     */
    fun findLineForOffset(layout: ReadingLayout, offset: Int): Int {
        val count = layout.lineCount
        if (count <= 0) return 0
        if (offset <= 0) return 0
        val length = layout.textLength
        if (offset >= length) return count - 1
        var low = 0
        var high = count - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = layout.lineStart(mid)
            val end = layout.lineEnd(mid)
            when {
                offset < start -> high = mid - 1
                offset >= end -> low = mid + 1
                else -> return mid
            }
        }
        return low.coerceIn(0, count - 1)
    }

    /**
     * The text `translationY` (px) that places the reading cursor at
     * `viewportHeight * anchorFraction` inside the controller viewport.
     *
     * The cursor's absolute offset becomes a relative offset within the window, is mapped to
     * the controller's own layout, and sub-character progress inside that line yields a
     * continuous Y (no whole-line jumps). Returns 0 for an empty/unavailable layout.
     */
    fun targetTranslationY(
        layout: ReadingLayout,
        windowStart: Int,
        windowText: String,
        absoluteCursor: Double,
        viewportHeight: Float,
        anchorFraction: Float,
    ): Float {
        if (viewportHeight <= 0f || layout.lineCount <= 0 || windowText.isEmpty()) return 0f
        val relative = (absoluteCursor - windowStart).coerceIn(0.0, windowText.length.toDouble())
        val base = safeFloorOffset(relative, windowText)
        val line = findLineForOffset(layout, base)
        val top = layout.lineTop(line)
        val bottom = layout.lineBottom(line)
        val lineHeight = bottom - top
        val start = layout.lineStart(line)
        val end = layout.lineEnd(line)
        val lineProgress = if (lineHeight <= 0f || end <= start) {
            0f
        } else {
            ((relative - start) / (end - start)).toFloat().coerceIn(0f, 1f)
        }
        val cursorY = top + lineProgress * lineHeight
        val anchorY = viewportHeight * anchorFraction.coerceIn(0f, 1f)
        return anchorY - cursorY
    }
}
