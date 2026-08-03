package com.zhy20.teleprompter.feature.prompter

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.zhy20.teleprompter.core.util.PlaybackTouchPolicy

/**
 * Consumes all app-level edge touches before tap/drag recognition. The modifier is disabled while
 * paused or while the control sheet is visible so controls retain their normal full-area behavior.
 */
fun Modifier.playbackTouchGestures(
    enabled: Boolean,
    density: Float,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
): Modifier {
    if (!enabled) return this
    return this
        .pointerInput(density) {
            detectTapGestures(
                onTap = { offset ->
                    if (PlaybackTouchPolicy.allowsPlaybackGesture(size.width.toFloat(), size.height.toFloat(), density, offset.x, offset.y, true, false)) onTap()
                },
                onDoubleTap = { offset ->
                    if (PlaybackTouchPolicy.allowsPlaybackGesture(size.width.toFloat(), size.height.toFloat(), density, offset.x, offset.y, true, false)) onDoubleTap()
                },
            )
        }
        .pointerInput(density) {
            var accepted = false
            detectVerticalDragGestures(
                onDragStart = { offset ->
                    accepted = PlaybackTouchPolicy.allowsPlaybackGesture(size.width.toFloat(), size.height.toFloat(), density, offset.x, offset.y, true, false)
                },
                onVerticalDrag = { change, dragAmount ->
                    if (accepted) {
                        change.consume()
                        onVerticalDrag((dragAmount / size.height * .24f).coerceIn(-.08f, .08f))
                    }
                },
            )
        }
        // This modifier is innermost, so it consumes an edge touch on the Main pass before
        // the outer tap and drag detectors can recognise it.
        .pointerInput(density) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!PlaybackTouchPolicy.centralRegion(size.width.toFloat(), size.height.toFloat(), density).contains(down.position.x, down.position.y)) {
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
        }
}
