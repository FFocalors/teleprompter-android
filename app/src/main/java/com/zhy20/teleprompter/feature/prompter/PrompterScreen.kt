package com.zhy20.teleprompter.feature.prompter

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.colorFromHex
import com.zhy20.teleprompter.core.design.scaleForViewport
import com.zhy20.teleprompter.core.design.components.PrimaryButton
import com.zhy20.teleprompter.core.design.components.PrompterViewport
import com.zhy20.teleprompter.core.design.components.PrompterViewportMode
import com.zhy20.teleprompter.core.design.components.SecondaryButton
import com.zhy20.teleprompter.core.model.GuideMode
import com.zhy20.teleprompter.core.model.PlaybackEvent
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.RemoteConnectionState
import com.zhy20.teleprompter.core.model.activeDisplayPreset
import com.zhy20.teleprompter.core.model.guideLineColorForBackground
import com.zhy20.teleprompter.core.util.PlaybackEngineState
import com.zhy20.teleprompter.core.util.PlaybackTouchPolicy
import com.zhy20.teleprompter.core.util.formatDuration
import com.zhy20.teleprompter.core.util.requestedActivityOrientation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

const val PlaybackScriptTestTag = "playbackScript"
const val PlaybackStatusTestTag = "playbackStatus"
const val AutomaticProgressTestTag = "automaticScrollProgress"
const val PlaybackControlsTestTag = "playbackControls"

