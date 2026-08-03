package com.zhy20.teleprompter.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.colorFromHex
import com.zhy20.teleprompter.core.model.DisplayPreset
import com.zhy20.teleprompter.core.model.DisplayPresets
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.activeDisplayPreset
import com.zhy20.teleprompter.core.model.applyDisplayPreset

/** Shared selector for per-script and global display defaults; custom colors are intentionally absent. */
@Composable
fun DisplayPresetPicker(
    settings: PlaybackSettings,
    onSettingsChange: (PlaybackSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeId = settings.activeDisplayPreset().id
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        DisplayPresets.defaults.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                row.forEach { preset ->
                    DisplayPresetCard(
                        preset = preset,
                        selected = preset.id == activeId,
                        onClick = { onSettingsChange(settings.applyDisplayPreset(preset)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DisplayPresetCard(
    preset: DisplayPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val label = presetDisplayName(preset)
    val description = stringResource(R.string.display_preset_description, label)
    Surface(
        modifier = modifier.height(92.dp).semantics { contentDescription = description }.clickable(onClick = onClick),
        color = colorFromHex(preset.backgroundColor),
        contentColor = colorFromHex(preset.textColor),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) AppColors.Primary else AppColors.Border),
    ) {
        Column(Modifier.padding(AppSpacing.sm), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(
                stringResource(R.string.preset_preview_sample),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun presetDisplayName(preset: DisplayPreset): String = when (preset.id) {
    "black_white" -> stringResource(R.string.display_preset_black_white)
    "white_black" -> stringResource(R.string.display_preset_white_black)
    "deep_blue_white" -> stringResource(R.string.display_preset_deep_blue_white)
    "deep_green_white" -> stringResource(R.string.display_preset_deep_green_white)
    "orange_charcoal" -> stringResource(R.string.display_preset_orange_charcoal)
    else -> stringResource(R.string.display_preset_black_white)
}
