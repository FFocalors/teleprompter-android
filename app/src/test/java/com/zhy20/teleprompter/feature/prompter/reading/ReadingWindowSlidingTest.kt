package com.zhy20.teleprompter.feature.prompter.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §24 regression: the sliding reading window must keep advancing for a long script — the window
 * is never "created once and frozen". As the absolute cursor crosses the forward threshold the
 * manager must produce a NEW window (revision+1) whose range still covers the cursor, all the
 * way to the document end.
 */
class ReadingWindowSlidingTest {

    /** 5000-char canonical text with newlines so paragraph alignment behaves realistically. */
    private fun canonicalText(length: Int): String =
        (0 until length).joinToString("") { index ->
            if (index > 0 && index % 25 == 0) "\n" else "文"
        }

    @Test
    fun longScriptProducesManyWindowsAndEveryCursorStaysCovered() {
        val text = canonicalText(5000)
        val manager = ReadingWindowManager()
        val seenRevisions = sortedSetOf<Long>()
        var lastWindow: ReadingWindow? = null

        for (cursor in 0L..4500L step 100L) {
            val w = manager.update(text, textRevision = 7L, absoluteCursor = cursor.toDouble())
            seenRevisions.add(w.revision)
            // Every returned window must cover the cursor that produced it.
            assertTrue(
                "cursor $cursor must be inside window ${w.revision} (${w.startOffset}..${w.endOffset})",
                cursor >= w.startOffset && cursor <= w.endOffset,
            )
            // No window may leave the document bounds.
            assertTrue(w.endOffset <= text.length)
            if (lastWindow != null) {
                // Revisions never go backwards.
                assertTrue(w.revision >= lastWindow!!.revision)
            }
            lastWindow = w
        }

        // The window must actually slide: more than one revision for a 4500-char walk.
        assertTrue("expected many window revisions, got ${seenRevisions.size}", seenRevisions.size >= 3)
        // Revisions are strictly increasing across distinct windows.
        val list = seenRevisions.toList()
        for (i in 1 until list.size) assertTrue(list[i] > list[i - 1])
    }

    @Test
    fun cursorNeverStraysBeyondTheWindowEndForContinuousPlayback() {
        val text = canonicalText(5000)
        val manager = ReadingWindowManager()
        var w = manager.update(text, 1, 0.0)
        var cursor = 0.0
        while (cursor < 4500.0) {
            cursor += 17.3 // smooth continuous advance
            w = manager.update(text, 1, cursor)
            // The window must always contain the cursor — never let it lag behind into a gap.
            assertTrue(
                "cursor $cursor escaped window ${w.revision} (${w.startOffset}..${w.endOffset})",
                cursor >= w.startOffset && cursor <= w.endOffset,
            )
        }
    }

    @Test
    fun documentEndProducesTerminalWindowAndStopsSliding() {
        val text = canonicalText(850)
        val manager = ReadingWindowManager()
        var w = manager.update(text, 1, 0.0)
        var cursor = 0.0
        var terminalRevision = 0L
        while (cursor <= 850.0) {
            cursor += 50.0
            val next = manager.update(text, 1, cursor)
            if (next !== w) {
                // Only forward windows are allowed.
                assertTrue(next.revision > w.revision)
                w = next
                if (w.endOffset == text.length) terminalRevision = w.revision
            }
        }
        // The last window reaches the document end.
        assertTrue("expected a terminal window touching the end", terminalRevision > 0)
        // Cursor walks to the very end while staying covered.
        assertTrue(850.0 >= w.startOffset && 850.0 <= w.endOffset)
    }

    @Test
    fun rapid2xAdvanceStillKeepsEveryCursorCovered() {
        // §25: high-frequency cursor growth (2x speed) must still produce timely windows; the
        // cursor never permanently escapes the current window.
        val text = canonicalText(5000)
        val manager = ReadingWindowManager()
        var w = manager.update(text, 1, 0.0)
        var cursor = 0.0
        while (cursor < 4500.0) {
            cursor += 6.0 // ~10x faster than a normal tick, simulating 2x playback of a long script
            w = manager.update(text, 1, cursor)
            assertTrue(
                "cursor $cursor escaped window ${w.revision} (${w.startOffset}..${w.endOffset})",
                cursor >= w.startOffset && cursor <= w.endOffset,
            )
        }
        assertTrue(w.endOffset <= text.length)
    }

    @Test
    fun backwardSeekBuildsACoveringWindowWithContextBehind() {
        // §26: seek backward from deep in the document must immediately produce a new window
        // covering the new cursor (windowRevision increments, range moves behind).
        val text = canonicalText(5000)
        val manager = ReadingWindowManager()
        val forward = manager.update(text, 1, 1400.0)
        assertTrue(forward.revision >= 1)

        val afterSeek = manager.update(text, 1, 400.0)
        assertTrue(afterSeek.revision > forward.revision)
        assertTrue("window must cover the seeked cursor", 400.0 >= afterSeek.startOffset && 400.0 <= afterSeek.endOffset)
    }

    @Test
    fun bigForwardSeekJumpsDirectlyToTheTargetWindow() {
        // §27: a large forward seek from cursor ~200 to ~4200 must produce ONE window covering
        // 4200 — the manager must not step through intermediate windows.
        val text = canonicalText(5000)
        val manager = ReadingWindowManager()
        val early = manager.update(text, 1, 200.0)
        val jumped = manager.update(text, 1, 4200.0)
        assertTrue(jumped.revision > early.revision)
        assertTrue("window must cover the jumped cursor", 4200.0 >= jumped.startOffset && 4200.0 <= jumped.endOffset)
        // The window start is near the cursor (front ratio), not back near the old position.
        assertTrue("window must start near the jumped cursor", jumped.startOffset > 3000)
    }

    @Test
    fun sameTextRevisionWindowsStillSlideWithHigherWindowRevisions() {
        // §28 / §9 regression: an unchanged canonical text (same textRevision) must NOT stop
        // the window from sliding — every new window gets a strictly higher windowRevision.
        val text = canonicalText(5000)
        val manager = ReadingWindowManager()
        val seenRevisions = mutableListOf<Long>()
        var w = manager.update(text, textRevision = 3, absoluteCursor = 0.0)
        seenRevisions.add(w.revision)
        var cursor = 0.0
        while (cursor < 4500.0) {
            cursor += 120.0
            val next = manager.update(text, textRevision = 3, absoluteCursor = cursor)
            if (next !== w) {
                w = next
                seenRevisions.add(w.revision)
            }
        }
        assertTrue(seenRevisions.size >= 3)
        for (i in 1 until seenRevisions.size) {
            assertTrue("window revisions must strictly increase: $seenRevisions", seenRevisions[i] > seenRevisions[i - 1])
        }
        // All windows share the same text revision.
        assertEquals(3L, w.textRevision)
    }
}
