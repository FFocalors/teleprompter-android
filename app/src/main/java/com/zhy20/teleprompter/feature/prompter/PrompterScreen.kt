package com.zhy20.teleprompter.feature.prompter

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.RichScriptText
import com.zhy20.teleprompter.core.design.colorFromHex
import com.zhy20.teleprompter.core.design.components.PrimaryButton
import com.zhy20.teleprompter.core.design.components.SecondaryButton
import com.zhy20.teleprompter.core.model.GuideLineStyle
import com.zhy20.teleprompter.core.model.PlaybackEvent
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.RemoteConnectionState
import com.zhy20.teleprompter.core.model.activeDisplayPreset
import com.zhy20.teleprompter.core.model.guideLineColorForBackground
import com.zhy20.teleprompter.core.util.formatDuration
import kotlinx.coroutines.delay

@Composable
fun PrompterScreen(
    scriptId: String,
    appState: AppState,
    onExit: () -> Unit,
) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    val density = LocalDensity.current.density
    var controlsVisible by remember { mutableStateOf(false) }
    val script = appState.script(scriptId)
    val settings = appState.playbackSettings

    DisposableEffect(view, activity) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    BackHandler { appState.resetPlayback(); onExit() }

    LaunchedEffect(appState.playbackState) {
        when (val state = appState.playbackState) {
            is PlaybackState.Countdown -> {
                delay(1_000)
                if (state.secondsRemaining <= 1) appState.finishCountdown()
                else appState.playbackState = PlaybackState.Countdown(state.secondsRemaining - 1)
            }
            PlaybackState.Playing -> {
                while (appState.playbackState == PlaybackState.Playing) {
                    delay(500)
                    val next = appState.progress + 0.0025f * settings.speedMultiplier
                    if (next >= 1f) {
                        appState.progress = 1f
                        appState.playbackState = PlaybackState.Finished
                    } else appState.progress = next
                }
            }
            else -> Unit
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colorFromHex(settings.backgroundColor))) {
        val contentWidth = maxWidth
        val gesturesEnabled = appState.playbackState == PlaybackState.Playing && !controlsVisible
        val gestureModifier = Modifier.playbackTouchGestures(
            enabled = gesturesEnabled,
            density = density,
            onTap = { controlsVisible = true },
            onDoubleTap = { appState.onPlaybackEvent(PlaybackEvent.PausePlayback) },
            onVerticalDrag = { delta -> appState.onPlaybackEvent(PlaybackEvent.SeekTo(appState.progress - delta)) },
        )

        val foreground = colorFromHex(settings.textColor)
        val guideY = maxHeight * settings.guideLinePosition
        val guideLineColor = colorFromHex(settings.activeDisplayPreset().guideLineColorForBackground())
        val elapsed = (script.normalEstimatedDurationSeconds * appState.progress).toInt()
        val remaining = (script.normalEstimatedDurationSeconds - elapsed).coerceAtLeast(0)

        Box(Modifier.fillMaxSize().then(gestureModifier)) {
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = if (contentWidth >= 700.dp) 72.dp else 28.dp)
                    .graphicsLayer { scaleX = if (settings.mirrorEnabled) -1f else 1f },
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xl),
            ) {
                RichScriptText(
                    document = script.content,
                    color = foreground,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = settings.fontSize.sp,
                        lineHeight = (settings.fontSize * 1.18f).sp,
                    ),
                )
            }

            if (settings.guideLineEnabled) {
                when (settings.guideLineStyle) {
                    GuideLineStyle.Highlight -> {
                        Box(
                            Modifier.fillMaxWidth().height(56.dp).offset(y = guideY - 28.dp)
                                .background(guideLineColor.copy(alpha = .28f)),
                        )
                        Box(Modifier.fillMaxWidth().height(3.dp).offset(y = guideY + 26.dp).background(guideLineColor))
                    }
                    GuideLineStyle.Line -> Box(
                        Modifier.fillMaxWidth().height(3.dp).offset(y = guideY).background(guideLineColor),
                    )
                }
            }

            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.progress_percent, (appState.progress * 100).toInt()),
                    color = foreground.copy(alpha = .68f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(R.string.playback_elapsed_remaining, formatDuration(elapsed), formatDuration(remaining)),
                    color = foreground.copy(alpha = .68f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (appState.remoteConnectionState == RemoteConnectionState.ConnectionLost) {
                Surface(Modifier.align(Alignment.BottomCenter).padding(AppSpacing.lg), color = AppColors.Surface, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.connection_lost_continue), Modifier.padding(AppSpacing.sm), color = AppColors.TextSecondary)
                }
            }

            when (val state = appState.playbackState) {
                is PlaybackState.Countdown -> CountdownOverlay(state.secondsRemaining)
                PlaybackState.Paused -> PauseOverlay(appState, elapsed, remaining, onExit)
                PlaybackState.Finished -> FinishedOverlay(onExit)
                else -> if (controlsVisible) PlaybackControls(
                    progress = appState.progress,
                    onProgressChange = { appState.onPlaybackEvent(PlaybackEvent.SeekTo(it)) },
                    onPause = { appState.onPlaybackEvent(PlaybackEvent.PausePlayback); controlsVisible = false },
                    onToggleGuide = { appState.onPlaybackEvent(PlaybackEvent.ToggleGuideLine) },
                    onGuideStyle = { appState.onPlaybackEvent(PlaybackEvent.ChangeGuideLineStyle(if (settings.guideLineStyle == GuideLineStyle.Highlight) GuideLineStyle.Line else GuideLineStyle.Highlight)) },
                    onExit = { appState.resetPlayback(); onExit() },
                )
            }
        }
    }
}

