package com.zhy20.teleprompter.feature.prompter.reading

import com.zhy20.teleprompter.BuildConfig
import kotlin.math.max
import kotlin.math.min

/**
 * A window into the canonical text that the controller keeps as a local re-layout cache.
 *
 * This is NOT "the 5–6 lines shown on screen": it is a larger contiguous slice of the source
 * (typically 500–800 UTF-16 units) that the controller re-flows at its own width. Only the
 * absolute cursor is sent frequently; the window is refreshed rarely.
 *
 * All offsets are UTF-16 code unit offsets into the canonical text.
 */
data class ReadingWindow(
    val revision: Long,
    val textRevision: Long,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
) {
    init {
        require(endOffset >= startOffset)
        require(text.length == endOffset - startOffset)
    }
}

/**
 * Maintains the current [ReadingWindow] with a hysteresis policy so the window does not churn
 * on every cursor update:
 *
 *  - a fresh window places the cursor near the front (~30%);
 *  - while the cursor stays inside the window below the forward threshold (~70%), the window
 *    is unchanged;
 *  - once the cursor crosses the forward threshold, a new window is built with the cursor back
 *    near ~30%;
 *  - when the cursor falls behind the backward threshold (~18%), the window is rebuilt so the
 *    cursor sits near ~65% (giving room behind for what was just read).
 *
 * Window boundaries prefer a paragraph/newline boundary (to keep the controller's re-wrap
 * stable) and never split a UTF-16 surrogate pair.
 */
