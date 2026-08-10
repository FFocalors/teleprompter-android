package com.zhy20.teleprompter.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.BuildConfig
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.components.AppCard
import com.zhy20.teleprompter.core.design.components.ChoiceRow
import com.zhy20.teleprompter.core.design.components.DisplayPresetPicker
import com.zhy20.teleprompter.core.design.components.MotionIconButton
import com.zhy20.teleprompter.core.design.components.SettingsCard
import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.GuideMode
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.PlaybackTextAlignment
import com.zhy20.teleprompter.core.model.RhythmMode

@Composable
fun SettingsScreen(
    appState: AppState,
    onBack: () -> Unit,
    onPlaybackDefaults: () -> Unit,
    onLanguage: () -> Unit,
    onAbout: () -> Unit,
    languageOverride: String? = null,
) {
    val selectedLanguage = languageOverride ?: appState.selectedLanguage
    SettingsPage(title = stringResource(R.string.settings), onBack = onBack) {
        SettingsMenuItem(
            icon = Icons.Default.Tune,
            title = stringResource(R.string.global_defaults),
            summary = stringResource(R.string.global_defaults_hint),
            onClick = onPlaybackDefaults,
        )
        SettingsMenuItem(
            icon = Icons.Default.Language,
            title = stringResource(R.string.language),
            summary = if (selectedLanguage == "zh-CN") {
                stringResource(R.string.simplified_chinese)
            } else {
                stringResource(R.string.english)
            },
            onClick = onLanguage,
        )
        SettingsMenuItem(
            icon = Icons.Default.Info,
            title = stringResource(R.string.about),
            summary = stringResource(R.string.about_summary),
            onClick = onAbout,
        )
    }
}

@Composable
fun DefaultPlaybackSettingsScreen(
    appState: AppState,
    onBack: () -> Unit,
    defaultsOverride: PlaybackSettings? = null,
    onDefaultsChange: ((PlaybackSettings) -> Unit)? = null,
) {
    val settings = defaultsOverride ?: appState.globalDefaults
    val updateDefaults: (PlaybackSettings) -> Unit = onDefaultsChange ?: { appState.globalDefaults = it }
    SettingsPage(title = stringResource(R.string.global_defaults), onBack = onBack) {
        Text(
            stringResource(R.string.global_defaults_hint),
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        SettingsCard(stringResource(R.string.display_settings)) {
            DisplayPresetPicker(settings, onSettingsChange = updateDefaults)
            LabelValue(stringResource(R.string.font_size), stringResource(R.string.font_size_value, settings.fontSize))
            Slider(settings.fontSize.toFloat(), { updateDefaults(settings.copy(fontSize = it.toInt())) }, valueRange = 32f..100f)
            ChoiceRow(
                listOf(
                    stringResource(R.string.portrait) to (settings.orientation == PlaybackOrientation.Portrait),
                    stringResource(R.string.landscape) to (settings.orientation == PlaybackOrientation.Landscape),
                ),
                { updateDefaults(settings.copy(orientation = if (it == 0) PlaybackOrientation.Portrait else PlaybackOrientation.Landscape)) },
            )
            Text(stringResource(R.string.text_alignment), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
            ChoiceRow(
                listOf(
                    stringResource(R.string.align_start) to (settings.textAlignment == PlaybackTextAlignment.Start),
                    stringResource(R.string.align_center) to (settings.textAlignment == PlaybackTextAlignment.Center),
                    stringResource(R.string.align_end) to (settings.textAlignment == PlaybackTextAlignment.End),
                ),
                { index ->
                    updateDefaults(
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
            ChoiceRow(
                listOf(
                    stringResource(R.string.normal_display) to !settings.mirrorEnabled,
                    stringResource(R.string.mirrored_display) to settings.mirrorEnabled,
                ),
                { updateDefaults(settings.copy(mirrorEnabled = it == 1)) },
            )
        }
        SettingsCard(stringResource(R.string.default_scroll_mode)) {
            ChoiceRow(
                listOf(
                    stringResource(R.string.speed_mode) to (settings.rhythmMode == RhythmMode.Speed),
                    stringResource(R.string.target_time_mode) to (settings.rhythmMode == RhythmMode.TargetDuration),
                ),
                { updateDefaults(settings.copy(rhythmMode = if (it == 0) RhythmMode.Speed else RhythmMode.TargetDuration)) },
            )
            LabelValue(stringResource(R.string.speed), stringResource(R.string.speed_multiplier, settings.speedMultiplier))
            Slider(settings.speedMultiplier, { updateDefaults(settings.copy(speedMultiplier = it)) }, valueRange = .5f..2f)
        }
        SettingsCard(stringResource(R.string.countdown)) {
            val options = CountdownOption.entries
            ChoiceRow(
                options.map {
                    (if (it == CountdownOption.Off) stringResource(R.string.countdown_off) else stringResource(R.string.seconds_format, it.seconds)) to
                        (settings.countdown == it)
                },
                onSelected = { index -> updateDefaults(settings.copy(countdown = options[index])) },
            )
        }
        SettingsCard(stringResource(R.string.guide_line)) {
            ChoiceRow(
                listOf(
                    stringResource(R.string.guide_off) to (settings.guideMode == GuideMode.Off),
                    stringResource(R.string.guide_horizontal) to (settings.guideMode == GuideMode.Line),
                    stringResource(R.string.guide_highlight_bar) to (settings.guideMode == GuideMode.HighlightBar),
                ),
                { updateDefaults(settings.copy(guideMode = GuideMode.entries[it])) },
            )
            Slider(settings.guideLinePosition, { updateDefaults(settings.copy(guideLinePosition = it)) }, valueRange = .15f..0.75f)
        }
    }
}

@Composable
fun LanguageScreen(
    appState: AppState,
    onBack: () -> Unit,
    languageOverride: String? = null,
    onLanguageChange: ((String) -> Unit)? = null,
) {
    val selectedLanguage = languageOverride ?: appState.selectedLanguage
    val updateLanguage: (String) -> Unit = onLanguageChange ?: { appState.selectedLanguage = it }
    SettingsPage(title = stringResource(R.string.language_settings), onBack = onBack) {
        Text(stringResource(R.string.language_hint), color = AppColors.TextSecondary)
        AppCard(Modifier.fillMaxWidth(), onClick = { updateLanguage("zh-CN") }) {
            LanguageOption(stringResource(R.string.simplified_chinese), selectedLanguage == "zh-CN")
        }
        AppCard(Modifier.fillMaxWidth(), onClick = { updateLanguage("en-US") }) {
            LanguageOption(stringResource(R.string.english), selectedLanguage == "en-US")
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsPage(title = stringResource(R.string.about), onBack = onBack) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_mark),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.extraLarge),
                )
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.about_description),
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
        SettingsCard(stringResource(R.string.version_info)) {
            LabelValue(
                stringResource(R.string.version),
                stringResource(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            )
        }
    }
}

@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier.align(Alignment.TopCenter)
                .widthIn(max = if (maxWidth >= 840.dp) 860.dp else 620.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MotionIconButton(onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            content()
            Spacer(Modifier.height(AppSpacing.lg))
        }
    }
}

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = AppColors.Primary.copy(alpha = 0.14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.size(AppSpacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(summary, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.size(AppSpacing.sm))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColors.TextWeak,
            )
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
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = AppColors.TextSecondary)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
