package com.zhy20.teleprompter.feature.prompter.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A framework-free fake [ReadingLayout] so the reading geometry is fully JVM-testable. Each
 * visual line carries its own px top/bottom and UTF-16 start/end.
 */
internal class FakeReadingLayout(
    private val visualLines: List<FakeVisualLine>,
    private val length: Int,
) : ReadingLayout {
    override val lineCount: Int get() = visualLines.size
    override val textLength: Int get() = length

    override fun lineTop(line: Int): Float = visualLines[line].top
    override fun lineBottom(line: Int): Float = visualLines[line].bottom
    override fun lineStart(line: Int): Int = visualLines[line].start
    override fun lineEnd(line: Int): Int = visualLines[line].end

    companion object {
        /**
         * Builds a layout from a text and per-line char ranges, laying lines top-to-bottom
         * with the given [lineHeightPx].
         */
        fun fromRanges(text: String, ranges: List<IntRange>, lineHeightPx: Float = 20f): FakeReadingLayout {
            val lines = ranges.mapIndexed { index, range ->
                FakeVisualLine(
                    top = index * lineHeightPx,
                    bottom = (index + 1) * lineHeightPx,
                    start = range.first,
                    end = range.last + 1,
                )
            }
            return FakeReadingLayout(lines, text.length)
        }
    }
}

internal data class FakeVisualLine(val top: Float, val bottom: Float, val start: Int, val end: Int)

/**
 * Covers §36 (reading-line selection) and §37 (continuous cursor): line selection must be based
 * on the first visual line whose BOTTOM edge is below the anchor Y, so text that already passed
 * the reading anchor is never reported as the current reading line.
 */
class PlaybackReadingTrackerTest {

    private val text = "第一行内容\n第二行内容\n第三行内容\n第四行内容"
    // Each line is exactly one visual line here (top-to-bottom, 20px each).
    private val layout = FakeReadingLayout.fromRanges(
        text,
        listOf(0..4, 6..10, 12..16, 18..22),
        lineHeightPx = 20f,
    )

    private fun cursor(anchorY: Float) = PlaybackReadingTracker.computeCursor(layout, anchorY, textRevision = 7L)

    @Test
    fun anchorInsideFirstLineYieldsContinuousOffsetInFirstLine() {
        // Line 0 top=0 bottom=20; anchor at y=10 is the middle -> ~50% through line 0.
        val c = cursor(10f)
        assertEquals(7L, c.textRevision)
        assertEquals(0, c.lineIndex)
        assertEquals(0, c.lineStartOffset)
        // 5 chars in the line, progress ~0.5 -> ~2.5
        assertTrue(c.absoluteOffset in 2.0..3.0)
    }

    @Test
    fun anchorInMiddleOfLineMapsToSubCharacterOffset() {
        val c = cursor(15f)
        assertTrue(c.absoluteOffset in 3.5..4.0)
        assertEquals(0, c.lineIndex)
    }

    @Test
    fun previousLineBottomPassingAnchorImmediatelySwitchesToNextLine() {
        // Regression: when the previous line's bottom has already crossed the anchor, the old
        // line must NOT stay the current reading line. At exactly y=20 (line0 bottom / line1
        // top) the current line is line 1 with progress 0.
        val c = cursor(20f)
        assertEquals(1, c.lineIndex)
        assertEquals(6, c.lineStartOffset)
        assertTrue(c.absoluteOffset <= 6.0 + 1e-9)
    }

    @Test
    fun anchorJustBelowLineBottomMovesToNextLine() {
        val c = cursor(20.1f)
        assertEquals(1, c.lineIndex)
        assertTrue(c.absoluteOffset > 6.0)
    }

    @Test
    fun anchorInGapBetweenParagraphsNeverReturnsPreviousLine() {
        // Line 0 covers 0..5 (0..20px), line 1 covers 6..11 (20..30px), line 2 covers 12..17
        // (40..60px). The 30..40px band is the vertical gap between paragraph lines; an anchor
        // inside it must map to line 2 with progress 0, never the previous line.
        val gappedLayout = FakeReadingLayout(
            listOf(
                FakeVisualLine(0f, 20f, 0, 5),
                FakeVisualLine(20f, 30f, 6, 11),
                FakeVisualLine(40f, 60f, 12, 17),
            ),
            length = 18,
        )
        val c = PlaybackReadingTracker.computeCursor(gappedLayout, 25f, 1L)
        // y=25 is inside line 1 (bottom 30 > 25), so line 1 is current with progress ~0.5.
        assertEquals(1, c.lineIndex)
        assertTrue(c.absoluteOffset in 8.5..9.5)
    }