class ReadingWindowManager(
    private val targetChars: Int = 700,
    private val hardCapChars: Int = 1100,
    private val forwardRatio: Double = 0.72,
    private val backwardRatio: Double = 0.18,
    private val cursorFrontRatio: Double = 0.30,
    /** Cursor position after a backward rebuild, leaving room behind for what was just read. */
    private val cursorBackRatio: Double = 0.68,
) {
    private var revision = 0L

    private var window: ReadingWindow? = null

    // ---- DIAG (RemoteReadingDiag): debug-only, no behavior change ----

    private val DIAG_TAG = "RemoteReadingDiag"
    private var lastKeepDiagNanos = 0L

    /** Debug-only; never logs text content. No-op in release and in unit tests (Log not mocked). */
    private fun diag(message: String) {
        if (!BuildConfig.DEBUG) return
        try {
            android.util.Log.d(DIAG_TAG, message)
        } catch (_: RuntimeException) {
            // JVM unit tests do not mock android.util.Log
        }
    }

    /** Logs the real decision at the exact branch where it is made. KEEP is throttled to 500 ms. */
    private fun diagEval(decision: String, current: ReadingWindow?, cursor: Double) {
        if (!BuildConfig.DEBUG) return
        val span = ((current?.endOffset ?: 1) - (current?.startOffset ?: 0)).coerceAtLeast(1)
        val relative = cursor - (current?.startOffset ?: 0)
        val ratio = relative / span
        val now = System.nanoTime()
        if (!decision.startsWith("KEEP") || now - lastKeepDiagNanos >= 500_000_000L) {
            lastKeepDiagNanos = now
            diag(
                "WINDOW_EVAL rev=${current?.revision} start=${current?.startOffset} end=${current?.endOffset} " +
                    "cursor=$cursor relative=$relative ratio=${"%.3f".format(ratio)} " +
                    "forward=$forwardRatio backward=$backwardRatio decision=$decision",
            )
        }
    }

    /** Current window, or null before the first update. */
    fun current(): ReadingWindow? = window

    fun reset() {
        window = null
    }

    /**
     * Updates the window for the given absolute cursor. Returns a NEW window only when the
     * window actually changed; otherwise returns the existing instance so callers can cheaply
     * skip sending.
     */
    fun update(
        canonicalText: String,
        textRevision: Long,
        absoluteCursor: Double,
    ): ReadingWindow {
        val length = canonicalText.length
        val cursor = if (length <= 0) 0.0 else absoluteCursor.coerceIn(0.0, length.toDouble())
        if (length <= 0) {
            return buildWindow(
                text = canonicalText,
                textRevision = textRevision,
                cursor = cursor,
                length = length,
                cursorRatio = cursorFrontRatio,
                decision = "NEW",
            )
        }

        val current = window
        if (current != null && current.textRevision == textRevision) {
            val span = (current.endOffset - current.startOffset).coerceAtLeast(1)
            if (cursor >= current.startOffset && cursor <= current.endOffset) {
                val relative = (cursor - current.startOffset) / span
                if (relative < forwardRatio && relative > backwardRatio) {
                    diagEval("KEEP", current, cursor)
                    return current
                }
                // Forward-crowding -> rebuild with the cursor near the front; backward-crowding
                // -> rebuild with the cursor near 65% so the reader keeps context behind.
                val movingBackward = relative <= backwardRatio
                return buildWindow(
                    text = canonicalText,
                    textRevision = textRevision,
                    cursor = cursor,
                    length = length,
                    cursorRatio = if (movingBackward) cursorBackRatio else cursorFrontRatio,
                    decision = if (movingBackward) "REPLACE_BACKWARD" else "REPLACE_FORWARD",
                )
            }
            // The cursor jumped outside the current window (seek): forward jumps get room ahead,
            // backward jumps keep context behind.
            val seekingBackward = cursor < current.startOffset
            return buildWindow(
                text = canonicalText,
                textRevision = textRevision,
                cursor = cursor,
                length = length,
                cursorRatio = if (seekingBackward) cursorBackRatio else cursorFrontRatio,
                decision = if (seekingBackward) "SEEK_BACKWARD" else "SEEK_FORWARD",
            )
        }

        return buildWindow(
            text = canonicalText,
            textRevision = textRevision,
            cursor = cursor,
            length = length,
            cursorRatio = cursorFrontRatio,
            decision = "NEW",
        )
    }

    private fun buildWindow(
        text: String,
        textRevision: Long,
        cursor: Double,
        length: Int,
        cursorRatio: Double,
        decision: String,
    ): ReadingWindow {
        val cursorInt = cursor.toInt().coerceIn(0, length)
        // Ideal window: cursor placed at [cursorRatio] of the window.
        val rawStart = (cursorInt - (targetChars * cursorRatio).toInt()).coerceIn(0, length)
        val rawEnd = (rawStart + targetChars).coerceAtMost(length)

        var start = rawStart
        var end = rawEnd
        // Clamp to hard cap.
        if (end - start > hardCapChars) end = start + hardCapChars

        // Align the start forward to the next paragraph/newline boundary within a lookback,
        // so the controller's re-wrap at its own width is stable and windows never start in
        // the middle of a long paragraph. Look back only a bounded window to avoid growing the
        // window unboundedly on a single huge paragraph.
        val lookback = min(hardCapChars / 2, 200)
        val boundaryStart = findParagraphStart(text, start, max(0, start - lookback))
        if (boundaryStart >= 0) start = boundaryStart

        // Align the end backward to the previous newline boundary within a small lookahead.
        val lookahead = 120
        val boundaryEnd = findParagraphEnd(text, end, min(length, end + lookahead))
        if (boundaryEnd >= 0) end = boundaryEnd

        // Final clamps + surrogate safety.
        start = start.coerceIn(0, length)
        end = end.coerceIn(start, length)
        if (end - start > hardCapChars) end = start + hardCapChars
        start = safeUtf16Start(text, start)
        end = safeUtf16End(text, end, start)
        if (end <= start) {
            // Degenerate: keep a minimal window so the controller always has text.
            end = safeUtf16End(text, min(length, start + 64), start)
        }

        val previous = window
        if (previous != null &&
            previous.textRevision == textRevision &&
            previous.startOffset == start &&
            previous.endOffset == end
        ) {
            // Document-edge clamping can make a requested forward/backward replacement resolve
            // to exactly the current range. Keeping the existing instance is essential: callers
            // use its revision as the low-frequency network/layout identity.
            diagEval("KEEP_SAME_RANGE", previous, cursor)
            return previous
        }

        diagEval(decision, previous, cursor)
        val newRevision = ++revision
        val built = ReadingWindow(
            revision = newRevision,
            textRevision = textRevision,
            startOffset = start,
            endOffset = end,
            text = text.substring(start, end),
        )
        // DIAG: log only genuinely new windows. Same-range candidates return above without
        // allocating, incrementing the revision or reaching the network layer.
        diag(
            "WINDOW_CREATE oldRev=${previous?.revision} newRev=$newRevision " +
                "old=${previous?.startOffset}..${previous?.endOffset} new=$start..$end " +
                "cursor=$cursor textRev=$textRevision " +
                "sameRange=${previous != null && previous.startOffset == start && previous.endOffset == end}",
        )
        window = built
        return built
    }

    /** Returns the index just past the previous `\n` at or before [from], or [from] if none. */
    private fun findParagraphStart(text: String, from: Int, atLeast: Int): Int {
        var i = from
        while (i > atLeast) {
            i -= 1
            if (text[i] == '\n') return i + 1
        }
        return from
    }

    /** Returns the index of the next `\n` at or after [from], or [from] if none. */
    private fun findParagraphEnd(text: String, from: Int, atMost: Int): Int {
        var i = from
        while (i < atMost) {
            if (text[i] == '\n') return i + 1
            i += 1
        }
        return from
    }

    /** Never start a window in the middle of a UTF-16 surrogate pair. */
    private fun safeUtf16Start(text: String, index: Int): Int {
        var i = index.coerceIn(0, text.length)
        // If we land on a low surrogate, back up to its high surrogate so the pair stays inside.
        while (i > 0 && i < text.length && Character.isLowSurrogate(text[i])) i -= 1
        return i
    }

    /** Never end a window in the middle of a UTF-16 surrogate pair. */
    private fun safeUtf16End(text: String, index: Int, start: Int): Int {
        var i = index.coerceIn(start, text.length)
        // Landing on a low surrogate: back up so the pair stays inside.
        if (i > start && i < text.length && Character.isLowSurrogate(text[i])) i -= 1
        // Landing on a high surrogate whose low half follows: include the pair so the window
        // never ends with a lone high surrogate.
        if (i > start && i < text.length && Character.isHighSurrogate(text[i]) &&
            i + 1 < text.length && Character.isLowSurrogate(text[i + 1])
        ) {
            i += 2
        }
        return i
    }
}
