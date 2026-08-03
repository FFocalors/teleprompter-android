package com.zhy20.teleprompter.core.model

import com.zhy20.teleprompter.core.util.PlaybackTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSetupTest {
    @Test
    fun targetDuration_parsesAndNormalizesPastFiftyNineMinutes() {
        assertEquals(3_665, PlaybackTiming.fromMinuteSecond(61, 5))
        assertEquals(330, PlaybackTiming.fromMinuteSecond(4, 90))
        assertEquals(5, PlaybackTiming.split(330).minutes)
        assertEquals(30, PlaybackTiming.split(330).seconds)
        assertNull(PlaybackTiming.fromMinuteSecond(0, 0))
    }

    @Test
    fun targetDuration_multiplierNeverDividesByZeroOrReturnsNan() {
        assertEquals(1f, PlaybackTiming.speedMultiplier(200, 0))
        assertEquals(1f, PlaybackTiming.speedMultiplier(0, 120))
        assertEquals(1.67f, PlaybackTiming.roundedMultiplier(200, 120))
    }

    @Test
    fun rhythmModeSwitch_keepsIndependentTargetAndSpeedValues() {
        val target = PlaybackSettings(targetDurationSeconds = 270, speedMultiplier = 1.2f, rhythmMode = RhythmMode.TargetDuration)
        val speedMode = target.copy(rhythmMode = RhythmMode.Speed, speedMultiplier = 1.4f)
        val restoredTargetMode = speedMode.copy(rhythmMode = RhythmMode.TargetDuration)

        assertEquals(270, restoredTargetMode.targetDurationSeconds)
        assertEquals(1.4f, restoredTargetMode.speedMultiplier)
    }

    @Test
    fun applyingPreset_updatesBothColors_andCustomColorsStayCustom() {
        val preset = DisplayPresets.BlueOnWhite
        val applied = PlaybackSettings().applyDisplayPreset(preset)
        assertEquals(preset.backgroundColor, applied.backgroundColor)
        assertEquals(preset.textColor, applied.textColor)
        assertEquals(preset.id, applied.activeDisplayPreset().id)

        val custom = applied.withCustomColors(backgroundColor = "#123456")
        assertEquals(DisplayPresets.CustomId, custom.activeDisplayPreset().id)
        assertEquals("#123456", custom.backgroundColor)
    }

    @Test
    fun legacyColorPair_isNotOverwrittenWhenItDoesNotMatchPreset() {
        val legacy = PlaybackSettings(backgroundColor = "#141622", textColor = "#C4CBD6")

        assertTrue(legacy.activeDisplayPreset().isCustom)
        assertEquals("#141622", legacy.backgroundColor)
        assertEquals("#C4CBD6", legacy.textColor)
        assertFalse(legacy.activeDisplayPreset().id == DisplayPresets.BlueOnWhite.id)
    }
}
