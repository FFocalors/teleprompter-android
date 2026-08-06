package com.zhy20.teleprompter.feature.prompter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackNearbyTextWindowTest {

    private val fullText = "第一行内容" + "\n" +
        "第二行内容" + "\n" +
        "第三行内容" + "\n" +
        "第四行内容" + "\n" +
        "第五行内容" + "\n" +
        "第六行内容"

    /** Each visual line is one paragraph in this synthetic layout. */
    private val lines = fullText.split('\n').let { parts ->
        var cursor = 0
        parts.map { part ->
            val range = VisualLineRange(cursor, cursor + part.length)
            cursor += part.length + 1 // +1 for the '\n'
            range
        }
    }

    private fun window(anchor: Int, windowLines: Int = 3) =
        selectNearbyTextWindow(fullText, lines, anchor, windowLines)

    @Test
    fun firstLineShowsFirstThreeLines() {
        val state = window(anchor = 0)
        assertEquals(0, state!!.anchorLineIndex)
        assertEquals("第一行内容\n第二行内容\n第三行内容", state.text)
    }

    @Test
    fun secondLineShowsFirstThreeLinesCenteredOnSecond() {
        val state = window(anchor = 1)
        assertEquals(1, state!!.anchorLineIndex)
        assertEquals("第一行内容\n第二行内容\n第三行内容", state.text)
    }

    @Test
    fun middleLineIsCentered() {
        val state = window(anchor = 3)
        assertEquals(3, state!!.anchorLineIndex)
        assertEquals("第三行内容\n第四行内容\n第五行内容", state.text)
    }

    @Test
    fun secondToLastLineIsCentered() {
        val state = window(anchor = 4)
        assertEquals(4, state!!.anchorLineIndex)
        assertEquals("第四行内容\n第五行内容\n第六行内容", state.text)
    }

    @Test
    fun lastLineShowsLastThreeLines() {
        val state = window(anchor = 5)
        assertEquals(5, state!!.anchorLineIndex)
        assertEquals("第四行内容\n第五行内容\n第六行内容", state.text)
    }

    @Test
    fun singleLineText() {
        val state = selectNearbyTextWindow("只有一行", listOf(VisualLineRange(0, 4)), anchorLineIndex = 0)
        assertEquals("只有一行", state!!.text)
    }

    @Test
    fun twoLineTextClampsToBothLines() {
        val text = "甲\n乙"
        val ranges = listOf(VisualLineRange(0, 1), VisualLineRange(2, 3))
        val state = selectNearbyTextWindow(text, ranges, anchorLineIndex = 1)
        assertEquals("甲\n乙", state!!.text)
    }

    @Test
    fun emptyTextReturnsNull() {
        assertNull(selectNearbyTextWindow("", listOf(VisualLineRange(0, 0)), anchorLineIndex = 0))
    }

    @Test
    fun emptyLinesReturnsNull() {
        assertNull(selectNearbyTextWindow("abc", emptyList(), anchorLineIndex = 0))
    }

    @Test
    fun overlongTextIsTruncated() {
        val longLine = "字".repeat(100)
        val state = selectNearbyTextWindow(longLine, listOf(VisualLineRange(0, 100)), anchorLineIndex = 0, maxChars = 30)
        assertEquals(30, state!!.text.length)
        assertEquals("字".repeat(30), state.text)
    }

    @Test
    fun anchorOutOfRangeIsClamped() {
        val state = window(anchor = 99)
        assertEquals(5, state!!.anchorLineIndex)
        assertEquals("第四行内容\n第五行内容\n第六行内容", state.text)
    }

    @Test
    fun paragraphBlankLinesArePreservedWithinWindow() {
        val text = "甲\n\n\n乙"
        val ranges = listOf(
            VisualLineRange(0, 1),
            VisualLineRange(2, 2), // empty visual line
            VisualLineRange(3, 3), // empty visual line
            VisualLineRange(4, 5),
        )
        val state = selectNearbyTextWindow(text, ranges, anchorLineIndex = 3, windowLines = 5)
        assertEquals("甲\n\n\n乙", state!!.text)
    }

    @Test
    fun whitespaceOnlyWindowIsTrimmedToNull() {
        val text = "   \n   "
        val ranges = listOf(VisualLineRange(0, 3), VisualLineRange(4, 7))
        val state = selectNearbyTextWindow(text, ranges, anchorLineIndex = 0)
        assertNull(state)
    }

    @Test
    fun englishLongParagraphWrappingIsHandledByVisualLines() {
        // A single paragraph wrapped into 4 visual lines: the window follows the visual
        // lines, not paragraph boundaries. Ranges are derived from the real text so the
        // expected window cannot drift from hand-counted offsets.
        val text = "one two three four five six seven eight nine ten"
        val lineTexts = listOf(
            "one two thr",    // wraps mid-word
            "ee four five",
            " six seven ei",
            "ght nine ten",
        )
        val ranges = buildList {
            var cursor = 0
            lineTexts.forEach { line ->
                add(VisualLineRange(cursor, cursor + line.length))
                cursor += line.length
            }
        }
        val state = selectNearbyTextWindow(text, ranges, anchorLineIndex = 1, windowLines = 3)
        assertEquals(1, state!!.anchorLineIndex)
        assertEquals("one two thr\nee four five\n six seven ei", state.text)
    }
}
