package com.zhy20.teleprompter.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhy20.teleprompter.core.model.GuideMode
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.testing.PlaybackGestureTestActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrompterViewportTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PlaybackGestureTestActivity>()

    @Test
    fun statusBandKeepsScriptAndGuideInsideTheContentViewport() {
        composeRule.setContent {
            Box(Modifier.size(360.dp, 640.dp).background(Color.Black)) {
                PrompterViewport(
                    document = ScriptContent(
                        listOf(ScriptBlock.Paragraph("p", listOf(ScriptSpan("普通文字", bold = false)))),
                    ),
                    settings = PlaybackSettings(guideMode = GuideMode.HighlightBar),
                    mode = PrompterViewportMode.Preview,
                    foreground = Color.White,
                    guideColor = Color.Red,
                    modifier = Modifier.fillMaxSize(),
                    scriptTestTag = ScriptTag,
                    statusContent = { _, _ ->
                        Box(Modifier.fillMaxSize().semantics { testTag = StatusTag })
                    },
                )
            }
        }

        val status = composeRule.onNodeWithTag(StatusTag).getUnclippedBoundsInRoot()
        val script = composeRule.onNodeWithTag(ScriptTag).getUnclippedBoundsInRoot()
        val guide = composeRule.onNodeWithTag(GuideHighlightBarTestTag).getUnclippedBoundsInRoot()

        assertTrue(script.top >= status.bottom)
        assertTrue(guide.top >= status.bottom)
    }

    private companion object {
        const val StatusTag = "viewportStatus"
        const val ScriptTag = "viewportScript"
    }
}
