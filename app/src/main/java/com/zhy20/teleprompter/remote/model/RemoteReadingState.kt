package com.zhy20.teleprompter.remote.model

/**
 * Controller-side state for the reading-sync channel. The prompter sends a **low-frequency**
 * contiguous text window ([RemoteReadingWindow]) plus a **high-frequency** absolute reading
 * position ([RemoteReadingCursor]); the controller re-flows the window at its own width and
 * never receives the whole document.
 *
 * All offsets are **UTF-16 code unit offsets** into the prompter's canonical text — the same
 * coordinate system Kotlin [String] and Compose [androidx.compose.ui.text.TextLayoutResult]
 * use. This file is protocol/model-safe: it never references Compose, Android or database
 * types.
 */

/** Hard cap on a reading-window's text so a peer cannot push an unbounded document slice. */
const val MAX_READING_WINDOW_LENGTH = 1200

/**
 * A large contiguous slice of the canonical text the controller keeps as a local re-layout
 * cache. [text] equals `canonical.substring(startOffset, endOffset)`; it is refreshed rarely.
 */
data class RemoteReadingWindow(
    val textRevision: Long,
    val windowRevision: Long,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
) {
    /**
     * Validates/normalizes so a malformed peer cannot crash the controller. [startOffset] and
     * [endOffset] are **absolute** offsets into the canonical text, so the delivered text must
     * exactly match the declared range (`end - start == text.length`); anything else is dropped.
     */
    fun normalized(): RemoteReadingWindow? {
        if (text.isEmpty()) return null
        if (text.length > MAX_READING_WINDOW_LENGTH) return null
        val start = startOffset.coerceAtLeast(0)
        val end = endOffset.coerceAtLeast(start)
        if (end - start != text.length) return null
        return copy(
            textRevision = textRevision.coerceAtLeast(0L),
            windowRevision = windowRevision.coerceAtLeast(0L),
            startOffset = start,
            endOffset = end,
        )
    }
}

/**
 * The current absolute reading position as a continuous UTF-16 offset. [sequence] is monotonic;
 * the controller ignores out-of-order or stale sequences (latest-only).
 */
data class RemoteReadingCursor(
    val textRevision: Long,
    val absoluteOffset: Double,
    val sequence: Long,
    val sentAtElapsedRealtimeMillis: Long,
) {
    fun normalized(): RemoteReadingCursor = copy(
        textRevision = textRevision.coerceAtLeast(0L),
        absoluteOffset = if (absoluteOffset.isFinite()) absoluteOffset.coerceAtLeast(0.0) else 0.0,
        sequence = sequence.coerceAtLeast(0L),
        sentAtElapsedRealtimeMillis = sentAtElapsedRealtimeMillis.coerceAtLeast(0L),
    )
}
