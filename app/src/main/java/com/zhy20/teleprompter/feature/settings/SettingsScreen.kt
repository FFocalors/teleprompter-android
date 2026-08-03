package com.zhy20.teleprompter.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.components.AppCard
import com.zhy20.teleprompter.core.design.components.ChoiceRow
import com.zhy20.teleprompter.core.design.components.DisplayPresetPicker
import com.zhy20.teleprompter.core.design.components.SettingsCard
import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.GuideLineStyle
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.RhythmMode

@Composable
fun SettingsScreen(appState: AppState, onBack: () -> Unit, onLanguage: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val settings = appState.globalDefaults
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = if (maxWidth >= 840.dp) 860.dp else 620.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column {
                    Text(stringResource(R.string.global_defaults), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.global_defaults_hint), color = AppColors.TextWeak, style = MaterialTheme.typography.bodyMedium)
                }
            }
            SettingsCard(stringResource(R.string.display_settings)) {
                DisplayPresetPicker(settings, onSettingsChange = { appState.globalDefaults = it })
                LabelValue(stringResource(R.string.font_size), stringResource(R.string.font_size_value, settings.fontSize))
                Slider(settings.fontSize.toFloat(), { appState.globalDefaults = settings.copy(fontSize = it.toInt()) }, valueRange = 32f..100f)
                ChoiceRow(
                    listOf(
                        stringResource(R.string.portrait) to (settings.orientation == PlaybackOrientation.Portrait),
                        stringResource(R.string.landscape) to (settings.orientation == PlaybackOrientation.Landscape),
                    ),
                    { appState.globalDefaults = settings.copy(orientation = if (it == 0) PlaybackOrientation.Portrait else PlaybackOrientation.Landscape) },
                )
                ToggleRow(stringResource(R.string.mirror), settings.mirrorEnabled) { appState.globalDefaults = settings.copy(mirrorEnabled = it) }
            }
            SettingsCard(stringResource(R.string.default_scroll_mode)) {
                ChoiceRow(
                    listOf(
                        stringResource(R.string.speed_mode) to (settings.rhythmMode == RhythmMode.Speed),
                        stringResource(R.string.target_time_mode) to (settings.rhythmMode == RhythmMode.TargetDuration),
                    ),
                    { appState.globalDefaults = settings.copy(rhythmMode = if (it == 0) RhythmMode.Speed else RhythmMode.TargetDuration) },
                )
                LabelValue(stringResource(R.string.speed), stringResource(R.string.speed_multiplier, settings.speedMultiplier))
                Slider(settings.speedMultiplier, { appState.globalDefaults = settings.copy(speedMultiplier = it) }, valueRange = .5f..2f)
            }
            SettingsCard(stringResource(R.string.countdown)) {
                val options = CountdownOption.entries
                ChoiceRow(options.map { (if (it == CountdownOption.Off) stringResource(R.string.countdown_off) else stringResource(R.string.seconds_format, it.seconds)) to (settings.countdown == it) }, onSelected = { index ->
                    appState.globalDefaults = settings.copy(countdown = options[index])
                })
            }
            SettingsCard(stringResource(R.string.guide_line)) {
                ToggleRow(stringResource(R.string.guide_line), settings.guideLineEnabled) { appState.globalDefaults = settings.copy(guideLineEnabled = it) }
                ChoiceRow(
                    listOf(
                        stringResource(R.string.guide_highlight) to (settings.guideLineStyle == GuideLineStyle.Highlight),
                        stringResource(R.string.guide_horizontal) to (settings.guideLineStyle == GuideLineStyle.Line),
                    ),
                    { appState.globalDefaults = settings.copy(guideLineStyle = if (it == 0) GuideLineStyle.Highlight else GuideLineStyle.Line) },
                )
                Slider(settings.guideLinePosition, { appState.globalDefaults = settings.copy(guideLinePosition = it) }, valueRange = .15f..0.75f)
            }
            AppCard(Modifier.fillMaxWidth(), onClick = onLanguage) {
                Row(Modifier.fillMaxWidth().padding(AppSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, null, tint = AppColors.Primary)
                    Spacer(Modifier.size(AppSpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                        Text(if (appState.selectedLanguage == "zh-CN") stringResource(R.string.simplified_chinese) else stringResource(R.string.english), color = AppColors.TextSecondary)
                    }
                    Text(stringResource(R.string.tap_language), color = AppColors.TextWeak, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))
        }
    }
}

@Composable
fun LanguageScreen(appState: AppState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.language_settings), style = MaterialTheme.typography.headlineMedium)
        }
        Text(stringResource(R.string.language_hint), color = AppColors.TextSecondary)
        AppCard(Modifier.fillMaxWidth(), onClick = { appState.selectedLanguage = "zh-CN" }) {
            LanguageOption(stringResource(R.string.simplified_chinese), appState.selectedLanguage == "zh-CN")
        }
        AppCard(Modifier.fillMaxWidth(), onClick = { appState.selectedLanguage = "en-US" }) {
            LanguageOption(stringResource(R.string.english), appState.selectedLanguage == "en-US")
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean) {
    Row(Modifier.fillMaxWidth().padding(AppSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (selected) AppColors.Primary else AppColors.Border))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = AppColors.TextSecondary)
        Switch(checked, onChecked)
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = AppColors.TextSecondary)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