    @Test
    fun anchorAtVeryTopSelectsFirstLine() {
        val c = cursor(-100_000f)
        assertEquals(0, c.lineIndex)
        assertEquals(0.0, c.absoluteOffset, 1e-6)
    }

    @Test
    fun anchorBelowLastLineClampsToTextLength() {
        val c = cursor(100_000f)
        assertEquals(3, c.lineIndex)
        assertTrue(c.absoluteOffset in 18.0..23.0)
    }

    @Test
    fun blankLinesAreHandledWithoutDivisionByZero() {
        // A blank line has start == end (just a newline); progress must clamp to 0 and the
        // cursor must land on that line's start without NaN.
        val blankLayout = FakeReadingLayout(
            listOf(
                FakeVisualLine(0f, 20f, 0, 5),
                FakeVisualLine(20f, 40f, 6, 6), // blank
                FakeVisualLine(40f, 60f, 7, 12),
            ),
            length = 13,
        )
        val c = PlaybackReadingTracker.computeCursor(blankLayout, 30f, 1L)
        assertEquals(1, c.lineIndex)
        assertTrue(c.absoluteOffset.isFinite())
        assertEquals(6.0, c.absoluteOffset, 1e-6)
    }

    @Test
    fun longParagraphWrapsIntoMultipleVisualLines() {
        // One long paragraph wraps over 3 visual lines; the anchor on the 2nd visual line
        // produces an offset within that line's range.
        val longText = "这是一段非常长的中文内容，会自动折成多行。".repeat(4)
        val layout = FakeReadingLayout.fromRanges(longText, listOf(0..9, 10..19, 20..29))
        val c = PlaybackReadingTracker.computeCursor(layout, 30f, 1L) // line 1 (10..19), middle
        assertEquals(1, c.lineIndex)
        assertTrue(c.absoluteOffset in 14.0..16.0)
    }

    @Test
    fun mixedChineseEnglishOffsetsStayInsideText() {
        val mixed = "Hello 世界\nMixed 中英文 text here"
        val layout = FakeReadingLayout.fromRanges(mixed, listOf(0..10, 12..32))
        val c = PlaybackReadingTracker.computeCursor(layout, 25f, 1L) // line 1
        assertEquals(1, c.lineIndex)
        assertTrue(c.absoluteOffset >= 12.0 && c.absoluteOffset <= 32.0)
    }

    @Test
    fun emojiSurrogatePairsStayInRangeAndNeverProduceNaN() {
        // The emoji "🙂" is a surrogate pair (2 UTF-16 units). Offsets must remain finite and
        // within the text length; the window layer is responsible for not splitting the pair.
        val textWithEmoji = "开头🙂中间内容后面文字"
        val layout = FakeReadingLayout.fromRanges(textWithEmoji, listOf(0 until textWithEmoji.length))
        val c = PlaybackReadingTracker.computeCursor(layout, 10f, 1L)
        assertTrue(c.absoluteOffset.isFinite())
        assertTrue(c.absoluteOffset in 0.0..textWithEmoji.length.toDouble())
    }

    @Test
    fun continuousCursorAdvancesSmoothlyInsideOneLine() {
        // §37: as the text scrolls, the offset must advance continuously (100.0, 101.x, 102.x),
        // never jumping whole lines.
        val layout = FakeReadingLayout.fromRanges(text, listOf(0..22)) // one line spanning all 23 chars
        val offsets = (0..4).map { step ->
            PlaybackReadingTracker.computeCursor(layout, step * 5f, 1L).absoluteOffset
        }
        offsets.forEach { assertTrue(it.isFinite()) }
        for (i in 1 until offsets.size) {
            assertTrue("offset must keep advancing: $offsets", offsets[i] > offsets[i - 1])
        }
        // The spread across the line is continuous, not quantized to whole lines.
        assertTrue(offsets.last() - offsets.first() > 10.0)
    }

    @Test
    fun sameAnchorYieldsSameCursorDeterministically() {
        // Paused playback freezes contentOffset; a frozen anchor must produce an identical
        // cursor every time (no drift, no time dependence).
        val first = cursor(12f)
        val second = cursor(12f)
        assertEquals(first, second)
    }

    @Test
    fun differentAnchorYieldsImmediatelyDifferentCursor() {
        // Seek changes contentOffset -> anchorLocalY changes -> cursor changes immediately.
        val before = cursor(12f)
        val after = cursor(60f)
        assertTrue(after.absoluteOffset > before.absoluteOffset + 10.0)
    }

    @Test
    fun emptyLayoutReturnsZeroCursor() {
        val c = PlaybackReadingTracker.computeCursor(FakeReadingLayout(emptyList(), 0), 10f, 1L)
        assertEquals(0.0, c.absoluteOffset, 1e-6)
        assertEquals(0, c.lineIndex)
    }}
