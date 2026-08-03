package com.zhy20.teleprompter.feature.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppColorOptions
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.colorFromHex
import com.zhy20.teleprompter.core.design.components.AppCard
import com.zhy20.teleprompter.core.design.components.ChoiceRow
import com.zhy20.teleprompter.core.design.components.PrimaryButton
import com.zhy20.teleprompter.core.design.components.RemoteStatusCard
import com.zhy20.teleprompter.core.design.components.SettingsCard
import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.GuideLineStyle
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.RhythmMode
import com.zhy20.teleprompter.core.util.formatDuration

@Composable
fun SetupScreen(
    scriptId: String,
    appState: AppState,
    onBack: () -> Unit,
    onRemote: () -> Unit,
    onStart: (String) -> Unit,
) {
    val script = appState.script(scriptId)
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val expanded = maxWidth >= 840.dp
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                SetupPreview(appState.playbackSettings, Modifier.weight(1.3f).fillMaxHeight())
                SettingsPanel(
                    settings = appState.playbackSettings,
                    onSettings = { appState.playbackSettings = it },
                    normalSeconds = script.normalEstimatedDurationSeconds,
                    appState = appState,
                    onBack = onBack,
                    onRemote = onRemote,
                    onStart = { onStart(scriptId) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    showStart = true,
                )
            }
        } else {
            Scaffold(
                containerColor = AppColors.Background,
                bottomBar = {
                    Box(Modifier.fillMaxWidth().background(AppColors.Surface).padding(AppSpacing.md)) {
                        PrimaryButton(stringResource(R.string.start_playback), { onStart(scriptId) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null) }
                    }
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    SetupPreview(appState.playbackSettings, Modifier.fillMaxWidth().height(260.dp))
                    SettingsPanel(
                        settings = appState.playbackSettings,
                        onSettings = { appState.playbackSettings = it },
                        normalSeconds = script.normalEstimatedDurationSeconds,
                        appState = appState,
                        onBack = onBack,
                        onRemote = onRemote,
                        onStart = { onStart(scriptId) },
                        modifier = Modifier.weight(1f),
                        showStart = false,
                    )
                }
            }
        }
    }
}