@Composable
fun PrompterScreen(
    scriptId: String,
    appState: AppState,
    onExit: () -> Unit,
) {
    val view = LocalView.current
    val activity = view.context.findActivity()
    val density = LocalDensity.current.density
    var controlsVisible by remember { mutableStateOf(false) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    val script = appState.script(scriptId)
    val settings = appState.playbackSettings
    val session = appState.playbackSession

    DisposableEffect(view, activity) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val previousOrientation = activity.requestedOrientation
        onDispose { activity.requestedOrientation = previousOrientation }
    }
    LaunchedEffect(activity, settings.orientation) {
        activity?.requestedOrientation = settings.orientation.requestedActivityOrientation()
    }
    BackHandler { appState.resetPlayback(); onExit() }

    LaunchedEffect(appState.playbackState) {
        val state = appState.playbackState
        if (state is PlaybackState.Countdown) {
            delay(1_000)
            if (state.secondsRemaining <= 1) appState.finishCountdown()
            else appState.playbackState = PlaybackState.Countdown(state.secondsRemaining - 1)
        }
    }
    LaunchedEffect(appState.playbackState, session.layoutReady) {
        if (appState.playbackState == PlaybackState.Playing) {
            while (isActive && appState.playbackState == PlaybackState.Playing) {
                withFrameNanos(appState::onPlaybackFrame)
            }
        }
    }
    LaunchedEffect(controlsVisible, appState.playbackState, interactionVersion) {
        if (controlsVisible && appState.playbackState == PlaybackState.Playing) {
            delay(3_000)
            controlsVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(colorFromHex(settings.backgroundColor))) {
        val gesturesEnabled = appState.playbackState == PlaybackState.Playing && !controlsVisible
        val foreground = colorFromHex(settings.textColor)
        val guideColor = colorFromHex(settings.activeDisplayPreset().guideLineColorForBackground())
        val gestureModifier = Modifier.playbackTouchGestures(
            enabled = gesturesEnabled,
            density = density,
            onTap = { controlsVisible = true; interactionVersion++ },
            onDoubleTap = { appState.onPlaybackEvent(PlaybackEvent.PausePlayback) },
            onVerticalDragStart = appState::beginManualProgressAdjustment,
            onVerticalDrag = { delta -> appState.onPlaybackEvent(PlaybackEvent.SeekTo(appState.progress - delta)) },
            onVerticalDragEnd = appState::endManualProgressAdjustment,
        )

        Box(Modifier.fillMaxSize().then(gestureModifier)) {
            PrompterViewport(
                document = script.content,
                settings = settings,
                mode = PrompterViewportMode.Playback,
                foreground = foreground,
                guideColor = guideColor,
                session = session,
                modifier = Modifier.fillMaxSize(),
                scriptTestTag = PlaybackScriptTestTag,
                onLayoutMeasured = { metrics ->
                    appState.updatePlaybackLayout(metrics.contentViewportHeightPx, metrics.textMeasuredHeightPx)
                },
                statusContent = { _, viewportScale ->
                    PlaybackStatus(
                        session = session,
                        foreground = foreground,
                        viewportScale = viewportScale,
                        onExit = {
                            appState.resetPlayback()
                            onExit()
                        },
                    )
                },
            )

            if (appState.remoteConnectionState == RemoteConnectionState.ConnectionLost) {
                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(AppSpacing.lg),
                    color = AppColors.Surface.copy(alpha = .92f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.connection_lost_continue), Modifier.padding(AppSpacing.sm), color = AppColors.TextSecondary)
                }
            }

            when (val state = appState.playbackState) {
                is PlaybackState.Countdown -> CountdownOverlay(state.secondsRemaining)
                PlaybackState.Paused -> PauseControls(appState, onExit)
                PlaybackState.Finished -> Unit
                PlaybackState.Playing -> if (controlsVisible) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(density) {
                                detectTapGestures { offset ->
                                    if (PlaybackTouchPolicy.centralRegion(size.width.toFloat(), size.height.toFloat(), density)
                                            .contains(offset.x, offset.y)
                                    ) controlsVisible = false
                                }
                            },
                    )
                    PlaybackControls(
                        appState = appState,
                        onInteraction = { interactionVersion++ },
                        onPause = { appState.onPlaybackEvent(PlaybackEvent.PausePlayback); controlsVisible = false },
                        onExit = { appState.resetPlayback(); onExit() },
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun PlaybackStatus(
    session: PlaybackEngineState,
    foreground: androidx.compose.ui.graphics.Color,
    viewportScale: Float,
    onExit: () -> Unit,
) {
    val labelStyle = MaterialTheme.typography.labelLarge.scaleForViewport(viewportScale)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg * viewportScale, vertical = AppSpacing.sm * viewportScale)
            .semantics { testTag = PlaybackStatusTestTag },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            stringResource(R.string.elapsed, formatDuration((session.elapsedTimeMillis / 1_000L).toInt())),
            color = foreground.copy(alpha = .72f),
            style = labelStyle,
        )
        if (session.playbackState == PlaybackState.Finished) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.playback_finished),
                    color = foreground.copy(alpha = .78f),
                    style = labelStyle,
                )
                IconButton(onClick = onExit, modifier = Modifier.size(40.dp * viewportScale)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.exit_to_setup),
                        tint = foreground,
                    )
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp * viewportScale)) {
                Text(
                    stringResource(R.string.remaining, formatDuration((session.remainingTimeMillis / 1_000L).toInt())),
                    color = foreground.copy(alpha = .72f),
                    style = labelStyle,
                )
                if (session.showAutomaticProgress) {
                    LinearProgressIndicator(
                        progress = { session.currentSemanticProgress },
                        modifier = Modifier
                            .widthIn(min = 96.dp * viewportScale, max = 180.dp * viewportScale)
                            .height(6.dp * viewportScale)
                            .semantics { testTag = AutomaticProgressTestTag },
                        color = AppColors.Primary,
                        trackColor = foreground.copy(alpha = .16f),
                    )
                }
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
private fun BoxScope.PlaybackControls(
    appState: AppState,
    onInteraction: () -> Unit,
    onPause: () -> Unit,
    onExit: () -> Unit,
) {
    Card(
        modifier = Modifier.overlayModifier(this)
            .semantics { testTag = PlaybackControlsTestTag },
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface.copy(alpha = .94f)),
        border = BorderStroke(1.dp, AppColors.Border),
        shape = MaterialTheme.shapes.large,
    ) {
        BoxWithConstraints(Modifier.padding(8.dp)) {
            val controlWidth = maxWidth
            if (controlWidth >= 760.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlaybackMainActions(appState, onInteraction, onPause, onExit)
                    ProgressAdjustment(appState, onInteraction, Modifier.weight(1.2f))
                    GuideModeSelector(
                        selected = appState.playbackSettings.guideMode,
                        onSelected = { appState.onPlaybackEvent(PlaybackEvent.ChangeGuideMode(it)); onInteraction() },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        PlaybackMainActions(appState, onInteraction, onPause, onExit)
                    }
                    if (controlWidth >= 600.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProgressAdjustment(appState, onInteraction, Modifier.weight(1.25f))
                        GuideModeSelector(
                            selected = appState.playbackSettings.guideMode,
                            onSelected = { appState.onPlaybackEvent(PlaybackEvent.ChangeGuideMode(it)); onInteraction() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    } else {
                        ProgressAdjustment(appState, onInteraction)
                        GuideModeSelector(
                            selected = appState.playbackSettings.guideMode,
                            onSelected = { appState.onPlaybackEvent(PlaybackEvent.ChangeGuideMode(it)); onInteraction() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PlaybackMainActions(
    appState: AppState,
    onInteraction: () -> Unit,
    onPause: () -> Unit,
    onExit: () -> Unit,
) {
    CompactControl(Icons.Default.Close, stringResource(R.string.end_playback), onExit)
    CompactControl(Icons.Default.Remove, stringResource(R.string.speed_down)) {
        appState.onPlaybackEvent(PlaybackEvent.DecreaseSpeed); onInteraction()
    }
    Text(
        stringResource(R.string.speed_multiplier, appState.playbackSettings.speedMultiplier),
        fontWeight = FontWeight.Bold,
        color = AppColors.TextPrimary,
    )
    CompactControl(Icons.Default.Add, stringResource(R.string.speed_up)) {
        appState.onPlaybackEvent(PlaybackEvent.IncreaseSpeed); onInteraction()
    }
    Surface(shape = CircleShape, color = AppColors.Primary) {
        IconButton(onPause, Modifier.size(48.dp)) {
            Icon(Icons.Default.Pause, stringResource(R.string.pause), tint = AppColors.OnPrimary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxScope.PauseControls(appState: AppState, onExit: () -> Unit) {
    Card(
        modifier = Modifier.overlayModifier(this),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface.copy(alpha = .96f)),
        border = BorderStroke(1.dp, AppColors.Border),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.paused), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(
                    onClick = { appState.resetPlayback(); onExit() },
                    modifier = Modifier.size(48.dp),
                ) { Icon(Icons.Default.Close, stringResource(R.string.end_playback)) }
            }
            ProgressAdjustment(appState, onInteraction = {})
            Text(stringResource(R.string.guide_position), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = appState.playbackSettings.guideLinePosition,
                onValueChange = appState::updateGuidePosition,
                valueRange = .15f..0.75f,
            )
            GuideModeSelector(
                selected = appState.playbackSettings.guideMode,
                onSelected = { appState.onPlaybackEvent(PlaybackEvent.ChangeGuideMode(it)) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                PrimaryButton(
                    stringResource(R.string.resume_now),
                    { appState.onPlaybackEvent(PlaybackEvent.ResumeImmediately) },
                    Modifier.weight(1f),
                ) { Icon(Icons.Default.PlayArrow, null) }
                SecondaryButton(
                    stringResource(R.string.resume_countdown),
                    { appState.onPlaybackEvent(PlaybackEvent.ResumeWithCountdown) },
                    Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.hold_to_end),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { appState.resetPlayback(); onExit() },
                    )
                    .align(Alignment.CenterHorizontally)
                    .padding(AppSpacing.sm),
                color = AppColors.Danger,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ProgressAdjustment(appState: AppState, onInteraction: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.progress_percent, (appState.progress * 100).toInt()),
            modifier = Modifier.widthIn(min = 88.dp),
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = appState.progress,
            onValueChange = {
                if (!appState.playbackSession.isManualAdjusting) appState.beginManualProgressAdjustment()
                appState.onPlaybackEvent(PlaybackEvent.SeekTo(it))
                onInteraction()
            },
            onValueChangeFinished = appState::endManualProgressAdjustment,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GuideModeSelector(selected: GuideMode, onSelected: (GuideMode) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                shape = MaterialTheme.shapes.small,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, color = if (active) AppColors.TextPrimary else AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
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

@Composable
private fun Modifier.overlayModifier(boxScope: BoxScope): Modifier = with(boxScope) {
    this@overlayModifier.align(Alignment.TopCenter)
}
    .fillMaxWidth(.94f)
    .widthIn(max = 900.dp)
    .windowInsetsPadding(WindowInsets.safeDrawing)
    .padding(top = 32.dp)

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