@Composable
private fun CountdownOverlay(seconds: Int) {
    Box(Modifier.fillMaxSize().background(AppColors.Scrim), contentAlignment = Alignment.Center) {
        Text(seconds.toString(), color = AppColors.TextPrimary, fontSize = 112.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlaybackControls(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onPause: () -> Unit,
    onToggleGuide: () -> Unit,
    onGuideStyle: () -> Unit,
    onExit: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(AppColors.Scrim), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    ControlIcon(Icons.Default.Close, stringResource(R.string.end_playback), onExit)
                    ControlIcon(Icons.Default.SwapVert, stringResource(R.string.guide_line), onToggleGuide)
                    Surface(shape = CircleShape, color = AppColors.Primary) {
                        IconButton(onPause, Modifier.size(104.dp)) { Icon(Icons.Default.Pause, stringResource(R.string.pause), Modifier.size(52.dp), tint = AppColors.OnPrimary) }
                    }
                    ControlIcon(Icons.Default.HorizontalRule, stringResource(R.string.guide_horizontal), onGuideStyle)
                    ControlIcon(Icons.Default.Settings, stringResource(R.string.prompt_settings), onExit)
                }
                Slider(value = progress, onValueChange = onProgressChange, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ControlIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(60.dp)) { Icon(icon, label, Modifier.size(30.dp), tint = AppColors.TextPrimary) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PauseOverlay(appState: AppState, elapsed: Int, remaining: Int, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppColors.Scrim), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.paused), style = MaterialTheme.typography.headlineLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                    Text(stringResource(R.string.elapsed, formatDuration(elapsed)), color = AppColors.TextSecondary)
                    Text(stringResource(R.string.remaining, formatDuration(remaining)), color = AppColors.TextSecondary)
                }
                Text(stringResource(R.string.progress_percent, (appState.progress * 100).toInt()), color = AppColors.TextWeak)
                Slider(appState.progress, { appState.onPlaybackEvent(PlaybackEvent.SeekTo(it)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.guide_position), color = AppColors.TextSecondary)
                Slider(appState.playbackSettings.guideLinePosition, appState::updateGuidePosition, valueRange = .15f..0.75f, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    PrimaryButton(stringResource(R.string.resume_now), { appState.onPlaybackEvent(PlaybackEvent.ResumeImmediately) }) { Icon(Icons.Default.PlayArrow, null) }
                    SecondaryButton(stringResource(R.string.resume_countdown), { appState.onPlaybackEvent(PlaybackEvent.ResumeWithCountdown) })
                }
                Text(
                    stringResource(R.string.hold_to_end),
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { appState.resetPlayback(); onExit() }).padding(AppSpacing.md),
                    color = AppColors.Danger,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun FinishedOverlay(onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppColors.Scrim), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                Text(stringResource(R.string.playback_finished), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.playback_finished_body), color = AppColors.TextSecondary, textAlign = TextAlign.Center)
                PrimaryButton(stringResource(R.string.exit_to_setup), onExit)
            }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
