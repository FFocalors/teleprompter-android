package com.zhy20.teleprompter.feature.prompter

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.model.GuideMode
import com.zhy20.teleprompter.core.model.PlaybackEvent
import com.zhy20.teleprompter.core.model.PlaybackState
import kotlinx.coroutines.isActive

/** The single visual contract used by both the playing and paused control bars. */
enum class ControlBarMode {
    Hidden,
    Playing,
    Paused,
    Finished,
}

fun controlBarModeFor(
    playbackState: PlaybackState,
    controlsVisible: Boolean,
): ControlBarMode {
    if (!controlsVisible) return ControlBarMode.Hidden
    return when (playbackState) {
        PlaybackState.Playing -> ControlBarMode.Playing
        PlaybackState.Paused -> ControlBarMode.Paused
        PlaybackState.Finished -> ControlBarMode.Finished
        else -> ControlBarMode.Hidden
    }
}

@Composable
fun BoxScope.PlaybackControlBar(
    mode: ControlBarMode,
    appState: AppState,
    resumeCountdownEnabled: Boolean,
    onResumeCountdownEnabledChange: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
) {
    if (mode == ControlBarMode.Hidden) return
    val controlsDescription = stringResource(R.string.hide_playback_controls)

    Card(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth(.94f)
            .widthIn(max = 900.dp)
            // Playback owns a fixed edge-to-edge viewport. Dynamic system-bar insets would move
            // this card when transient bars are revealed, so its top position is intentionally
            // anchored to the fixed status band instead.
            .padding(top = 64.dp)
            .animateContentSize()
            .semantics {
                testTag = PlaybackControlsTestTag
                contentDescription = controlsDescription
            },
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface.copy(alpha = .94f)),
        border = BorderStroke(1.dp, AppColors.Border),
        shape = androidx.compose.material3.MaterialTheme.shapes.large,
    ) {
        BoxWithConstraints(Modifier.padding(AppSpacing.xs)) {
            val controlWidth = maxWidth
            val isWide = controlWidth >= 640.dp
            val isPaused = mode == ControlBarMode.Paused
            val primaryAction = when (mode) {
                ControlBarMode.Playing -> onPause
                ControlBarMode.Paused -> onResume
                ControlBarMode.Finished -> ({})
                ControlBarMode.Hidden -> return@BoxWithConstraints
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                PlaybackPrimaryRow(
                    appState = appState,
                    mode = mode,
                    isWide = isWide,
                    resumeCountdownEnabled = resumeCountdownEnabled,
                    onResumeCountdownEnabledChange = onResumeCountdownEnabledChange,
                    onInteraction = onInteraction,
                    onPrimaryAction = primaryAction,
                    onExit = onExit,
                )

                if (isWide) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProgressAdjustment(appState, onInteraction, Modifier.weight(1.25f))
                        GuideModeSelector(
                            selected = appState.playbackSettings.guideMode,
                            onSelected = {
                                appState.onPlaybackEvent(PlaybackEvent.ChangeGuideMode(it))
                                onInteraction()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    ProgressAdjustment(appState, onInteraction)
                    GuideModeSelector(
                        selected = appState.playbackSettings.guideMode,
                        onSelected = {
                            appState.onPlaybackEvent(PlaybackEvent.ChangeGuideMode(it))
                            onInteraction()
                        },
                    )
                }

                if (isPaused) GuidePositionAdjustment(appState, onInteraction)
            }
        }
    }
}

@Composable
private fun PlaybackPrimaryRow(
    appState: AppState,
    mode: ControlBarMode,
    isWide: Boolean,
    resumeCountdownEnabled: Boolean,
    onResumeCountdownEnabledChange: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onPrimaryAction: () -> Unit,
    onExit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LongPressExitControl(onExit)
                SpeedControlGroup(
                    appState = appState,
                    onInteraction = onInteraction,
                    modifier = Modifier.width(260.dp),
                )
                PlaybackPrimaryAction(mode = mode, onClick = onPrimaryAction)
                ResumeCountdownToggle(
                    checked = resumeCountdownEnabled,
                    onCheckedChange = onResumeCountdownEnabledChange,
                    modifier = Modifier.width(184.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LongPressExitControl(onExit)
                SpeedControlGroup(
                    appState = appState,
                    onInteraction = onInteraction,
                    modifier = Modifier.weight(1f),
                )
                PlaybackPrimaryAction(mode = mode, onClick = onPrimaryAction)
            }
            ResumeCountdownToggle(
                checked = resumeCountdownEnabled,
                onCheckedChange = onResumeCountdownEnabledChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SpeedControlGroup(
    appState: AppState,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(56.dp),
        color = AppColors.SurfaceRaised,
        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, AppColors.Border.copy(alpha = .72f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xxs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactControl(Icons.Default.Remove, stringResource(R.string.speed_down)) {
                appState.onPlaybackEvent(PlaybackEvent.DecreaseSpeed)
                onInteraction()
            }
            Column(
                modifier = Modifier.widthIn(min = 76.dp, max = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.current_speed),
                    color = AppColors.TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.speed_value, appState.playbackSettings.speedMultiplier),
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                )
            }
            CompactControl(Icons.Default.Add, stringResource(R.string.speed_up)) {
                appState.onPlaybackEvent(PlaybackEvent.IncreaseSpeed)
                onInteraction()
            }
        }
    }
}

