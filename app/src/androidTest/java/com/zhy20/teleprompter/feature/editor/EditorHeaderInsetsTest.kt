package com.zhy20.teleprompter.feature.editor

import android.graphics.Rect
import android.view.View
import android.view.WindowInsets
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhy20.teleprompter.core.model.RichTextEditorState
import com.zhy20.teleprompter.core.model.SaveState
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.core.model.TextSelection
import com.zhy20.teleprompter.testing.EditorInsetsTestActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The editor header must sit below the real status bar instead of being covered by it.
 *
 * The host activity is edge-to-edge (like MainActivity), so the editor composable sees the real
 * system-bar insets. The test compares the header's top-most control (the back button) against
 * the activity's actual status-bar top inset — the button must be at or below it, never under it.
 */
@RunWith(AndroidJUnit4::class)
class EditorHeaderInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<EditorInsetsTestActivity>()

    @Test
    fun headerBackButton_isPlacedBelowTheStatusBarInset() {
        val statusBarHeight = with(composeRule.density) { realStatusBarInset().toDp() }
        assertTrue("test host must be edge-to-edge (inset > 0), got $statusBarHeight", statusBarHeight > 0.dp)

        composeRule.setContent {
            EditorHeader(
                title = "测试标题",
                onTitleChange = {},
                saveState = SaveState.Initial,
                savedAfterEdit = false,
                editorState = RichTextEditorState(
                    ScriptContent(listOf(ScriptBlock.Paragraph("p", listOf(ScriptSpan("正文"))))),
                    TextSelection(0, 0),
                ),
                onToggleStyle = {},
                onUndo = {},
                onRetrySave = {},
                onBack = {},
                onSetup = {},
            )
        }

        val backBounds = composeRule.onNodeWithTag(EditorHeaderBackTag).getUnclippedBoundsInRoot()
        assertTrue(
            "back button top ${backBounds.top} must be at or below the status bar inset $statusBarHeight",
            backBounds.top >= statusBarHeight,
        )
    }

    /** The real top system-bar inset of the edge-to-edge host activity. */
    private fun realStatusBarInset(): Int {
        val root = composeRule.activity.window.decorView
        val insets = root.rootWindowInsets ?: return 0
        return insets.getInsets(WindowInsets.Type.statusBars()).top
            .coerceAtLeast(insets.getInsets(WindowInsets.Type.displayCutout()).top)
    }
}
