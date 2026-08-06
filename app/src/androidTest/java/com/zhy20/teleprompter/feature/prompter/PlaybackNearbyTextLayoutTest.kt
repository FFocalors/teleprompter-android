package com.zhy20.teleprompter.feature.prompter

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhy20.teleprompter.testing.PlaybackGestureTestActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackNearbyTextLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<PlaybackGestureTestActivity>()

    private fun extractAt(localAnchorY: Float): PlaybackReadingWindow? {
        var extracted: PlaybackReadingWindow? = null
        composeRule.setContent {
            var captured by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = "第一行\n第二行\n第三行\n第四行",
                style = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
                modifier = Modifier.testTag("nearbyTextSource"),
                onTextLayout = { captured = it },
            )
            captured?.let { extracted = extractReadingWindow(it, localAnchorY) }
        }
        composeRule.waitForIdle()
        return extracted
    }

    @Test
    fun guideLineInsideSecondLineSelectsIt() {
        // The real TextLayoutResult decides the line: pick the middle of line 1 by its own
        // top/bottom, so the test is robust to actual line heights.
        var lineMiddle = 0f
        var extracted: PlaybackReadingWindow? = null
        composeRule.setContent {
            var captured by remember { mutableStateOf<TextLayoutResult?>(null) }
            val result = captured
            Text(
                text = "第一行\n第二行\n第三行\n第四行",
                style = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
                modifier = Modifier.testTag("nearbyTextSource"),
                onTextLayout = { captured = it },
            )
            result?.let {
                lineMiddle = (it.getLineTop(1) + it.getLineBottom(1)) / 2f
                extracted = extractReadingWindow(it, lineMiddle)
            }
        }
        composeRule.waitForIdle()
        val w = extracted!!
        assertEquals(1, w.anchorLineIndex)
        // The active range is the reading line's own characters.
        assertEquals("第二行", w.text.substring(w.activeStart, w.activeEnd))
    }

    @Test
    fun guideLineAtBottomSelectsLastLines() {
        val extracted = extractAt(localAnchorY = 100_000f)
        assertEquals(3, extracted!!.anchorLineIndex)
        assertEquals("第四行", extracted.text.substring(extracted.activeStart, extracted.activeEnd))
    }

    @Test
    fun guideLineAtTopSelectsFirstLines() {
        val extracted = extractAt(localAnchorY = -100_000f)
        assertEquals(0, extracted!!.anchorLineIndex)
        assertEquals("第一行", extracted.text.substring(extracted.activeStart, extracted.activeEnd))
    }

    @Test
    fun emptyDocumentReturnsNull() {
        var extracted: PlaybackReadingWindow? = null
        composeRule.setContent {
            var captured by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = "",
                style = TextStyle(fontSize = 20.sp),
                modifier = Modifier.testTag("nearbyTextSource"),
                onTextLayout = { captured = it },
            )
            captured?.let { extracted = extractReadingWindow(it, 0f) }
        }
        composeRule.waitForIdle()
        assertNull(extracted)
    }
}
