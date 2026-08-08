package com.zhy20.teleprompter.feature.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhy20.teleprompter.core.design.AppTheme
import com.zhy20.teleprompter.remote.model.RemoteReadingCursor
import com.zhy20.teleprompter.remote.model.RemoteReadingWindow
import com.zhy20.teleprompter.testing.PlaybackGestureTestActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Covers §40 (ControllerReadingViewport): the fixed ≈5–6 line area never shrinks for short
 * text nor stretches for long text, the placeholder shows without reading state, and the window
 * text is re-flown at the controller's own width.
 */
@RunWith(AndroidJUnit4::class)
class ControllerReadingViewportTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<PlaybackGestureTestActivity>()

    private fun window(
        text: String,
        start: Int = 0,
        revision: Long = start.toLong() + 1L,
    ) = RemoteReadingWindow(
        textRevision = 1L,
        windowRevision = revision,
        startOffset = start,
        endOffset = start + text.length,
        text = text,
    )

    private val shortText = "短台本内容。"
    private val longText = (0 until 40).joinToString("\n") { "第${it}行内容" + "啊".repeat(12) }

    private data class ViewportHarness(
        val widthDp: MutableIntState,
        val window: MutableState<RemoteReadingWindow?>,
        val cursor: MutableState<RemoteReadingCursor?>,
    )

    private fun setViewport(
        widthDp: Int,
        window: RemoteReadingWindow?,
        cursor: RemoteReadingCursor? = null,
    ): ViewportHarness {
        val harness = ViewportHarness(
            widthDp = mutableIntStateOf(widthDp),
            window = mutableStateOf(window),
            cursor = mutableStateOf(cursor),
        )
        composeRule.setContent {
            AppTheme {
                Box(Modifier.size(harness.widthDp.intValue.dp, 900.dp)) {
                    ControllerReadingViewport(
                        window = harness.window.value,
                        cursor = harness.cursor.value,
                        modifier = Modifier,
                        placeholder = "暂无朗读文本",
                        semanticsTag = "readingViewport",
                    )
                }
            }
        }
        return harness
    }

    private fun ViewportHarness.update(
        widthDp: Int = this.widthDp.intValue,
        window: RemoteReadingWindow? = this.window.value,
        cursor: RemoteReadingCursor? = this.cursor.value,
    ) {
        composeRule.runOnIdle {
            this.widthDp.intValue = widthDp
            this.window.value = window
            this.cursor.value = cursor
        }
    }

    private fun areaHeightDp(): Float {
        val bounds = composeRule.onNodeWithTag(ControllerReadingViewportAreaTag).getUnclippedBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }

    @Test
    fun placeholderShownWhenNoReadingState() {
        setViewport(widthDp = 360, window = null)
        composeRule.onNodeWithTag("readingViewport").assertIsDisplayed()
    }

    @Test
    fun areaHeightIsFixedForShortAndLongWindow() {
        val viewport = setViewport(widthDp = 360, window = window(shortText))
        val shortHeight = areaHeightDp()
        viewport.update(window = window(longText))
        val longHeight = areaHeightDp()
        // The card never grows with the text length.
        assertTrue(abs(longHeight - shortHeight) <= 1f)
    }

    @Test
    fun longWindowTextIsClippedNotStretched() {
        setViewport(widthDp = 360, window = window(longText))
        val areaHeight = areaHeightDp()
        // The window text itself is far taller than the fixed area; the area still stays small.
        val textBounds = composeRule.onNodeWithTag("readingViewport").getUnclippedBoundsInRoot()
        assertTrue(textBounds.bottom.value > areaHeight * 2)
        composeRule.onNodeWithTag(ControllerReadingViewportAreaTag).assertIsDisplayed()
    }

    @Test
    fun fourHundredSixtyPlusCharactersUseFullLayoutBehindFixedViewport() {
        val text = "正文".repeat(240) // 480 UTF-16 units, comparable to the reproduced script.
        val cursor = RemoteReadingCursor(
            textRevision = 1L,
            absoluteOffset = 360.0,
            sequence = 1L,
            sentAtElapsedRealtimeMillis = 0L,
        )
        setViewport(widthDp = 360, window = window(text), cursor = cursor)

        composeRule.waitUntil(5_000) {
            composeRule.onNodeWithTag("readingViewport").fetchSemanticsNode()
                .config.getOrElse(ControllerReadingLineCountKey) { 0 } > 6
        }
        val config = composeRule.onNodeWithTag("readingViewport").fetchSemanticsNode().config
        val windowLength = config[ControllerReadingWindowLengthKey]
        val lineCount = config[ControllerReadingLineCountKey]
        val layoutHeight = config[ControllerReadingLayoutHeightKey]
        val viewportHeight = config[ControllerReadingViewportHeightKey]

        assertTrue("expected the full 480-char cache", windowLength >= 460)
        assertTrue("expected more lines than the clipped viewport, got $lineCount", lineCount > 6)
        assertTrue(
            "full text layout ($layoutHeight px) must exceed viewport ($viewportHeight px)",
            layoutHeight > viewportHeight,
        )
        composeRule.waitUntil(5_000) {
            composeRule.onNodeWithTag("readingViewport").fetchSemanticsNode()
                .config.getOrElse(ControllerReadingTranslationYKey) { 0 } < 0
        }
    }

    @Test
    fun rendersAtNarrowAndWideWidths() {
        val viewport = setViewport(widthDp = 320, window = window(longText))
        composeRule.onNodeWithTag(ControllerReadingViewportAreaTag).assertIsDisplayed()
        viewport.update(widthDp = 412)
        composeRule.onNodeWithTag(ControllerReadingViewportAreaTag).assertIsDisplayed()
    }

    @Test
    fun chineseParagraphWrapsWithoutHardBreaks() {
        // A single paragraph with no newlines must wrap on its own at the controller width.
        val noNewlines = "这是一段没有换行的中文长段落。" + "内容".repeat(120)
        setViewport(widthDp = 320, window = window(noNewlines))
        composeRule.onNodeWithTag("readingViewport").assertIsDisplayed()
        composeRule.onNodeWithTag(ControllerReadingViewportAreaTag).assertIsDisplayed()
    }

    @Test
    fun cursorDrivenRenderDoesNotCrashAndKeepsAreaStable() {
        val cursor = RemoteReadingCursor(
            textRevision = 1L,
            absoluteOffset = 300.0,
            sequence = 1L,
            sentAtElapsedRealtimeMillis = 0L,
        )
        val viewport = setViewport(widthDp = 360, window = window(longText), cursor = cursor)
        composeRule.onNodeWithTag(ControllerReadingViewportAreaTag).assertIsDisplayed()
        // A mismatched cursor textRevision is ignored safely.
        viewport.update(
            widthDp = 360,
            window = window(longText),
            cursor = RemoteReadingCursor(textRevision = 99L, absoluteOffset = 5.0, sequence = 2L, sentAtElapsedRealtimeMillis = 0L),
        )
        composeRule.onNodeWithTag(ControllerReadingViewportAreaTag).assertIsDisplayed()
    }

    @Test
    fun windowReplacementSwapsTheRenderedText() {
        // §30: replacing the window (a sliding update) must re-render the new text — the
        // viewport must not keep displaying the initial window's content.
        val viewport = setViewport(widthDp = 360, window = window("这是窗口A的独特标记文字"))
        composeRule.onNodeWithTag("readingViewport").assertTextEquals("这是窗口A的独特标记文字")

        viewport.update(window = window("这是窗口B的独特标记文字", start = 60))
        composeRule.onNodeWithTag("readingViewport").assertTextEquals("这是窗口B的独特标记文字")
    }
}
