package com.zhy20.teleprompter.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.colorFromHex
import com.zhy20.teleprompter.core.design.scaleForViewport
import com.zhy20.teleprompter.core.design.components.PrimaryButton
import com.zhy20.teleprompter.core.design.components.DisplayPresetPicker
import com.zhy20.teleprompter.core.design.components.PrompterViewport
import com.zhy20.teleprompter.core.design.components.PrompterViewportMode
import com.zhy20.teleprompter.core.design.components.PrompterPreviewTarget
import com.zhy20.teleprompter.core.design.components.RemoteStatusCard
import com.zhy20.teleprompter.core.design.components.roundedClickable
import com.zhy20.teleprompter.core.design.components.SettingsCard
import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.GuideMode
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.PlaybackTextAlignment
import com.zhy20.teleprompter.core.model.RhythmMode
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ChineseSpeechDurationEstimator
import com.zhy20.teleprompter.core.model.activeDisplayPreset
import com.zhy20.teleprompter.core.model.guideLineColorForBackground
import com.zhy20.teleprompter.core.util.PlaybackTiming
import com.zhy20.teleprompter.core.util.PlaybackPreviewLayout
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
    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        SetupTopBar(onBack)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val expanded = maxWidth >= 840.dp
            if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    SetupPreview(
                        document = script.content,
                        settings = appState.playbackSettings,
                        modifier = Modifier.weight(1.3f).fillMaxHeight(),
                    )
                    Column(Modifier.weight(1f).fillMaxHeight().background(AppColors.Background)) {
                        SettingsPanel(
                            settings = appState.playbackSettings,
                            onSettings = appState::updatePlaybackSettings,
                            normalSeconds = appState.normalEstimatedDurationSeconds(scriptId),
                            appState = appState,
                            onRemote = onRemote,
                            modifier = Modifier.weight(1f),
                        )
                        StartBar(onStart = { onStart(scriptId) })
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    SetupPreview(
                        document = script.content,
                        settings = appState.playbackSettings,
                        modifier = Modifier.fillMaxWidth().height(
                            if (appState.playbackSettings.orientation == PlaybackOrientation.Portrait) 320.dp else 260.dp,
                        ),
                    )
                    SettingsPanel(
                        settings = appState.playbackSettings,
                        onSettings = appState::updatePlaybackSettings,
                        normalSeconds = appState.normalEstimatedDurationSeconds(scriptId),
                        appState = appState,
                        onRemote = onRemote,
                        modifier = Modifier.weight(1f),
                    )
                    StartBar(onStart = { onStart(scriptId) })
                }
            }
        }
    }
}

@Composable
private fun SetupTopBar(onBack: () -> Unit) {
    Surface(color = AppColors.Surface) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Text(stringResource(R.string.prompt_settings), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun StartBar(onStart: () -> Unit) {
    Surface(color = AppColors.Surface) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(AppSpacing.md)) {
            PrimaryButton(
                text = stringResource(R.string.start_playback),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.PlayArrow, null) }
        }
    }
}

