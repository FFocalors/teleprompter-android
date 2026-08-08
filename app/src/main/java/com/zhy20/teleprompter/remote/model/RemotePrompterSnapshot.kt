package com.zhy20.teleprompter.remote.model

/**
 * Structured reading window sent to the controller. The [text] is a finite window of the
 * source document (never the whole script); [activeStart]/[activeEnd] are the current reading
 * range relative to [text]; [sourceStartOffset]/[sourceEndOffset] are the absolute character
 * offsets in the canonical annotated text. All ranges are validated/normalized so a malformed
 * peer cannot crash the controller.
 */
data class RemoteReadingText(
    val text: String,
    val activeStart: Int,
    val activeEnd: Int,
    val sourceStartOffset: Int,
    val sourceEndOffset: Int,
    val layoutRevision: Long,
) {
    fun normalized(): RemoteReadingText? {
        if (text.isEmpty()) return null
        val length = text.length
        val start = activeStart.coerceIn(0, length)
        val end = activeEnd.coerceIn(start, length)
        val srcStart = sourceStartOffset.coerceAtLeast(0)
        val srcEnd = sourceEndOffset.coerceAtLeast(srcStart)
        if (text.length > MAX_READING_TEXT_LENGTH) return null
        return copy(
            activeStart = start,
            activeEnd = end,
            sourceStartOffset = srcStart,
            sourceEndOffset = srcEnd,
            layoutRevision = layoutRevision.coerceAtLeast(0L),
        )
    }
}

/** Hard cap on the reading-text window so a peer cannot push an unbounded document. */
const val MAX_READING_TEXT_LENGTH = 420

/**
 * Immutable, protocol-safe snapshot of the prompter device state, sent to a controller.
 *
 * The snapshot carries only a finite plain-text summary of the nearby text (never the full
 * rich-text document) and never references Compose, Android [android.content.Context],
 * resource ids or database entities.
 *
 * @param revision monotonically increases so out-of-order messages can be detected later.
 * @param progress normalized progress, clamped to `0f..1f`.
 */
data class RemotePrompterSnapshot(
    val revision: Long,
    val surface: RemotePrompterSurface,
    val scriptId: String?,
    val scriptTitle: String?,
    val estimatedDurationSeconds: Int?,
    val playbackState: RemotePlaybackState,
    val progress: Float,
    val elapsedTimeMillis: Long,
    val remainingTimeMillis: Long,
    val speedMultiplier: Float,
    val countdownSecondsRemaining: Int?,
    /**
     * @deprecated Legacy plain-text summary. No longer populated by the prompter; the
     *   controller's current-reading text now comes from the dedicated reading window + cursor
     *   messages. Kept only for protocol/backward compatibility and never used in display.
     */
    val nearbyText: String?,
    /** @deprecated Legacy structured reading window; see [nearbyText]. */
    val readingText: RemoteReadingText? = null,
) {
    /** Normalizes all fields so a snapshot can never carry invalid protocol values. */
    fun normalized(): RemotePrompterSnapshot = copy(
        progress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f,
        elapsedTimeMillis = elapsedTimeMillis.coerceAtLeast(0L),
        remainingTimeMillis = remainingTimeMillis.coerceAtLeast(0L),
        speedMultiplier = if (speedMultiplier.isFinite()) speedMultiplier.coerceIn(0f, 10f) else 1f,
        estimatedDurationSeconds = estimatedDurationSeconds?.coerceAtLeast(0),
        countdownSecondsRemaining = countdownSecondsRemaining?.coerceAtLeast(0),
        nearbyText = nearbyText?.take(MAX_NEARBY_TEXT_LENGTH),
        readingText = readingText?.normalized(),
    )
}

/**
 * The default snapshot carries a derived summary of the current script plain text. It is
 * kept tiny so the protocol layer never transmits a full document.
 */
const val MAX_NEARBY_TEXT_LENGTH = 140

/**
 * Compatibility helper for tests and small call sites: builds a snapshot whose nearby text
 * is automatically truncated to the protocol maximum and whose progress is clamped.
 */
fun remotePrompterSnapshot(
    revision: Long,
    surface: RemotePrompterSurface,
    scriptId: String? = null,
    scriptTitle: String? = null,
    estimatedDurationSeconds: Int? = null,
    playbackState: RemotePlaybackState = RemotePlaybackState(),
    progress: Float = 0f,
    elapsedTimeMillis: Long = 0L,
    remainingTimeMillis: Long = 0L,
    speedMultiplier: Float = 1f,
    countdownSecondsRemaining: Int? = null,
    nearbyText: String? = null,
    readingText: RemoteReadingText? = null,
): RemotePrompterSnapshot = RemotePrompterSnapshot(
    revision = revision,
    surface = surface,
    scriptId = scriptId,
    scriptTitle = scriptTitle,
    estimatedDurationSeconds = estimatedDurationSeconds,
    playbackState = playbackState,
    progress = progress,
    elapsedTimeMillis = elapsedTimeMillis,
    remainingTimeMillis = remainingTimeMillis,
    speedMultiplier = speedMultiplier,
    countdownSecondsRemaining = countdownSecondsRemaining,
    nearbyText = nearbyText,
    readingText = readingText?.normalized(),
).normalized()