@Composable
private fun PlaybackPrimaryAction(mode: ControlBarMode, onClick: () -> Unit) {
    val enabled = mode != ControlBarMode.Finished
    val icon = if (mode == ControlBarMode.Playing) Icons.Default.Pause else Icons.Default.PlayArrow
    val label = when (mode) {
        ControlBarMode.Playing -> stringResource(R.string.pause)
        ControlBarMode.Paused -> stringResource(R.string.resume_now)
        ControlBarMode.Finished -> stringResource(R.string.playback_finished)
        ControlBarMode.Hidden -> stringResource(R.string.resume_now)
    }
    Surface(
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = if (enabled) AppColors.Primary else AppColors.SurfaceRaised,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, tint = if (enabled) AppColors.OnPrimary else AppColors.TextWeak)
        }
    }
}

@Composable
private fun ResumeCountdownToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(56.dp),
        color = AppColors.SurfaceRaised,
        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, AppColors.Border.copy(alpha = .72f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AppSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.resume_countdown_toggle),
                color = AppColors.TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            Spacer(Modifier.width(AppSpacing.xs))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun LongPressExitControl(onExit: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    val holdToEndDescription = stringResource(R.string.hold_to_end)

    LaunchedEffect(pressed) {
        if (!pressed) {
            holdProgress = 0f
            completed = false
            return@LaunchedEffect
        }
        val startedAt = withFrameNanos { it }
        while (isActive && pressed && !completed) {
            val now = withFrameNanos { it }
            holdProgress = ((now - startedAt) / 900_000_000f).coerceIn(0f, 1f)
            if (holdProgress >= 1f) {
                completed = true
                onExit()
            }
        }
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                        }
                    },
                )
            }
            .semantics {
                contentDescription = holdToEndDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { holdProgress },
            modifier = Modifier.size(56.dp),
            color = AppColors.Danger,
            trackColor = AppColors.Danger.copy(alpha = .18f),
            strokeWidth = 3.dp,
        )
        Icon(Icons.Default.Close, contentDescription = null, tint = AppColors.Danger)
    }
}

@Composable
private fun ProgressAdjustment(
    appState: AppState,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressDescription = stringResource(R.string.current_progress)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.progress_percent, (appState.progress * 100).toInt()),
            modifier = Modifier
                .widthIn(min = 88.dp)
                .semantics { contentDescription = progressDescription },
            color = AppColors.TextSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = appState.progress,
            onValueChange = {
                if (!appState.playbackSession.isManualAdjusting) appState.beginManualProgressAdjustment()
                appState.onPlaybackEvent(PlaybackEvent.SeekTo(it))
                onInteraction()
            },
            onValueChangeFinished = appState::endManualProgressAdjustment,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = progressDescription },
        )
    }
}

@Composable
private fun GuidePositionAdjustment(appState: AppState, onInteraction: () -> Unit) {
    val guidePositionDescription = stringResource(R.string.guide_position)
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.guide_position),
            color = AppColors.TextSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = appState.playbackSettings.guideLinePosition,
            onValueChange = {
                appState.updateGuidePosition(it)
                onInteraction()
            },
            valueRange = .15f..0.75f,
            modifier = Modifier.semantics { contentDescription = guidePositionDescription },
        )
    }
}

@Composable
private fun GuideModeSelector(
    selected: GuideMode,
    onSelected: (GuideMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
        val labels = listOf(
            GuideMode.Off to stringResource(R.string.guide_off),
            GuideMode.Line to stringResource(R.string.guide_horizontal),
            GuideMode.HighlightBar to stringResource(R.string.guide_highlight_bar),
        )
        labels.forEach { (mode, label) ->
            val active = selected == mode
            Surface(
                onClick = { onSelected(mode) },
                modifier = Modifier.weight(1f).height(40.dp),
                color = if (active) AppColors.Secondary.copy(alpha = .72f) else AppColors.SurfaceRaised,
                border = BorderStroke(if (active) 2.dp else 1.dp, if (active) AppColors.Primary else AppColors.Border),
                shape = androidx.compose.material3.MaterialTheme.shapes.small,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = if (active) AppColors.TextPrimary else AppColors.TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = AppColors.TextPrimary)
    }
}
