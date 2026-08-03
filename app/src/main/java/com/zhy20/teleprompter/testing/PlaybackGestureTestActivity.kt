package com.zhy20.teleprompter.testing

import androidx.activity.ComponentActivity

/**
 * Empty, non-exported host used by the instrumentation test for the playback
 * touch modifier. Keeping it in the target APK lets ActivityScenario create a
 * separate Compose root without replacing the application's MainActivity UI.
 */
class PlaybackGestureTestActivity : ComponentActivity()
