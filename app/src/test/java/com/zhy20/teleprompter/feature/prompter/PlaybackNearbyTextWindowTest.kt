package com.zhy20.teleprompter.feature.prompter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackNearbyTextWindowTest {

    private val fullText = "第一行内容" + "\n" +
        "第二行内容" + "\n" +
        "第三行内容" + "\n" +
        "第四行内容" + "\n" +
        "第五行内容" + "\n" +
        "第六行内容" + "\n" +
        "第七行内容" + "\n" +
        "第八行内容"

    /** Each visual line is one paragraph in this synthetic layout. */
    private val lines = fullText.split('\n').let { parts ->
        var cursor = 0
        parts.map { part ->
            val range = VisualLineRange(cursor, cursor + part.length)
            cursor += part.length + 1 // +1 for the '\n'
            range
        }
    }

    private fun window(anchor: Int, windowLines: Int = 6) =
        buildReadingWindow(fullText, lines, anchor, windowLines)

    @Test
    fun firstLineStartsAtDocumentBeginning() {
        val w = window(anchor = 0)
        assertNotNull(w)
        assertEquals(0, w!!.windowStartLineIndex)
        assertTrue(w.text.startsWith("第一行内容"))
        assertEquals(0, w.sourceStartOffset)
    }

    @Test
    fun activeRangeTracksTheAnchorLineInsideWindow() {
        val w = window(anchor = 2)
        assertNotNull(w)
        assertEquals(2, w!!.anchorLineIndex)
        // The active range is the anchor line's chars relative to the window.
        assertEquals("第三行内容", w.text.substring(w.activeStart, w.activeEnd))
    }

    @Test
    fun lastLineClampsWindowToDocumentEnd() {
        val w = window(anchor = 7)
        assertNotNull(w)
        assertEquals(7, w!!.anchorLineIndex)
        assertEquals("第八行内容", w.text.substring(w.activeStart, w.activeEnd))
        assertEquals(fullText.length, w.sourceEndOffset)
    }

    @Test
    fun windowShowsAboutSixVisualLinesOfContext() {
        val w = window(anchor = 3)
        assertNotNull(w)
        val covered = w!!.windowEndLineIndex - w.windowStartLineIndex + 1
        assertTrue(covered >= 5 && covered <= 6)
    }

    @Test
    fun anchorOutOfRangeIsClamped() {
        val w = window(anchor = 99)
        assertEquals(7, w!!.anchorLineIndex)
    }

    @Test
    fun emptyDocumentReturnsNull() {
        assertNull(buildReadingWindow("", listOf(VisualLineRange(0, 0)), anchorLineIndex = 0))
    }

    @Test
    fun emptyLinesReturnsNull() {
        assertNull(buildReadingWindow("abc", emptyList(), anchorLineIndex = 0))
    }

    @Test
    fun overlongTextIsTruncated() {
        val longLine = "字".repeat(100)
        val w = buildReadingWindow(longLine, listOf(VisualLineRange(0, 100)), anchorLineIndex = 0, maxChars = 40)
        assertEquals(40, w!!.text.length)
    }

    @Test
    fun advanceKeepsWindowTextWhileActiveRangeMovesInsideIt() {
        val first = window(anchor = 1, windowLines = 6)
        val second = advanceReadingWindow(fullText, lines, current = first!!, newAnchorLineIndex = 3)
        // Same window text (hysteresis), only the active range advanced.
        assertEquals(first.text, second.text)
        assertEquals(3, second.anchorLineIndex)
        assertEquals("第四行内容", second.text.substring(second.activeStart, second.activeEnd))
    }

    @Test
    fun advanceNearBackEdgeBuildsAFreshWindow() {
        val first = window(anchor = 0, windowLines = 6)
        // Jump far into the window (anchor near the back edge) -> fresh window slides forward.
        val advanced = advanceReadingWindow(fullText, lines, current = first!!, newAnchorLineIndex = 4)
        assertTrue(advanced.windowStartLineIndex > first.windowStartLineIndex)
    }

    @Test
    fun paragraphNewlinesArePreservedButAutoWrapBoundariesAreNot() {
        val text = "第一段涵盖了标点、数字等内容。" + "\n" + "第二段开始。"
        // The first paragraph auto-wraps into 3 visual lines; the paragraph boundary (real
        // '\n') stays between paragraphs.
        val ranges = listOf(
            VisualLineRange(0, 6),   // "第一段涵盖了"
            VisualLineRange(6, 12),  // "标点、数字等内"
            VisualLineRange(12, 17), // "容。"
            VisualLineRange(18, 23), // "第二段开始。"
        )
        val w = buildReadingWindow(text, ranges, anchorLineIndex = 1, windowLines = 4)
        assertNotNull(w)
        // Visual boundaries dropped; the real paragraph newline preserved.
        assertEquals("第一段涵盖了标点、数字等内容。\n第二段开始。", w!!.text)
    }

    @Test
    fun whitespaceOnlyWindowIsNull() {
        val text = "   \n   "
        val ranges = listOf(VisualLineRange(0, 3), VisualLineRange(4, 7))
        val w = buildReadingWindow(text, ranges, anchorLineIndex = 0)
        assertNull(w)
    }
}
