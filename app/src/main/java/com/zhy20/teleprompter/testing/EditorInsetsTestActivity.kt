package com.zhy20.teleprompter.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Edge-to-edge host for the editor header insets test. Mirrors MainActivity's
 * window configuration (transparent system bars) so the tested composable sees
 * real status-bar insets, which the existing non-edge-to-edge
 * PlaybackGestureTestActivity would not deliver.
 */
class EditorInsetsTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
    }
}
