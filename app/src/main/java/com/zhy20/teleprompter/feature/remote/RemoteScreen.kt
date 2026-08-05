package com.zhy20.teleprompter.feature.remote

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.components.AppCard
import com.zhy20.teleprompter.core.design.components.ConnectionStatusLabel
import com.zhy20.teleprompter.core.design.components.PrimaryButton
import com.zhy20.teleprompter.core.design.components.SecondaryButton
import com.zhy20.teleprompter.core.util.formatDuration
import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemoteFailureReason
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.pairing.RemotePairingPayload
import com.zhy20.teleprompter.remote.pairing.RemotePairingPayloadCodec

/**
 * Pure presentation layer for the remote controller. It renders [RemoteUiState], forwards
 * user intent as [RemoteUiAction] and never touches [com.zhy20.teleprompter.app.AppState],
 * the transport or the protocol models. Camera scanning is the only Android API it touches,
 * and only to obtain the pairing string.
 */
@Composable
fun RemoteScreen(
    state: RemoteUiState,
    onAction: (RemoteUiAction) -> Unit,
    onBack: () -> Unit,
    scanError: RemoteScanError? = null,
    onScanRequested: () -> Unit = {},
    onScanErrorDismiss: () -> Unit = {},
) {
    val snapshot = state.snapshot
    val section = RemoteUiMapper.sectionOf(state.status, snapshot, state.role, state.reconnecting)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Surface(color = AppColors.Surface) {
            Row(Modifier.fillMaxWidth().padding(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.remote_controller), style = MaterialTheme.typography.titleLarge)
                    ConnectionStatusLabel(state.status)
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
                when (section) {
                    RemoteUiSection.RoleSelection -> RoleSelectionPanel(onAction)
                    RemoteUiSection.PrompterReady -> PrompterReadyPanel(onAction)
                    RemoteUiSection.PrompterWaiting -> PrompterWaitingPanel(state.pairingPayload, onAction)
                    RemoteUiSection.ControllerReady -> ControllerReadyPanel(
                        scanError = scanError,
                        onScanRequested = onScanRequested,
                        onScanErrorDismiss = onScanErrorDismiss,
                        onAction = onAction,
                    )
                    RemoteUiSection.Connecting -> ConnectingPanel()
                    RemoteUiSection.ConnectionFailed -> FailedPanel(
                        (state.status as RemoteConnectionStatus.Failed).reason,
                        state.lastCommandError,
                        onAction,
                    )
                    RemoteUiSection.ConnectionLost -> ReconnectingPanel(onAction)
                    RemoteUiSection.ConnectedWaiting -> ConnectedWaitingPanel()
                    RemoteUiSection.Ready -> snapshot?.let { ReadyPanel(it, onAction) }
                    RemoteUiSection.Countdown -> snapshot?.let { CountdownRemotePanel(it.countdownSecondsRemaining ?: 0) }
                    RemoteUiSection.Playing -> snapshot?.let { PlayingRemotePanel(it, onAction, expanded) }
                    RemoteUiSection.Paused -> snapshot?.let { PausedRemotePanel(it, onAction) }
                    RemoteUiSection.Finished -> FinishedRemotePanel()
                }
            }
        }
    }
}

