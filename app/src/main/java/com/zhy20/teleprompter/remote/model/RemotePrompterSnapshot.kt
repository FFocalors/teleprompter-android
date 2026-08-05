package com.zhy20.teleprompter.remote.model

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
    val nearbyText: String?,
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
).normalized()
