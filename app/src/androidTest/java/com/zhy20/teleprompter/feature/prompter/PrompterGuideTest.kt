package com.zhy20.teleprompter.feature.prompter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhy20.teleprompter.core.design.AppTheme
import com.zhy20.teleprompter.core.design.components.GuideHighlightBarTestTag
import com.zhy20.teleprompter.core.design.components.GuideLineTestTag
import com.zhy20.teleprompter.core.design.components.PrompterGuide
import com.zhy20.teleprompter.core.model.GuideMode
import com.zhy20.teleprompter.testing.PlaybackGestureTestActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrompterGuideTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PlaybackGestureTestActivity>()

    @Test
    fun guideModesNeverLeaveThePreviousVisualBehind() {
        lateinit var mode: MutableState<GuideMode>
        composeRule.setContent {
            mode = remember { mutableStateOf(GuideMode.Off) }
            AppTheme {
                Box(Modifier.size(600.dp, 400.dp)) {
                    PrompterGuide(mode = mode.value, position = .25f, color = Color.Red)
                }
            }
        }

        assertNoGuide()
        composeRule.runOnIdle { mode.value = GuideMode.Line }
        composeRule.onNodeWithTag(GuideLineTestTag).assertExists()
        composeRule.onNodeWithTag(GuideHighlightBarTestTag).assertDoesNotExist()

        composeRule.runOnIdle { mode.value = GuideMode.HighlightBar }
        composeRule.onNodeWithTag(GuideLineTestTag).assertDoesNotExist()
        composeRule.onNodeWithTag(GuideHighlightBarTestTag).assertExists()

        composeRule.runOnIdle { mode.value = GuideMode.Off }
        assertNoGuide()
    }

    private fun assertNoGuide() {
        composeRule.onNodeWithTag(GuideLineTestTag).assertDoesNotExist()
        composeRule.onNodeWithTag(GuideHighlightBarTestTag).assertDoesNotExist()
    }
}
