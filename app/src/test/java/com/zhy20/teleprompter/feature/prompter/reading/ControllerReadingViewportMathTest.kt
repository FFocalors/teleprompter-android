package com.zhy20.teleprompter.feature.prompter.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers §23 (sub-character mapping) and §40.8–§40.12 (continuous translation, anchor hold,
 * window-switch continuity, seek snap): the absolute cursor is mapped through the controller's
 * OWN layout to a continuous Y, and the text is translated so that Y sits at the reading anchor.
 */
class ControllerReadingViewportMathTest {

    private val anchorFraction = 0.28f
    private val viewportHeight = 120f
    private val anchorY = viewportHeight * anchorFraction // 33.6

    // Each visual line is 20px tall; text ranges are contiguous.
    private fun layout(
        lines: List<Pair<Int, Int>>, // (start, endExclusive)
        lineHeightPx: Float = 20f,
    ): FakeReadingLayout = FakeReadingLayout(
        lines.mapIndexed { index, (start, end) ->
            FakeVisualLine(
                top = index * lineHeightPx,
                bottom = (index + 1) * lineHeightPx,
                start = start,
                end = end,
            )
        },
        length = lines.lastOrNull()?.second ?: 0,
    )

    private fun cursorY(layout: FakeReadingLayout, line: Int, relative: Double): Float {
        val top = layout.lineTop(line)
        val bottom = layout.lineBottom(line)
        val start = layout.lineStart(line)
        val end = layout.lineEnd(line)
        val progress = if (end <= start) 0f else ((relative - start) / (end - start)).toFloat().coerceIn(0f, 1f)
        return top + progress * (bottom - top)
    }

    @Test
    fun safeFloorOffsetClampsAndAvoidsLowSurrogates() {
        val text = "ab🙂cd" // 0:a 1:b 2:high 3:low 4:c 5:d
        assertEquals(0, ControllerReadingViewportMath.safeFloorOffset(-5.0, text))
        assertEquals(6, ControllerReadingViewportMath.safeFloorOffset(999.0, text))
        // Floored index 3 is a low surrogate -> back up to 2 so a pair is never split.
        assertEquals(2, ControllerReadingViewportMath.safeFloorOffset(3.4, text))
        // A normal index is unchanged.
        assertEquals(4, ControllerReadingViewportMath.safeFloorOffset(4.9, text))
    }

    @Test
    fun findLineForOffsetPicksTheCorrectVisualLine() {
        val l = layout(listOf(0 to 10, 11 to 21, 22 to 32))
        assertEquals(0, ControllerReadingViewportMath.findLineForOffset(l, 0))
        assertEquals(0, ControllerReadingViewportMath.findLineForOffset(l, 9))
        assertEquals(1, ControllerReadingViewportMath.findLineForOffset(l, 11))
        assertEquals(2, ControllerReadingViewportMath.findLineForOffset(l, 30))
        // Offset at/past the text end maps to the last line.
        assertEquals(2, ControllerReadingViewportMath.findLineForOffset(l, 32))
        assertEquals(2, ControllerReadingViewportMath.findLineForOffset(l, 99))
    }

    @Test
    fun targetTranslationHoldsCursorAtAnchorY() {
        val l = layout(listOf(0 to 10, 11 to 21, 22 to 32))
        val relative = 6.0 // line 0, progress 0.6 -> cursorY 12
        val line = 0
        val target = ControllerReadingViewportMath.targetTranslationY(
            layout = l,
            windowStart = 0,
            windowText = "0123456789",
            absoluteCursor = relative,
            viewportHeight = viewportHeight,
            anchorFraction = anchorFraction,
        )
        val expectedCursorY = cursorY(l, line, relative)
        assertEquals(anchorY, target + expectedCursorY, 1e-3f)
    }

    @Test
    fun subCharacterOffsetsProduceContinuousTranslation() {
        // Within a single line, moving the cursor by fractions of a character must move the
        // translation continuously (no whole-line quantization).
        val l = layout(listOf(0 to 100))
        val text = "x".repeat(100)
        val targets = (0..20).map { i ->
            ControllerReadingViewportMath.targetTranslationY(
                layout = l,
                windowStart = 0,
                windowText = text,
                absoluteCursor = 10.0 + i * 1.7,
                viewportHeight = viewportHeight,
                anchorFraction = anchorFraction,
            )
        }
        targets.forEach { assertTrue(it.isFinite()) }
        for (i in 1 until targets.size) {
            assertTrue("target should advance downward as cursor moves: $i", targets[i] < targets[i - 1])
        }
    }

    @Test
    fun sameAbsoluteCursorStaysAtAnchorAfterWindowSwitch() {
        // Two overlapping windows (A: chars 0..40, B: chars 30..70) with the same absolute
        // cursor (40): both must place that cursor at the same anchor Y (visual continuity
        // across the window slide).
        val text = (0 until 70).joinToString("") { "x" }
        val layoutA = layout(listOf(0 to 20, 20 to 40), lineHeightPx = 20f) // window A text 0..40
        val layoutB = layout(listOf(0 to 20, 20 to 40), lineHeightPx = 20f) // window B text 30..70
        val absolute = 40.0
        val targetA = ControllerReadingViewportMath.targetTranslationY(
            layout = layoutA, windowStart = 0, windowText = text.substring(0, 40),
            absoluteCursor = absolute, viewportHeight = viewportHeight, anchorFraction = anchorFraction,
        )
        val targetB = ControllerReadingViewportMath.targetTranslationY(
            layout = layoutB, windowStart = 30, windowText = text.substring(30, 70),
            absoluteCursor = absolute, viewportHeight = viewportHeight, anchorFraction = anchorFraction,
        )
        // In each window the cursor Y must land on the anchor: target + cursorY == anchorY.
        val relA = (absolute - 0).coerceIn(0.0, 40.0)
        val relB = (absolute - 30).coerceIn(0.0, 40.0)
        val lineA = ControllerReadingViewportMath.findLineForOffset(layoutA, ControllerReadingViewportMath.safeFloorOffset(relA, text.substring(0, 40)))
        val lineB = ControllerReadingViewportMath.findLineForOffset(layoutB, ControllerReadingViewportMath.safeFloorOffset(relB, text.substring(30, 70)))
        assertEquals(anchorY, targetA + cursorY(layoutA, lineA, relA), 1e-3f)
        assertEquals(anchorY, targetB + cursorY(layoutB, lineB, relB), 1e-3f)
    }

    @Test
    fun cursorBeforeWindowStartClampsToFirstLine() {
        val l = layout(listOf(0 to 10, 11 to 21))
        val target = ControllerReadingViewportMath.targetTranslationY(
            layout = l, windowStart = 20, windowText = "0123456789abcdefghij",
            absoluteCursor = 5.0, // 15 chars before the window -> clamp to 0
            viewportHeight = viewportHeight, anchorFraction = anchorFraction,
        )
        // Relative 0 -> line 0, cursorY 0 -> target == anchorY.
        assertEquals(anchorY, target, 1e-3f)
    }

    @Test
    fun emptyLayoutReturnsZeroTranslation() {
        val l = FakeReadingLayout(emptyList(), 0)
        val target = ControllerReadingViewportMath.targetTranslationY(
            layout = l, windowStart = 0, windowText = "",
            absoluteCursor = 10.0, viewportHeight = viewportHeight, anchorFraction = anchorFraction,
        )
        assertEquals(0f, target)
        assertFalse(target.isNaN())
    }
}
