package com.zhy20.teleprompter.feature.prompter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhy20.teleprompter.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackTouchGesturesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun centralTap_invokesPlaybackCallback() {
        var taps = 0
        setPlaybackSurface(onTap = { taps += 1 }, onVerticalDrag = {})

        composeRule.onNodeWithTag(PlaybackSurfaceTag).performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(400)

        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun edgeTapAndDrag_areConsumedBeforePlaybackCallbacks() {
        var taps = 0
        var drags = 0
        setPlaybackSurface(onTap = { taps += 1 }, onVerticalDrag = { drags += 1 })

        composeRule.onNodeWithTag(PlaybackSurfaceTag).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(1f, center.y))
            down(androidx.compose.ui.geometry.Offset(1f, center.y))
            moveTo(androidx.compose.ui.geometry.Offset(1f, center.y - 120f), delayMillis = 150)
            up()
        }
        composeRule.mainClock.advanceTimeBy(400)

        composeRule.runOnIdle {
            assertEquals(0, taps)
            assertEquals(0, drags)
        }
    }

    private fun setPlaybackSurface(onTap: () -> Unit, onVerticalDrag: (Float) -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current.density
            Box(
                Modifier
                    .size(300.dp)
                    .background(Color.Black)
                    .semantics { testTag = PlaybackSurfaceTag }
                    .playbackTouchGestures(
                        enabled = true,
                        density = density,
                        onTap = onTap,
                        onDoubleTap = {},
                        onVerticalDrag = onVerticalDrag,
                    ),
            )
        }
    }

    private companion object {
        const val PlaybackSurfaceTag = "playbackSurface"
    }
}
