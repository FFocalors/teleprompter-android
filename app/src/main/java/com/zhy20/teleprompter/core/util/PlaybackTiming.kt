package com.zhy20.teleprompter.core.util

import kotlin.math.roundToInt

data class MinuteSecond(val minutes: Int, val seconds: Int)

object PlaybackTiming {
    /** Returns null for incomplete, negative, overflowing, or zero durations. */
    fun fromMinuteSecond(minutes: Int?, seconds: Int?): Int? {
        val safeMinutes = minutes ?: return null
        val safeSeconds = seconds ?: return null
        if (safeMinutes < 0 || safeSeconds < 0) return null
        val total = safeMinutes.toLong() * 60L + safeSeconds.toLong()
        return total.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
    }

    fun split(seconds: Int): MinuteSecond {
        val safe = seconds.coerceAtLeast(0)
        return MinuteSecond(minutes = safe / 60, seconds = safe % 60)
    }

    /** Prevents divide-by-zero and non-finite values in the visual playback prototype. */
    fun speedMultiplier(normalDurationSeconds: Int, targetDurationSeconds: Int): Float {
        if (normalDurationSeconds <= 0 || targetDurationSeconds <= 0) return 1f
        return (normalDurationSeconds.toDouble() / targetDurationSeconds.toDouble())
            .coerceIn(0.1, 10.0)
            .toFloat()
    }

    fun roundedMultiplier(normalDurationSeconds: Int, targetDurationSeconds: Int): Float =
        (speedMultiplier(normalDurationSeconds, targetDurationSeconds) * 100f).roundToInt() / 100f
}