@Composable
fun SetupPreview(settings: PlaybackSettings, modifier: Modifier = Modifier) {
    val background = colorFromHex(settings.backgroundColor)
    val foreground = colorFromHex(settings.textColor)
    BoxWithConstraints(modifier.background(background).clip(MaterialTheme.shapes.medium)) {
        val guideY = maxHeight * settings.guideLinePosition
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(AppSpacing.lg).graphicsLayer { scaleX = if (settings.mirrorEnabled) -1f else 1f },
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Text(stringResource(R.string.preview_sample_previous), color = foreground.copy(alpha = .45f), fontSize = (settings.fontSize * .55f).sp, lineHeight = (settings.fontSize * .68f).sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.preview_sample_current), color = foreground, fontSize = (settings.fontSize * .55f).sp, lineHeight = (settings.fontSize * .68f).sp, fontWeight = FontWeight.Bold)
        }
        if (settings.guideLineEnabled) {
            when (settings.guideLineStyle) {
                GuideLineStyle.Highlight -> Box(Modifier.fillMaxWidth().height(54.dp).offset(y = guideY).background(AppColors.Secondary.copy(alpha = .26f)))
                GuideLineStyle.Line -> Box(Modifier.fillMaxWidth().height(2.dp).offset(y = guideY).background(AppColors.Secondary))
            }
        }
        Text(stringResource(R.string.live_preview), Modifier.align(Alignment.TopStart).padding(AppSpacing.md), color = AppColors.TextWeak, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SettingsPanel(
    settings: PlaybackSettings,
    onSettings: (PlaybackSettings) -> Unit,
    normalSeconds: Int,
    appState: AppState,
    onBack: () -> Unit,
    onRemote: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier,
    showStart: Boolean,
) {
    Column(modifier.background(AppColors.Background).verticalScroll(rememberScrollState()).padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.prompt_settings), style = MaterialTheme.typography.headlineMedium)
        }
        SettingsCard(stringResource(R.string.display_settings)) {
            ColorSetting(stringResource(R.string.background_color), settings.backgroundColor, AppColorOptions.Backgrounds) { onSettings(settings.copy(backgroundColor = it)) }
            ColorSetting(stringResource(R.string.text_color), settings.textColor, AppColorOptions.Texts) { onSettings(settings.copy(textColor = it)) }
            SettingLabel(stringResource(R.string.font_size), stringResource(R.string.font_size_value, settings.fontSize))
            Slider(settings.fontSize.toFloat(), { onSettings(settings.copy(fontSize = it.toInt())) }, valueRange = 32f..100f)
            ChoiceRow(
                listOf(
                    stringResource(R.string.portrait) to (settings.orientation == PlaybackOrientation.Portrait),
                    stringResource(R.string.landscape) to (settings.orientation == PlaybackOrientation.Landscape),
                ),
                { onSettings(settings.copy(orientation = if (it == 0) PlaybackOrientation.Portrait else PlaybackOrientation.Landscape)) },
            )
            ToggleSetting(stringResource(R.string.mirror), settings.mirrorEnabled) { onSettings(settings.copy(mirrorEnabled = it)) }
        }
        SettingsCard(
            stringResource(R.string.scroll_rhythm),
            supportingText = stringResource(R.string.normal_duration_format, formatDuration(normalSeconds)),
        ) {
            ChoiceRow(
                listOf(
                    stringResource(R.string.speed_mode) to (settings.rhythmMode == RhythmMode.Speed),
                    stringResource(R.string.target_time_mode) to (settings.rhythmMode == RhythmMode.TargetDuration),
                ),
                { onSettings(settings.copy(rhythmMode = if (it == 0) RhythmMode.Speed else RhythmMode.TargetDuration)) },
            )
            if (settings.rhythmMode == RhythmMode.Speed) {
                SettingLabel(stringResource(R.string.speed), stringResource(R.string.speed_multiplier, settings.speedMultiplier))
                Slider(settings.speedMultiplier, { onSettings(settings.copy(speedMultiplier = it)) }, valueRange = .5f..2f)
            } else {
                ChoiceRow(listOf(120, 180, 300).map { stringResource(R.string.target_duration) + " " + formatDuration(it) to (settings.targetDurationSeconds == it) }, onSelected = { index ->
                    onSettings(settings.copy(targetDurationSeconds = listOf(120, 180, 300)[index]))
                })
            }
        }
        SettingsCard(stringResource(R.string.countdown)) {
            val options = CountdownOption.entries
            ChoiceRow(options.map { option -> (if (option == CountdownOption.Off) stringResource(R.string.countdown_off) else stringResource(R.string.seconds_format, option.seconds)) to (settings.countdown == option) }, onSelected = {
                onSettings(settings.copy(countdown = options[it]))
            })
        }
        SettingsCard(stringResource(R.string.guide_line)) {
            ToggleSetting(stringResource(R.string.guide_line), settings.guideLineEnabled) { onSettings(settings.copy(guideLineEnabled = it)) }
            ChoiceRow(
                listOf(
                    stringResource(R.string.guide_highlight) to (settings.guideLineStyle == GuideLineStyle.Highlight),
                    stringResource(R.string.guide_horizontal) to (settings.guideLineStyle == GuideLineStyle.Line),
                ),
                { onSettings(settings.copy(guideLineStyle = if (it == 0) GuideLineStyle.Highlight else GuideLineStyle.Line)) },
            )
            SettingLabel(stringResource(R.string.guide_position), "${(settings.guideLinePosition * 100).toInt()}%")
            Slider(settings.guideLinePosition, { onSettings(settings.copy(guideLinePosition = it)) }, valueRange = .15f..0.75f)
        }
        RemoteStatusCard(appState.remoteConnectionState, onRemote)
        if (showStart) PrimaryButton(stringResource(R.string.start_playback), onStart, Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null) }
        Spacer(Modifier.height(AppSpacing.md))
    }
}

@Composable
private fun SettingLabel(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = AppColors.TextSecondary)
        Switch(checked, onChecked)
    }
}

@Composable
private fun ColorSetting(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        SettingLabel(label, selected)
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            options.forEach { hex ->
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(colorFromHex(hex)).clickable { onSelected(hex) }
                        .then(if (selected == hex) Modifier.background(Color.Transparent) else Modifier),
                )
            }
        }
    }
}
