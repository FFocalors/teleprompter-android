package com.zhy20.teleprompter.core.model

import com.zhy20.teleprompter.core.util.PlaybackTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun everyDisplayPreset_updatesBothColorsAndKeepsItsId() {
        DisplayPresets.defaults.forEach { preset ->
            val applied = PlaybackSettings().applyDisplayPreset(preset)
            assertEquals(preset.backgroundColor, applied.backgroundColor)
            assertEquals(preset.textColor, applied.textColor)
            assertEquals(preset.id, applied.displayPresetId)
            assertEquals(preset.id, applied.activeDisplayPreset().id)
        }
    }

    @Test
    fun legacyColorPair_mapsToClosestSafePreset() {
        val legacy = PlaybackSettings(backgroundColor = "#141622", textColor = "#C4CBD6", displayPresetId = null)
        val normalized = legacy.normalizedToDisplayPreset()

        assertEquals(DisplayPresets.BlackOnWhite.id, normalized.displayPresetId)
        assertEquals(DisplayPresets.BlackOnWhite.backgroundColor, normalized.backgroundColor)
        assertEquals(DisplayPresets.BlackOnWhite.textColor, normalized.textColor)
    }

    @Test
    fun legacyColors_takePrecedenceOverAStalePresetIdWhenFindingNearestPreset() {
        val legacy = PlaybackSettings(
            backgroundColor = "#29405A",
            textColor = "#FFF2DF",
            displayPresetId = DisplayPresets.BlackOnWhite.id,
        )

        assertEquals(DisplayPresets.BlueOnWhite.id, legacy.normalizedToDisplayPreset().displayPresetId)
    }

    @Test
    fun guideLine_usesBrightRedOnDarkPresetsAndDeepRedOnLightPresets() {
        assertEquals("#FF3B30", DisplayPresets.BlackOnWhite.guideLineColorForBackground())
        assertEquals("#C62828", DisplayPresets.WhiteOnBlack.guideLineColorForBackground())
        assertEquals("#C62828", DisplayPresets.OrangeOnCharcoal.guideLineColorForBackground())
    }
}
