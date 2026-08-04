package com.zhy20.teleprompter.feature.remote

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.RichScriptText
import com.zhy20.teleprompter.core.design.toComposeTextAlign
import com.zhy20.teleprompter.core.design.components.AppCard
import com.zhy20.teleprompter.core.design.components.ChoiceRow
import com.zhy20.teleprompter.core.design.components.ConnectionStatusLabel
import com.zhy20.teleprompter.core.design.components.PrimaryButton
import com.zhy20.teleprompter.core.design.components.SecondaryButton
import com.zhy20.teleprompter.core.model.PlaybackEvent
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.core.model.RemoteConnectionState
import com.zhy20.teleprompter.core.util.formatDuration

@Composable
fun RemoteScreen(appState: AppState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Surface(color = AppColors.Surface) {
            Row(Modifier.fillMaxWidth().padding(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.remote_controller), style = MaterialTheme.typography.titleLarge)
                    ConnectionStatusLabel(appState.remoteConnectionState)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 760.dp
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = if (expanded) 980.dp else 620.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                when (appState.remoteConnectionState) {
                    RemoteConnectionState.Disconnected -> ConnectionPanel(appState)
                    RemoteConnectionState.Waiting -> WaitingPanel(appState)
                    RemoteConnectionState.ConnectionLost -> LostPanel(appState)
                    RemoteConnectionState.Connected -> {
                        DemoStatePicker(appState)
                        when {
                            appState.prompterSurface in listOf(PrompterSurface.Library, PrompterSurface.Editor) -> ConnectedWaitingPanel()
                            appState.prompterSurface == PrompterSurface.Setup -> ReadyPanel(appState)
                            appState.playbackState is PlaybackState.Countdown -> CountdownRemotePanel((appState.playbackState as PlaybackState.Countdown).secondsRemaining)
                            appState.playbackState == PlaybackState.Paused -> PausedRemotePanel(appState)
                            appState.playbackState == PlaybackState.Playing -> PlayingRemotePanel(appState, expanded)
                            appState.playbackState in listOf(PlaybackState.Finished, PlaybackState.Exited) -> FinishedRemotePanel()
                            else -> ConnectedWaitingPanel()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionPanel(appState: AppState) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Icon(Icons.Default.QrCode2, null, Modifier.size(150.dp), tint = AppColors.TextPrimary)
            Text(stringResource(R.string.connection_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.connection_hint), color = AppColors.TextSecondary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.demo_qr), color = AppColors.TextWeak)
            PrimaryButton(stringResource(R.string.start_waiting), { appState.remoteConnectionState = RemoteConnectionState.Waiting }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WaitingPanel(appState: AppState) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Icon(Icons.Default.QrCode2, null, Modifier.size(130.dp), tint = AppColors.TextSecondary)
            Text(stringResource(R.string.waiting_connection), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.connection_hint), color = AppColors.TextSecondary, textAlign = TextAlign.Center)
            PrimaryButton(stringResource(R.string.simulate_connected), { appState.remoteConnectionState = RemoteConnectionState.Connected }, Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.cancel), { appState.remoteConnectionState = RemoteConnectionState.Disconnected }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LostPanel(appState: AppState) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.connection_lost), style = MaterialTheme.typography.headlineMedium, color = AppColors.Danger)
            Text(stringResource(R.string.connection_lost_continue), color = AppColors.TextSecondary)
            SecondaryButton(stringResource(R.string.retry), { appState.remoteConnectionState = RemoteConnectionState.Waiting }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DemoStatePicker(appState: AppState) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(stringResource(R.string.demo_state), color = AppColors.TextWeak, style = MaterialTheme.typography.labelMedium)
            ChoiceRow(
                listOf(
                    stringResource(R.string.home_state) to (appState.prompterSurface == PrompterSurface.Library),
                    stringResource(R.string.setup_state) to (appState.prompterSurface == PrompterSurface.Setup),
                    stringResource(R.string.playing_state) to (appState.prompterSurface == PrompterSurface.Prompter),
                    stringResource(R.string.lost_state) to false,
                ),
                onSelected = { index ->
                when (index) {
                    0 -> { appState.setSurface(PrompterSurface.Library); appState.playbackState = PlaybackState.Idle }
                    1 -> { appState.setSurface(PrompterSurface.Setup); appState.playbackState = PlaybackState.Preparing }
                    2 -> { appState.setSurface(PrompterSurface.Prompter); appState.playbackState = PlaybackState.Playing }
                    3 -> appState.remoteConnectionState = RemoteConnectionState.ConnectionLost
                }
                },
            )
        }
    }
}