@Composable
fun SetupPreview(document: ScriptContent, settings: PlaybackSettings, modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val targetViewport = PlaybackPreviewLayout.targetViewport(
        currentWidthDp = configuration.screenWidthDp,
        currentHeightDp = configuration.screenHeightDp,
        orientation = settings.orientation,
    )
    val previewTarget = PrompterPreviewTarget(
        width = targetViewport.widthDp.dp,
        height = targetViewport.heightDp.dp,
        usesLargeLayout = targetViewport.usesLargeLayout,
    )
    val background = colorFromHex(settings.backgroundColor)
    val foreground = colorFromHex(settings.textColor)
    val guideLineColor = colorFromHex(settings.activeDisplayPreset().guideLineColorForBackground())
    val previewRemainingSeconds = PlaybackTiming.playbackDurationSeconds(
        settings,
        ChineseSpeechDurationEstimator.estimate(document),
    )
    BoxWithConstraints(modifier.background(AppColors.SurfaceRaised).padding(AppSpacing.md)) {
        val previewRatio = targetViewport.aspectRatio
        val sizeModifier = if (maxWidth / previewRatio <= maxHeight) {
            Modifier.fillMaxWidth().aspectRatio(previewRatio)
        } else {
            Modifier.fillMaxHeight().aspectRatio(previewRatio)
        }
        Box(
            Modifier.align(Alignment.Center).then(sizeModifier)
                .clip(MaterialTheme.shapes.medium).background(background),
        ) {
            PrompterViewport(
                document = document,
                settings = settings,
                mode = PrompterViewportMode.Preview,
                previewTarget = previewTarget,
                foreground = foreground,
                guideColor = guideLineColor,
                modifier = Modifier.fillMaxSize(),
                statusContent = { _, viewportScale ->
                    val statusStyle = MaterialTheme.typography.labelMedium.scaleForViewport(viewportScale)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.sm * viewportScale, vertical = AppSpacing.xs * viewportScale),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.elapsed, formatDuration(0)),
                            color = foreground.copy(alpha = .68f),
                            style = statusStyle,
                        )
                        Text(
                            stringResource(R.string.remaining, formatDuration(previewRemainingSeconds)),
                            color = foreground.copy(alpha = .68f),
                            style = statusStyle,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    settings: PlaybackSettings,
    onSettings: (PlaybackSettings) -> Unit,
    normalSeconds: Int,
    appState: AppState,
    onRemote: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.background(AppColors.Background).verticalScroll(rememberScrollState()).padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        DisplaySettings(settings, onSettings)
        RhythmSettings(settings, onSettings, normalSeconds)
        SettingsCard(stringResource(R.string.countdown)) {
            val options = CountdownOption.entries
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                options.forEach { option ->
                    val selected = settings.countdown == option
                    val shape = MaterialTheme.shapes.medium
                    Surface(
                        modifier = Modifier.weight(1f).height(48.dp).roundedClickable(shape = shape) { onSettings(settings.copy(countdown = option)) },
                        shape = shape,
                        color = if (selected) AppColors.Secondary.copy(alpha = .55f) else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AppColors.Primary else AppColors.Border),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (option == CountdownOption.Off) stringResource(R.string.countdown_off) else stringResource(R.string.seconds_format, option.seconds))
                        }
                    }
                }
            }
        }
        SettingsCard(stringResource(R.string.guide_line)) {
            SimpleChoiceRow(
                labels = listOf(
                    stringResource(R.string.guide_off),
                    stringResource(R.string.guide_horizontal),
                    stringResource(R.string.guide_highlight_bar),
                ),
                selectedIndex = settings.guideMode.ordinal,
                onSelected = { onSettings(settings.copy(guideMode = GuideMode.entries[it])) },
            )
            SettingLabel(stringResource(R.string.guide_position), "${(settings.guideLinePosition * 100).toInt()}%")
            Slider(settings.guideLinePosition, { onSettings(settings.copy(guideLinePosition = it)) }, valueRange = .15f..0.75f)
        }
        RemoteStatusCard(appState.remoteConnectionState, onRemote)
        Spacer(Modifier.height(AppSpacing.xl))
    }
}