@Composable
private fun RoleSelectionPanel(onAction: (RemoteUiAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Icon(Icons.Default.QrCode2, null, Modifier.size(96.dp), tint = AppColors.Primary)
            Text(stringResource(R.string.remote_role_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.remote_role_hint), color = AppColors.TextSecondary, textAlign = TextAlign.Center)
            PrimaryButton(stringResource(R.string.remote_role_prompter), { onAction(RemoteUiAction.SelectPrompterRole) }, Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.remote_role_controller), { onAction(RemoteUiAction.SelectControllerRole) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PrompterReadyPanel(onAction: (RemoteUiAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.connection_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.prompter_role_description), color = AppColors.TextSecondary, textAlign = TextAlign.Center)
            PrimaryButton(stringResource(R.string.start_waiting), { onAction(RemoteUiAction.StartWaiting) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PrompterWaitingPanel(payload: RemotePairingPayload?, onAction: (RemoteUiAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.waiting_connection), style = MaterialTheme.typography.headlineMedium)
            if (payload != null) {
                val qrText = RemotePairingPayloadCodec.encode(payload)
                val bitmap = remember(qrText) { RemoteQrGenerator.encode(qrText) }
                Image(bitmap.asImageBitmap(), stringResource(R.string.demo_qr), Modifier.size(230.dp).clip(MaterialTheme.shapes.medium))
                Text(stringResource(R.string.prompter_address_format, payload.host, payload.port), color = AppColors.TextSecondary)
                Text(stringResource(R.string.pairing_expires_hint), color = AppColors.TextWeak)
            } else {
                Text(stringResource(R.string.pairing_initializing), color = AppColors.TextSecondary)
            }
            SecondaryButton(stringResource(R.string.regenerate_qr), { onAction(RemoteUiAction.StartWaiting) }, Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.cancel), { onAction(RemoteUiAction.CancelWaiting) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ControllerReadyPanel(
    scanError: RemoteScanError?,
    onScanRequested: () -> Unit,
    onScanErrorDismiss: () -> Unit,
    onAction: (RemoteUiAction) -> Unit,
) {
    var manualOpen by remember { mutableStateOf(false) }
    var manualError by remember { mutableStateOf<String?>(null) }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("8765") }
    var manualToken by remember { mutableStateOf("") }

    val manualInvalidText = stringResource(R.string.manual_invalid)
    val scanErrorText = when (scanError) {
        RemoteScanError.InvalidPairing -> stringResource(R.string.pairing_invalid)
        RemoteScanError.ExpiredPairing -> stringResource(R.string.pairing_expired)
        RemoteScanError.CameraDenied -> stringResource(R.string.camera_permission_denied)
        null -> null
    }

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.controller_role_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.controller_role_description), color = AppColors.TextSecondary, textAlign = TextAlign.Center)
            PrimaryButton(stringResource(R.string.scan_qr_code), onScanRequested, Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCode2, null) }
            SecondaryButton(stringResource(R.string.manual_connect), { manualOpen = true }, Modifier.fillMaxWidth())

            scanErrorText?.let { error ->
                Text(error, color = AppColors.Danger)
                SecondaryButton(stringResource(R.string.close), onScanErrorDismiss, Modifier.fillMaxWidth())
            }

            if (manualOpen) {
                OutlinedTextField(
                    value = manualHost,
                    onValueChange = { manualHost = it },
                    label = { Text(stringResource(R.string.manual_host)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = manualPort,
                    onValueChange = { manualPort = it },
                    label = { Text(stringResource(R.string.manual_port)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it },
                    label = { Text(stringResource(R.string.manual_token)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                manualError?.let { Text(it, color = AppColors.Danger) }
                PrimaryButton(stringResource(R.string.connect), {
                    val port = manualPort.toIntOrNull()
                    if (manualHost.isBlank() || port == null || port !in 1..65535 || manualToken.isBlank()) {
                        manualError = manualInvalidText
                    } else {
                        onAction(RemoteUiAction.ConnectManual(manualHost, port, "manual", manualToken))
                    }
                }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ConnectingPanel() {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.connecting), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.connection_hint), color = AppColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FailedPanel(reason: RemoteFailureReason, lastCommandError: String?, onAction: (RemoteUiAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.connection_lost), style = MaterialTheme.typography.headlineMedium, color = AppColors.Danger)
            Text(lastCommandError ?: failureText(reason), color = AppColors.TextSecondary)
            SecondaryButton(stringResource(R.string.retry), { onAction(RemoteUiAction.RetryConnection) }, Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.disconnect), { onAction(RemoteUiAction.Disconnect) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ReconnectingPanel(onAction: (RemoteUiAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.remote_reconnecting), style = MaterialTheme.typography.headlineMedium, color = AppColors.Danger)
            Text(stringResource(R.string.connection_lost_continue), color = AppColors.TextSecondary)
            SecondaryButton(stringResource(R.string.retry), { onAction(RemoteUiAction.RetryConnection) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConnectedWaitingPanel() {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.device_connected), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.waiting_for_setup), color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun ReadyPanel(snapshot: RemotePrompterSnapshot, onAction: (RemoteUiAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.current_script), color = AppColors.TextWeak)
            Text(snapshot.scriptTitle.orEmpty().ifBlank { stringResource(R.string.untitled_script) }, style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.ready), color = AppColors.Success)
                Text(
                    stringResource(
                        R.string.estimated_duration_format,
                        formatDuration(snapshot.estimatedDurationSeconds ?: 0),
                    ),
                    color = AppColors.TextSecondary,
                )
            }
            PrimaryButton(stringResource(R.string.start_playback), { onAction(RemoteUiAction.StartPlayback) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, null)
            }
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
private fun PlayingRemotePanel(snapshot: RemotePrompterSnapshot, onAction: (RemoteUiAction) -> Unit, expanded: Boolean) {
    if (expanded) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            NearbyTextCard(snapshot, Modifier.weight(1.2f))
            RemoteControls(snapshot, onAction, Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            NearbyTextCard(snapshot)
            RemoteControls(snapshot, onAction)
        }
    }
}

@Composable
private fun NearbyTextCard(snapshot: RemotePrompterSnapshot, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.nearby_text), color = AppColors.TextWeak, style = MaterialTheme.typography.labelMedium)
            Surface(color = AppColors.Secondary.copy(alpha = .25f), shape = MaterialTheme.shapes.medium) {
                Text(
                    snapshot.nearbyText.orEmpty().ifBlank { stringResource(R.string.empty_nearby_text) },
                    modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LinearProgressIndicator(
                progress = { snapshot.progress },
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.Primary,
                trackColor = AppColors.Secondary,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.progress_percent, (snapshot.progress * 100).toInt()), color = AppColors.TextSecondary)
                Text(
                    stringResource(
                        R.string.playback_elapsed_remaining,
                        formatDuration((snapshot.elapsedTimeMillis / 1_000L).toInt()),
                        formatDuration((snapshot.remainingTimeMillis / 1_000L).toInt()),
                    ),
                    color = AppColors.TextSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteControls(snapshot: RemotePrompterSnapshot, onAction: (RemoteUiAction) -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SecondaryButton(stringResource(R.string.speed_down), { onAction(RemoteUiAction.DecreaseSpeed) }, Modifier.weight(1f)) { Icon(Icons.Default.Remove, null) }
                Text(
                    stringResource(R.string.speed_multiplier, snapshot.speedMultiplier),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                )
                SecondaryButton(stringResource(R.string.speed_up), { onAction(RemoteUiAction.IncreaseSpeed) }, Modifier.weight(1f)) { Icon(Icons.Default.Add, null) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                SecondaryButton(stringResource(R.string.seek_backward), { onAction(RemoteUiAction.SeekBackward) }, Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.seek_forward), { onAction(RemoteUiAction.SeekForward) }, Modifier.weight(1f))
            }
            PrimaryButton(stringResource(R.string.pause), { onAction(RemoteUiAction.Pause) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Pause, null) }
            Text(
                stringResource(R.string.hold_to_end),
                color = AppColors.Danger,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .combinedClickable(onClick = {}, onLongClick = { onAction(RemoteUiAction.EndPlayback) })
                    .padding(AppSpacing.md),
            )
        }
    }
}

@Composable
private fun PausedRemotePanel(snapshot: RemotePrompterSnapshot, onAction: (RemoteUiAction) -> Unit) {
    NearbyTextCard(snapshot)
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.paused), style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                PrimaryButton(stringResource(R.string.resume_now), { onAction(RemoteUiAction.ResumeImmediately) }, Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.resume_countdown), { onAction(RemoteUiAction.ResumeWithCountdown) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                SecondaryButton(stringResource(R.string.seek_backward), { onAction(RemoteUiAction.SeekBackward) }, Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.seek_forward), { onAction(RemoteUiAction.SeekForward) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun failureText(reason: RemoteFailureReason): String = stringResource(
    when (reason) {
        RemoteFailureReason.HandshakeFailed -> R.string.remote_handshake_failed
        RemoteFailureReason.ProtocolMismatch -> R.string.remote_protocol_mismatch
        RemoteFailureReason.TransportUnavailable -> R.string.remote_transport_unavailable
        RemoteFailureReason.Rejected -> R.string.remote_rejected
        RemoteFailureReason.NoNetworkAddress -> R.string.remote_no_network
        RemoteFailureReason.InvalidPairing -> R.string.pairing_invalid
        RemoteFailureReason.ConnectionTimeout -> R.string.remote_connect_timeout
        RemoteFailureReason.AlreadyConnected -> R.string.remote_already_connected
        RemoteFailureReason.PortUnavailable -> R.string.remote_port_unavailable
    },
)
