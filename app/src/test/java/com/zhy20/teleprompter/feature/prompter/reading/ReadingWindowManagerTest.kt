package com.zhy20.teleprompter.feature.prompter.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers §38 (ReadingWindowManager): window sizing, forward/backward hysteresis, paragraph and
 * surrogate-pair boundaries, and hard caps.
 */
class ReadingWindowManagerTest {

    /** ~50 paragraphs of ~28 chars each so windows never hit the document edge. */
    private fun longText(): String = (0 until 50).joinToString("\n") { line ->
        "第${line}段内容" + "啊".repeat(18)
    }

    @Test
    fun initialWindowStartsAtDocumentBeginning() {
        val manager = ReadingWindowManager()
        val w = manager.update(longText(), textRevision = 3, absoluteCursor = 0.0)
        assertEquals(1L, w.revision)
        assertEquals(3L, w.textRevision)
        assertEquals(0, w.startOffset)
        assertEquals(w.text, longText().substring(w.startOffset, w.endOffset))
    }

    @Test
    fun cursorInsideWindowKeepsTheSameInstance() {
        val manager = ReadingWindowManager()
        val first = manager.update(longText(), 1, 100.0)
        val same = manager.update(longText(), 1, 300.0) // still below the forward threshold
        assertSame(first, same)
        assertEquals(1L, first.revision)
    }

    @Test
    fun crossingForwardThresholdBuildsAFreshWindow() {
        val manager = ReadingWindowManager()
        val first = manager.update(longText(), 1, 100.0)
        val span = first.endOffset - first.startOffset
        val cursor = first.startOffset + span * 0.75
        val second = manager.update(longText(), 1, cursor.toDouble())
        assertNotSame(first, second)
        assertTrue(second.revision > first.revision)
        // The fresh window re-places the cursor near the front (~30%).
        val relative = (cursor - second.startOffset).toDouble() / (second.endOffset - second.startOffset)
        assertTrue("expected ~0.3, was $relative", relative in 0.15..0.45)
    }

    @Test
    fun backwardSeekBelowThresholdRebuildsWindowWithContextBehind() {
        val manager = ReadingWindowManager()
        val first = manager.update(longText(), 1, 700.0)
        val nearFront = first.startOffset + 5
        val second = manager.update(longText(), 1, nearFront.toDouble())
        assertNotSame(first, second)
        // Backward rebuild places the cursor near ~65% (room behind for what was just read).
        val relative = (nearFront - second.startOffset).toDouble() / (second.endOffset - second.startOffset)
        assertTrue("expected ~0.65, was $relative", relative in 0.45..0.85)
    }

    @Test
    fun seekBeforeWindowBuildsForwardFromCursor() {
        val manager = ReadingWindowManager()
        val first = manager.update(longText(), 1, 500.0)
        val seekBack = first.startOffset - 100.0
        val second = manager.update(longText(), 1, seekBack)
        assertNotSame(first, second)
        // The new window contains the cursor.
        assertTrue(second.startOffset <= seekBack && seekBack <= second.endOffset)
    }

    @Test
    fun cursorAtDocumentStartRebuildsWindowAtZero() {
        val manager = ReadingWindowManager()
        manager.update(longText(), 1, 400.0)
        val w = manager.update(longText(), 1, 0.0)
        assertEquals(0, w.startOffset)
    }

    @Test
    fun cursorAtDocumentEndClampsWindowToDocumentEnd() {
        val manager = ReadingWindowManager()
        val text = longText()
        val w = manager.update(text, 1, text.length.toDouble())
        assertEquals(text.length, w.endOffset)
    }

    @Test
    fun longParagraphIsCappedAtHardLimit() {
        val manager = ReadingWindowManager()
        val huge = "字".repeat(3000) // a single paragraph, no newline to align to
        val w = manager.update(huge, 1, 1500.0)
        assertTrue(w.endOffset - w.startOffset <= 1100)
        assertEquals(huge.substring(w.startOffset, w.endOffset), w.text)
    }

    @Test
    fun shortDocumentFitsEntirelyInWindow() {
        val manager = ReadingWindowManager()
        val text = "短台本内容。"
        val w = manager.update(text, 1, 0.0)
        assertEquals(0, w.startOffset)
        assertEquals(text.length, w.endOffset)
        assertEquals(text, w.text)
    }

    @Test
    fun emptyTextYieldsEmptyWindow() {
        val manager = ReadingWindowManager()
        val w = manager.update("", 1, 0.0)
        assertEquals(0, w.startOffset)
        assertEquals(0, w.endOffset)
        assertTrue(w.text.isEmpty())
    }

    @Test
    fun windowPreservesRealNewlines() {
        val manager = ReadingWindowManager()
        val text = longText()
        val w = manager.update(text, 1, 200.0)
        assertEquals(text.substring(w.startOffset, w.endOffset), w.text)
        // The window never injects or removes newlines.
        assertEquals(w.text.count { it == '\n' }, text.substring(w.startOffset, w.endOffset).count { it == '\n' })
    }

    @Test
    fun windowStartAlignsToParagraphBoundaryWithinLookback() {
        val manager = ReadingWindowManager()
        // "aa\nbbbbbbbbbbbbbbbbbbbbbbbbbb..." — raw start would land mid-paragraph; the
        // manager should pull it back to just after the newline when within lookback.
        val text = "aa\n" + "b".repeat(2000)
        val w = manager.update(text, 1, 100.0)
        if (w.startOffset > 0) {
            assertEquals('b', text[w.startOffset])
            assertTrue(w.startOffset <= 3 + 200) // within the lookback after the newline
        }
    }

    @Test
    fun surrogatePairsAreNeverSplit() {
        val manager = ReadingWindowManager()
        // Each "ab🙂" is 4 UTF-16 units (a, b, high, low).
        val text = "ab🙂".repeat(600)
        val w = manager.update(text, 1, 800.0)
        val start = w.startOffset
        val end = w.endOffset
        assertFalse("start must not be a low surrogate", start < text.length && Character.isLowSurrogate(text[start]))
        assertFalse("end must not be a low surrogate", end > start && end < text.length && Character.isLowSurrogate(text[end]))
        assertFalse("end must not be a lone high surrogate", end > start && end < text.length && Character.isHighSurrogate(text[end]))
        // The window text is a clean substring (no exception).
        assertEquals(text.substring(start, end), w.text)
    }

    @Test
    fun windowTextLengthStaysUnderHardCapForEmojiHeavyText() {
        val manager = ReadingWindowManager()
        val text = "🙂".repeat(3000)
        val w = manager.update(text, 1, 1500.0)
        assertTrue(w.endOffset - w.startOffset <= 1100 + 2) // +2 for a possible pair boundary
    }

    @Test
    fun resetClearsTheCurrentWindow() {
        val manager = ReadingWindowManager()
        manager.update(longText(), 1, 100.0)
        assertTrue(manager.current() != null)
        manager.reset()
        assertEquals(null, manager.current())
        // After reset the next update is a fresh window (revision keeps counting; a fresh
        // document gets a brand-new manager instance in the viewport).
        val w = manager.update(longText(), 1, 100.0)
        assertTrue(w.startOffset == 0)
    }

    @Test
    fun textRevisionChangeRebuildsEvenForSameCursor() {
        val manager = ReadingWindowManager()
        val first = manager.update(longText(), 1, 200.0)
        val second = manager.update(longText(), 2, 200.0)
        assertNotSame(first, second)
        assertEquals(2L, second.textRevision)
    }
}