@Composable
private fun ConnectedWaitingPanel() {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.connected_device), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.waiting_for_setup), color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun ReadyPanel(appState: AppState) {
    val script = appState.script(appState.selectedScriptId)
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.current_script), color = AppColors.TextWeak)
            Text(script.title, style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.ready), color = AppColors.Success)
                Text(
                    stringResource(
                        R.string.estimated_duration_format,
                        formatDuration(appState.normalEstimatedDurationSeconds(script.id)),
                    ),
                    color = AppColors.TextSecondary,
                )
            }
            PrimaryButton(stringResource(R.string.start_playback), {
                appState.setSurface(PrompterSurface.Prompter)
                appState.onPlaybackEvent(PlaybackEvent.StartPlayback)
            }, Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null) }
        }
    }
}

@Composable
private fun CountdownRemotePanel(secondsRemaining: Int) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(AppSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Text(stringResource(R.string.countdown), color = AppColors.TextSecondary)
            Text(secondsRemaining.toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FinishedRemotePanel() {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.playback_finished), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.playback_finished_body), color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun PlayingRemotePanel(appState: AppState, expanded: Boolean) {
    if (expanded) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            NearbyTextCard(appState, Modifier.weight(1.2f))
            RemoteControls(appState, Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            NearbyTextCard(appState)
            RemoteControls(appState)
        }
    }
}

@Composable
private fun NearbyTextCard(appState: AppState, modifier: Modifier = Modifier) {
    val script = appState.script(appState.selectedScriptId)
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.nearby_text), color = AppColors.TextWeak, style = MaterialTheme.typography.labelMedium)
            Surface(color = AppColors.Secondary.copy(alpha = .25f), shape = MaterialTheme.shapes.medium) {
                RichScriptText(
                    document = script.content,
                    modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    textAlign = appState.playbackSettings.textAlignment.toComposeTextAlign(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LinearProgressIndicator(progress = { appState.progress }, modifier = Modifier.fillMaxWidth(), color = AppColors.Primary, trackColor = AppColors.Secondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.progress_percent, (appState.progress * 100).toInt()), color = AppColors.TextSecondary)
                Text(
                    stringResource(
                        R.string.playback_elapsed_remaining,
                        formatDuration((appState.playbackSession.elapsedTimeMillis / 1_000L).toInt()),
                        formatDuration((appState.playbackSession.remainingTimeMillis / 1_000L).toInt()),
                    ),
                    color = AppColors.TextSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteControls(appState: AppState, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SecondaryButton(stringResource(R.string.speed_down), { appState.onPlaybackEvent(PlaybackEvent.DecreaseSpeed) }, Modifier.weight(1f)) { Icon(Icons.Default.Remove, null) }
                Text(
                    stringResource(R.string.speed_multiplier, appState.playbackSettings.speedMultiplier),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                )
                SecondaryButton(stringResource(R.string.speed_up), { appState.onPlaybackEvent(PlaybackEvent.IncreaseSpeed) }, Modifier.weight(1f)) { Icon(Icons.Default.Add, null) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                SecondaryButton(stringResource(R.string.seek_backward), { appState.onPlaybackEvent(PlaybackEvent.SeekBackwardSmall) }, Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.seek_forward), { appState.onPlaybackEvent(PlaybackEvent.SeekForwardSmall) }, Modifier.weight(1f))
            }
            PrimaryButton(stringResource(R.string.pause), { appState.onPlaybackEvent(PlaybackEvent.PausePlayback) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Pause, null) }
            Text(
                stringResource(R.string.hold_to_end),
                color = AppColors.Danger,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .combinedClickable(onClick = {}, onLongClick = { appState.onPlaybackEvent(PlaybackEvent.EndPlayback) })
                    .padding(AppSpacing.md),
            )
        }
    }
}

@Composable
private fun PausedRemotePanel(appState: AppState) {
    NearbyTextCard(appState)
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.paused), style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                PrimaryButton(stringResource(R.string.resume_now), { appState.onPlaybackEvent(PlaybackEvent.ResumeImmediately) }, Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.resume_countdown), { appState.onPlaybackEvent(PlaybackEvent.ResumeWithCountdown) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                SecondaryButton(stringResource(R.string.seek_backward), { appState.onPlaybackEvent(PlaybackEvent.SeekBackwardSmall) }, Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.seek_forward), { appState.onPlaybackEvent(PlaybackEvent.SeekForwardSmall) }, Modifier.weight(1f))
            }
        }
    }
}
