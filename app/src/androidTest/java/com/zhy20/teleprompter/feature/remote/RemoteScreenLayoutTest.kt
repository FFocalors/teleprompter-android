package com.zhy20.teleprompter.feature.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhy20.teleprompter.core.design.AppTheme
import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemotePrompterSurface
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.model.remotePrompterSnapshot
import com.zhy20.teleprompter.testing.PlaybackGestureTestActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteScreenLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<PlaybackGestureTestActivity>()

    private val playingSnapshot = remotePrompterSnapshot(
        revision = 1,
        surface = RemotePrompterSurface.Playing,
        scriptId = "1",
        scriptTitle = "校长采访开场",
        progress = 0.4f,
        elapsedTimeMillis = 80_000L,
        remainingTimeMillis = 120_000L,
        speedMultiplier = 1.0f,
        nearbyText = "接下来，我们会介绍三个重点项目，并分享它们给师生带来的变化。",
    )

    private val controllerConnected = RemoteConnectionStatus.Connected(
        RemoteDeviceInfo("c", "phone", RemoteRole.Controller),
    )

    private fun setControllerScreen(widthDp: Int) {
        composeRule.setContent {
            AppTheme {
                Box(Modifier.size(widthDp.dp, 800.dp)) {
                    RemoteScreen(
                        state = RemoteUiState(
                            status = controllerConnected,
                            snapshot = playingSnapshot,
                            role = RemoteRole.Controller,
                        ),
                        onAction = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun speedButtonsHaveNoStrayDigitsAndAreDistinct() {
        setControllerScreen(widthDp = 320)

        // The two speed buttons exist and are distinct click targets. The delta is NOT baked
        // into their labels (that was the source of the stray "0").
        composeRule.onNodeWithTag(SpeedDecreaseTestTag).assertIsDisplayed()
        composeRule.onNodeWithTag(SpeedIncreaseTestTag).assertIsDisplayed()
        // The centered speed value shows the multiplier only.
        composeRule.onNode(hasText("当前速度 1.0×")).assertIsDisplayed()
        // No button text contains a bare delta digit label.
        composeRule.onNode(hasText("−0.1×")).assertDoesNotExist()
        composeRule.onNode(hasText("+0.1×")).assertDoesNotExist()
    }

    @Test
    fun disconnectEntryIsVisibleWhenControllerConnected() {
        setControllerScreen(widthDp = 360)
        composeRule.onNode(hasContentDescription("断开连接")).assertExists()
    }

    @Test
    fun nearbyTextShowsRealWindowOnNarrowScreen() {
        setControllerScreen(widthDp = 320)
        composeRule.onNodeWithTag(NearbyTextTestTag).assertIsDisplayed()
    }

    @Test
    fun prompterConnectedShowsDisconnectControllerAction() {
        composeRule.setContent {
            AppTheme {
                Box(Modifier.size(360.dp, 800.dp)) {
                    RemoteScreen(
                        state = RemoteUiState(
                            status = RemoteConnectionStatus.Connected(
                                RemoteDeviceInfo("ctrl", "手机", RemoteRole.Controller),
                            ),
                            role = RemoteRole.Prompter,
                        ),
                        onAction = {},
                        onBack = {},
                    )
                }
            }
        }
        // The actions are button labels (text), not content descriptions.
        composeRule.onNode(hasText("断开当前控制端")).assertIsDisplayed()
        composeRule.onNode(hasText("停止远控")).assertIsDisplayed()
    }
}
