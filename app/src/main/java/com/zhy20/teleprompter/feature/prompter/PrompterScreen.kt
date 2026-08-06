package com.zhy20.teleprompter.feature.prompter

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.colorFromHex
import com.zhy20.teleprompter.core.design.scaleForViewport
import com.zhy20.teleprompter.core.design.components.PrompterViewport
import com.zhy20.teleprompter.core.design.components.PrompterViewportMode
import com.zhy20.teleprompter.core.model.PlaybackEvent
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.activeDisplayPreset
import com.zhy20.teleprompter.core.model.guideLineColorForBackground
import com.zhy20.teleprompter.core.util.PlaybackEngineState
import com.zhy20.teleprompter.core.util.formatDuration
import com.zhy20.teleprompter.core.util.requestedActivityOrientation
import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
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
    scriptOverride: Script? = null,
    remoteConnectionStatus: RemoteConnectionStatus = RemoteConnectionStatus.Disabled,
) {
    val view = LocalView.current
    val activity = view.context.findActivity()
    val density = LocalDensity.current.density
    var controlsVisible by remember { mutableStateOf(false) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    var resumeCountdownEnabled by rememberSaveable { mutableStateOf(false) }
    val script = scriptOverride ?: appState.script(scriptId)
    val settings = appState.playbackSettings
    val session = appState.playbackSession
    val connectionLost = remoteConnectionStatus is RemoteConnectionStatus.Reconnecting ||
        remoteConnectionStatus is RemoteConnectionStatus.Failed

    DisposableEffect(view, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
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

    // Clear the reported nearby text when leaving the prompter page so the controller never
    // keeps stale text on Library/Editor/Setup.
    DisposableEffect(Unit) {
        onDispose { appState.updatePlaybackReadingText(null) }
    }

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
    LaunchedEffect(appState.playbackState) {
        when (appState.playbackState) {
            PlaybackState.Paused -> controlsVisible = true
            PlaybackState.Finished -> controlsVisible = true
            is PlaybackState.Countdown -> controlsVisible = false
            else -> Unit
        }
    }
    LaunchedEffect(controlsVisible, appState.playbackState, interactionVersion) {
        if (controlsVisible && appState.playbackState == PlaybackState.Playing) {
            delay(3_000)
            controlsVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(colorFromHex(settings.backgroundColor))) {
        val playbackState = appState.playbackState
        val gesturesEnabled = playbackState == PlaybackState.Playing && !controlsVisible
        val showControlsDescription = stringResource(R.string.show_playback_controls)
        val foreground = colorFromHex(settings.textColor)
        val guideColor = colorFromHex(settings.activeDisplayPreset().guideLineColorForBackground())
        val gestureModifier = when {
            gesturesEnabled -> Modifier.playbackTouchGestures(
                enabled = true,
                density = density,
                onTap = { controlsVisible = true; interactionVersion++ },
                onDoubleTap = {
                    appState.onPlaybackEvent(PlaybackEvent.PausePlayback)
                    controlsVisible = true
                },
                onVerticalDragStart = appState::beginManualProgressAdjustment,
                onVerticalDrag = { delta -> appState.onPlaybackEvent(PlaybackEvent.SeekTo(appState.progress - delta)) },
                onVerticalDragEnd = appState::endManualProgressAdjustment,
            ).semantics { contentDescription = showControlsDescription }
            playbackState == PlaybackState.Paused || playbackState == PlaybackState.Finished -> Modifier.centralContentTouchGestures(
                density = density,
                onTap = { controlsVisible = !controlsVisible },
                onVerticalDragStart = appState::beginManualProgressAdjustment,
                onVerticalDrag = { delta -> appState.onPlaybackEvent(PlaybackEvent.SeekTo(appState.progress - delta)) },
                onVerticalDragEnd = appState::endManualProgressAdjustment,
            ).semantics { contentDescription = showControlsDescription }
            else -> Modifier
        }

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
                    appState.updatePlaybackLayout(
                        metrics.contentViewportHeightPx,
                        metrics.textMeasuredHeightPx,
                        lineHeightPx = metrics.lineHeightPx,
                    )
                },
                statusContent = { _, viewportScale ->
                    PlaybackStatus(
                        session = session,
                        foreground = foreground,
                        viewportScale = viewportScale,
                    )
                },
                onNearbyTextChanged = { update ->
                    appState.updatePlaybackReadingText(update)
                },
            )

            if (connectionLost) {
                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(AppSpacing.lg),
                    color = AppColors.Surface.copy(alpha = .92f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.connection_lost_continue), Modifier.padding(AppSpacing.sm), color = AppColors.TextSecondary)
                }
            }

            val controlMode = controlBarModeFor(
                playbackState = playbackState,
                controlsVisible = controlsVisible,
            )
            if (controlMode != ControlBarMode.Hidden) {
                if (playbackState == PlaybackState.Playing) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .centralContentTap(density = density, onTap = { controlsVisible = false }),
                    )
                }
                PlaybackControlBar(
                    mode = controlMode,
                    appState = appState,
                    resumeCountdownEnabled = resumeCountdownEnabled,
                    onResumeCountdownEnabledChange = {
                        resumeCountdownEnabled = it
                        interactionVersion++
                    },
                    onInteraction = { interactionVersion++ },
                    onPause = {
                        appState.onPlaybackEvent(PlaybackEvent.PausePlayback)
                        controlsVisible = true
                    },
                    onResume = {
                        appState.onPlaybackEvent(
                            if (resumeCountdownEnabled) PlaybackEvent.ResumeWithCountdown
                            else PlaybackEvent.ResumeImmediately,
                        )
                        controlsVisible = false
                        interactionVersion++
                    },
                    onExit = { appState.resetPlayback(); onExit() },
                )
            }

            if (playbackState is PlaybackState.Countdown) {
                CountdownOverlay(playbackState.secondsRemaining)
            }
        }
    }
}

@Composable
private fun PlaybackStatus(
    session: PlaybackEngineState,
    foreground: androidx.compose.ui.graphics.Color,
    viewportScale: Float,
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
            Text(
                stringResource(R.string.playback_finished),
                color = foreground.copy(alpha = .78f),
                style = labelStyle,
            )
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

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