@Composable
private fun DisplaySettings(settings: PlaybackSettings, onSettings: (PlaybackSettings) -> Unit) {
    SettingsCard(stringResource(R.string.display_settings)) {
        Text(stringResource(R.string.display_presets), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
        DisplayPresetPicker(settings, onSettings)
        SettingLabel(stringResource(R.string.font_size), stringResource(R.string.font_size_value, settings.fontSize))
        Slider(settings.fontSize.toFloat(), { onSettings(settings.copy(fontSize = it.toInt())) }, valueRange = 32f..100f)
        SimpleChoiceRow(
            labels = listOf(stringResource(R.string.portrait), stringResource(R.string.landscape)),
            selectedIndex = if (settings.orientation == PlaybackOrientation.Portrait) 0 else 1,
            onSelected = { onSettings(settings.copy(orientation = if (it == 0) PlaybackOrientation.Portrait else PlaybackOrientation.Landscape)) },
        )
        Text(stringResource(R.string.text_alignment), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
        SimpleChoiceRow(
            labels = listOf(
                stringResource(R.string.align_start),
                stringResource(R.string.align_center),
                stringResource(R.string.align_end),
            ),
            selectedIndex = when (settings.textAlignment) {
                PlaybackTextAlignment.Start -> 0
                PlaybackTextAlignment.Center -> 1
                PlaybackTextAlignment.End -> 2
            },
            onSelected = { index ->
                onSettings(
                    settings.copy(
                        textAlignment = when (index) {
                            0 -> PlaybackTextAlignment.Start
                            1 -> PlaybackTextAlignment.Center
                            else -> PlaybackTextAlignment.End
                        },
                    ),
                )
            },
        )
        Text(stringResource(R.string.mirror), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
        SimpleChoiceRow(
            labels = listOf(stringResource(R.string.normal_display), stringResource(R.string.mirrored_display)),
            selectedIndex = if (settings.mirrorEnabled) 1 else 0,
            onSelected = { onSettings(settings.copy(mirrorEnabled = it == 1)) },
        )
    }
}

@Composable
private fun RhythmSettings(settings: PlaybackSettings, onSettings: (PlaybackSettings) -> Unit, normalSeconds: Int) {
    SettingsCard(
        title = stringResource(R.string.scroll_rhythm),
        supportingText = stringResource(R.string.normal_duration_format, formatDuration(normalSeconds)),
    ) {
        SimpleChoiceRow(
            labels = listOf(stringResource(R.string.speed_mode), stringResource(R.string.target_time_mode)),
            selectedIndex = if (settings.rhythmMode == RhythmMode.Speed) 0 else 1,
            onSelected = { onSettings(settings.copy(rhythmMode = if (it == 0) RhythmMode.Speed else RhythmMode.TargetDuration)) },
        )
        if (settings.rhythmMode == RhythmMode.Speed) {
            SettingLabel(stringResource(R.string.speed), stringResource(R.string.speed_multiplier, settings.speedMultiplier))
            Slider(settings.speedMultiplier, { onSettings(settings.copy(speedMultiplier = it)) }, valueRange = .5f..2f)
        } else {
            TargetDurationEditor(settings, onSettings, normalSeconds)
        }
    }
}

@Composable
private fun TargetDurationEditor(settings: PlaybackSettings, onSettings: (PlaybackSettings) -> Unit, normalSeconds: Int) {
    val split = PlaybackTiming.split(settings.targetDurationSeconds)
    var minutes by remember(settings.targetDurationSeconds) { mutableStateOf(split.minutes.toString()) }
    var seconds by remember(settings.targetDurationSeconds) { mutableStateOf(split.seconds.toString()) }
    val parsedDuration = PlaybackTiming.fromMinuteSecond(minutes.toIntOrNull(), seconds.toIntOrNull())
    val invalid = parsedDuration == null
    val multiplier = PlaybackTiming.roundedMultiplier(normalSeconds, settings.targetDurationSeconds)

    SettingLabel(stringResource(R.string.target_duration), formatDuration(settings.targetDurationSeconds))
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm), verticalAlignment = Alignment.Top) {
        DurationField(
            value = minutes,
            label = stringResource(R.string.minutes_unit),
            isError = invalid,
            onValueChange = { updated ->
                minutes = updated
                PlaybackTiming.fromMinuteSecond(minutes.toIntOrNull(), seconds.toIntOrNull())?.let { duration ->
                    onSettings(settings.copy(targetDurationSeconds = duration, speedMultiplier = PlaybackTiming.roundedMultiplier(normalSeconds, duration)))
                }
            },
            modifier = Modifier.weight(1f),
        )
        DurationField(
            value = seconds,
            label = stringResource(R.string.seconds_unit),
            isError = invalid,
            onValueChange = { updated ->
                seconds = updated
                PlaybackTiming.fromMinuteSecond(minutes.toIntOrNull(), seconds.toIntOrNull())?.let { duration ->
                    onSettings(settings.copy(targetDurationSeconds = duration, speedMultiplier = PlaybackTiming.roundedMultiplier(normalSeconds, duration)))
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
    if (invalid) Text(stringResource(R.string.target_time_invalid), color = AppColors.Danger, style = MaterialTheme.typography.labelMedium)
    SettingLabel(stringResource(R.string.corresponding_speed), stringResource(R.string.speed_multiplier, multiplier))
}

@Composable
private fun DurationField(value: String, label: String, isError: Boolean, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(6)) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun SimpleChoiceRow(labels: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val shape = MaterialTheme.shapes.medium
            Surface(
                modifier = Modifier.weight(1f).height(48.dp).roundedClickable(shape = shape) { onSelected(index) },
                color = if (selected) AppColors.Secondary.copy(alpha = .55f) else Color.Transparent,
                contentColor = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AppColors.Primary else AppColors.Border),
                shape = shape,
            ) {
                Box(contentAlignment = Alignment.Center) { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 2) }
            }
        }
    }
}

@Composable
private fun SettingLabel(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
    }
}
