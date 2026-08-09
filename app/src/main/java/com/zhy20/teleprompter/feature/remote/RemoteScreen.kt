package com.zhy20.teleprompter.feature.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.components.AppCard
import com.zhy20.teleprompter.core.design.components.ConnectionStatusLabel
import com.zhy20.teleprompter.core.design.components.PrimaryButton
import com.zhy20.teleprompter.core.design.components.SecondaryButton
import com.zhy20.teleprompter.core.design.components.roundedClickable
import com.zhy20.teleprompter.core.util.formatDuration
import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemoteFailureReason
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemoteReadingCursor
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.pairing.RemotePairingPayload
import com.zhy20.teleprompter.remote.pairing.RemotePairingPayloadCodec
import kotlinx.coroutines.flow.StateFlow

/**
 * Pure presentation layer for the remote controller. It renders [RemoteUiState], forwards
 * user intent as [RemoteUiAction] and never touches [com.zhy20.teleprompter.app.AppState],
 * the transport or the protocol models. Camera scanning is the only Android API it touches,
 * and only to obtain the pairing string.
 */
const val SpeedDecreaseTestTag = "remoteSpeedDecrease"
const val SpeedIncreaseTestTag = "remoteSpeedIncrease"
const val NearbyTextTestTag = "remoteNearbyText"
@Composable
fun RemoteScreen(
    state: RemoteUiState,
    readingCursorUpdates: StateFlow<RemoteReadingCursor?>? = null,
    onAction: (RemoteUiAction) -> Unit,
    onBack: () -> Unit,
    scanError: RemoteScanError? = null,
    onScanRequested: () -> Unit = {},
    onScanErrorDismiss: () -> Unit = {},
) {
    val snapshot = state.snapshot
    val section = RemoteUiMapper.sectionOf(state.status, snapshot, state.role, state.reconnecting)
    val disconnectFromPrompterDesc = stringResource(R.string.disconnect_from_prompter)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Surface(color = AppColors.Surface) {
            Row(Modifier.fillMaxWidth().padding(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.remote_control_title), style = MaterialTheme.typography.titleLarge)
                    RemoteHeaderSubtitle(state.role, state.status)
                }
                // Controller-side explicit disconnect entry: a narrow icon button that never
                // squeezes the title or status.
                if (state.role == RemoteRole.Controller &&
                    state.status is RemoteConnectionStatus.Connected
                ) {
                    IconButton(
                        onClick = { onAction(RemoteUiAction.DisconnectFromPrompter) },
                        modifier = Modifier.semantics { contentDescription = disconnectFromPrompterDesc },
                    ) {
                        Icon(Icons.Default.LinkOff, null)
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 760.dp
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = if (expanded) 980.dp else 620.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.lg)
                    .padding(top = AppSpacing.lg)
                    .padding(bottom = AppSpacing.xl)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                when (section) {
                    RemoteUiSection.RoleSelection -> RoleSelectionPanel(onAction)
                    RemoteUiSection.PrompterReady -> PrompterReadyPanel(onAction)
                    RemoteUiSection.PrompterWaiting -> PrompterWaitingPanel(state.pairingPayload, onAction)
                    RemoteUiSection.PrompterConnected -> PrompterConnectedPanel(state, onAction)
                    RemoteUiSection.ControllerReady -> ControllerReadyPanel(
                        scanError = scanError,
                        onScanRequested = onScanRequested,
                        onScanErrorDismiss = onScanErrorDismiss,
                        onAction = onAction,
                    )
                    RemoteUiSection.Connecting -> ConnectingPanel(onAction)
                    RemoteUiSection.ConnectionFailed -> FailedPanel(
                        (state.status as RemoteConnectionStatus.Failed).reason,
                        state.lastCommandError,
                        onAction,
                    )
                    RemoteUiSection.ConnectionLost -> ReconnectingPanel(onAction)
                    RemoteUiSection.ConnectedWaiting -> ConnectedWaitingPanel(state)
                    RemoteUiSection.Ready -> snapshot?.let { ReadyPanel(it, onAction) }
                    RemoteUiSection.Countdown -> snapshot?.let { CountdownRemotePanel(it.countdownSecondsRemaining ?: 0) }
                    RemoteUiSection.Playing -> snapshot?.let {
                        PlayingRemotePanel(state, it, onAction, expanded, readingCursorUpdates)
                    }
                    RemoteUiSection.Paused -> snapshot?.let {
                        PausedRemotePanel(state, it, onAction, readingCursorUpdates)
                    }
                    RemoteUiSection.Finished -> FinishedRemotePanel()
                }
                Spacer(Modifier.height(AppSpacing.lg))
            }
        }
    }
}

/**
 * The header subtitle: the current role next to the live connection status. Showing the role
 * here ("提词端" / "控制端") keeps the two phones unambiguous on every panel, since the screen
 * title itself is the neutral "远程控制".
 */
@Composable
private fun RemoteHeaderSubtitle(role: RemoteRole?, status: RemoteConnectionStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (role != null) {
            val roleLabel = stringResource(
                when (role) {
                    RemoteRole.Prompter -> R.string.role_prompter_label
                    RemoteRole.Controller -> R.string.role_controller_label
                },
            )
            Text(roleLabel, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text("  ·  ", color = AppColors.TextWeak, style = MaterialTheme.typography.labelMedium)
        }
        ConnectionStatusLabel(status)
    }
}

/**
 * A centered "hero" block shared by every connection-flow panel: a tinted circular icon badge
 * over a title and an optional description. Using one visual template across the waiting,
 * connecting, failed and connected states keeps the whole flow visually consistent.
 */
@Composable
private fun PanelHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    iconTint: Color = AppColors.Primary,
    description: String? = null,
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Surface(Modifier.size(64.dp), shape = CircleShape, color = iconTint.copy(alpha = 0.14f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = iconTint)
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (description != null) {
            Text(description, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

/**
 * A de-emphasized tertiary action (full-width text button). Used for secondary paths such as
 * "choose role again", "cancel" or the destructive "stop hosting", so the one primary action on
 * each panel keeps the visual focus.
 */
@Composable
private fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AppColors.TextSecondary,
) {
    TextButton(onClick = onClick, modifier = modifier.height(48.dp)) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

// ---- role selection ----

@Composable
private fun RoleSelectionPanel(onAction: (RemoteUiAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = AppSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            Text(
                stringResource(R.string.remote_role_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.remote_role_hint),
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        RoleCard(
            icon = Icons.Default.Slideshow,
            title = stringResource(R.string.remote_role_prompter),
            description = stringResource(R.string.role_prompter_desc),
            onClick = { onAction(RemoteUiAction.SelectPrompterRole) },
        )
        RoleCard(
            icon = Icons.Default.PhoneAndroid,
            title = stringResource(R.string.remote_role_controller),
            description = stringResource(R.string.role_controller_desc),
            onClick = { onAction(RemoteUiAction.SelectControllerRole) },
        )
    }
}

/** A full-width tappable card describing one role; the chevron signals it navigates forward. */
@Composable
private fun RoleCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Surface(Modifier.size(48.dp), shape = CircleShape, color = AppColors.Primary.copy(alpha = 0.14f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = AppColors.Primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AppColors.TextWeak)
        }
    }
}

// ---- prompter flow ----

@Composable
private fun PrompterReadyPanel(onAction: (RemoteUiAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            PanelHeader(
                icon = Icons.Default.Slideshow,
                title = stringResource(R.string.prompter_ready_title),
                description = stringResource(R.string.prompter_role_description),
            )
            PrimaryButton(stringResource(R.string.start_waiting), { onAction(RemoteUiAction.StartWaiting) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.QrCodeScanner, null)
            }
            QuietButton(stringResource(R.string.choose_role_again), { onAction(RemoteUiAction.ResetRole) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PrompterWaitingPanel(payload: RemotePairingPayload?, onAction: (RemoteUiAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(
                stringResource(R.string.prompter_waiting_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (payload != null) {
                val qrText = RemotePairingPayloadCodec.encode(payload)
                val bitmap = remember(qrText) { RemoteQrGenerator.encode(qrText) }
                // A white mat keeps the (black-on-white) QR scannable against the dark card.
                Surface(color = Color.White, shape = MaterialTheme.shapes.medium) {
                    Image(
                        bitmap.asImageBitmap(),
                        stringResource(R.string.pairing_qr_desc),
                        Modifier.padding(AppSpacing.md).size(220.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppColors.TextSecondary)
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(
                        stringResource(R.string.prompter_address_format, payload.host, payload.port),
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    stringResource(R.string.pairing_expires_hint),
                    color = AppColors.TextWeak,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                CircularProgressIndicator(Modifier.size(40.dp), color = AppColors.Primary)
                Text(stringResource(R.string.pairing_initializing), color = AppColors.TextSecondary)
            }
            SecondaryButton(stringResource(R.string.regenerate_qr), { onAction(RemoteUiAction.StartWaiting) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null)
            }
            QuietButton(stringResource(R.string.cancel), { onAction(RemoteUiAction.CancelWaiting) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PrompterConnectedPanel(state: RemoteUiState, onAction: (RemoteUiAction) -> Unit) {
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }
    val controllerName = (state.status as? RemoteConnectionStatus.Connected)?.device?.displayName

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            PanelHeader(
                icon = Icons.Default.CheckCircle,
                iconTint = AppColors.Success,
                title = stringResource(R.string.prompter_connected_title),
                description = controllerName?.let { stringResource(R.string.connected_controller, it) },
            )
            SecondaryButton(
                stringResource(R.string.disconnect_controller),
                { showDisconnectConfirm = true },
                Modifier.fillMaxWidth(),
            )
            QuietButton(
                stringResource(R.string.stop_hosting),
                { showStopConfirm = true },
                Modifier.fillMaxWidth(),
                color = AppColors.Danger,
            )
        }
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text(stringResource(R.string.disconnect_controller)) },
            text = { Text(stringResource(R.string.disconnect_controller_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    onAction(RemoteUiAction.DisconnectController)
                }) { Text(stringResource(R.string.disconnect_controller)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text(stringResource(R.string.stop_hosting)) },
            text = { Text(stringResource(R.string.stop_hosting_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    onAction(RemoteUiAction.StopHosting)
                }) { Text(stringResource(R.string.stop_hosting)) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

// ---- controller flow ----

/**
 * The controller "scan to connect" page. The scan action is the single primary CTA; manual
 * connect is a collapsed secondary card and role-switching a quiet tertiary action, so the
 * hierarchy stays obvious. Scan/permission errors surface as a contextual banner under the CTA.
 */
@Composable
private fun ControllerReadyPanel(
    scanError: RemoteScanError?,
    onScanRequested: () -> Unit,
    onScanErrorDismiss: () -> Unit,
    onAction: (RemoteUiAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                PanelHeader(
                    icon = Icons.Default.QrCodeScanner,
                    title = stringResource(R.string.controller_role_title),
                    description = stringResource(R.string.controller_role_description),
                )
                PrimaryButton(stringResource(R.string.scan_qr_code), onScanRequested, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.QrCodeScanner, null)
                }
                if (scanError != null) {
                    ScanErrorBanner(scanError, onScanErrorDismiss)
                }
            }
        }
        ManualConnectCard(onAction)
        QuietButton(
            stringResource(R.string.choose_role_again),
            { onAction(RemoteUiAction.ResetRole) },
            Modifier.fillMaxWidth(),
        )
    }
}

/** Contextual error banner shown under the scan button after a failed scan / denied permission. */
@Composable
private fun ScanErrorBanner(error: RemoteScanError, onDismiss: () -> Unit) {
    val messageRes = when (error) {
        RemoteScanError.InvalidPairing -> R.string.pairing_invalid
        RemoteScanError.ExpiredPairing -> R.string.pairing_expired
        RemoteScanError.CameraDenied -> R.string.camera_permission_denied
    }
    Surface(
        color = AppColors.Danger.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, AppColors.Danger.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(AppSpacing.md), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AppColors.Danger,
            )
            Text(
                stringResource(messageRes),
                modifier = Modifier.weight(1f),
                color = AppColors.Danger,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(18.dp), tint = AppColors.TextWeak)
            }
        }
    }
}

/** Collapsed-by-default manual-connect card; expands to reveal the host/port/token form. */
@Composable
private fun ManualConnectCard(onAction: (RemoteUiAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var manualError by remember { mutableStateOf<String?>(null) }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("8765") }
    var manualToken by remember { mutableStateOf("") }
    val manualInvalidText = stringResource(R.string.manual_invalid)

    AppCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .roundedClickable(shape = MaterialTheme.shapes.large) { expanded = !expanded }
                    .padding(AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null, tint = AppColors.TextSecondary)
                Text(
                    stringResource(R.string.manual_connect),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.TextWeak,
                )
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
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
                    manualError?.let { Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodyMedium) }
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
}

// ---- shared connection states ----

@Composable
private fun ConnectingPanel(onAction: (RemoteUiAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            CircularProgressIndicator(Modifier.size(44.dp), color = AppColors.Primary)
            Text(stringResource(R.string.connecting), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            QuietButton(stringResource(R.string.cancel), { onAction(RemoteUiAction.Disconnect) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FailedPanel(reason: RemoteFailureReason, lastCommandError: String?, onAction: (RemoteUiAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            PanelHeader(
                icon = Icons.Default.ErrorOutline,
                iconTint = AppColors.Danger,
                title = stringResource(R.string.connection_failed),
                description = lastCommandError ?: failureText(reason),
            )
            PrimaryButton(stringResource(R.string.retry), { onAction(RemoteUiAction.RetryConnection) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null)
            }
            QuietButton(stringResource(R.string.back), { onAction(RemoteUiAction.Disconnect) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ReconnectingPanel(onAction: (RemoteUiAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            PanelHeader(
                icon = Icons.Default.Sync,
                iconTint = AppColors.Warning,
                title = stringResource(R.string.remote_reconnecting),
                description = stringResource(R.string.reconnecting_hint),
            )
            SecondaryButton(stringResource(R.string.retry), { onAction(RemoteUiAction.RetryConnection) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null)
            }
            QuietButton(stringResource(R.string.disconnect), { onAction(RemoteUiAction.Disconnect) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConnectedWaitingPanel(state: RemoteUiState) {
    val prompterName = (state.status as? RemoteConnectionStatus.Connected)?.device?.displayName
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            PanelHeader(
                icon = Icons.Default.CheckCircle,
                iconTint = AppColors.Success,
                title = stringResource(R.string.controller_connected_title),
                description = prompterName?.let { stringResource(R.string.connected_device_name, it) },
            )
            Text(stringResource(R.string.waiting_for_setup), color = AppColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

// ---- controller playback surface (unchanged behavior) ----

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
private fun PlayingRemotePanel(
    state: RemoteUiState,
    snapshot: RemotePrompterSnapshot,
    onAction: (RemoteUiAction) -> Unit,
    expanded: Boolean,
    readingCursorUpdates: StateFlow<RemoteReadingCursor?>?,
) {
    if (expanded) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            NearbyTextCard(state, readingCursorUpdates, Modifier.weight(1.2f))
            RemoteControls(snapshot, onAction, Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            NearbyTextCard(state, readingCursorUpdates)
            RemoteControls(snapshot, onAction)
        }
    }
}

/**
 * The "当前朗读文本" card: a fixed-height [ControllerReadingViewport] driven by the reading
 * window + cursor, with the progress bar and time rows below. The card height never depends on
 * the reading text, so the lower rows stay at a stable Y.
 */
@Composable
private fun NearbyTextCard(
    state: RemoteUiState,
    readingCursorUpdates: StateFlow<RemoteReadingCursor?>?,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.nearby_text), color = AppColors.TextWeak, style = MaterialTheme.typography.labelMedium)
            val placeholder = stringResource(R.string.empty_nearby_text)
            ControllerReadingViewport(
                window = state.readingWindow,
                cursor = state.readingCursor,
                cursorUpdates = readingCursorUpdates,
                modifier = Modifier.fillMaxWidth(),
                placeholder = placeholder,
                semanticsTag = NearbyTextTestTag,
            )
            val progress = state.snapshot?.progress?.coerceIn(0f, 1f) ?: 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.Primary,
                trackColor = AppColors.Secondary,
            )
            // Progress + times: allow wrapping on narrow screens instead of forcing one row.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val progressText = stringResource(R.string.progress_percent, (progress * 100).toInt())
                val timeText = stringResource(
                    R.string.playback_elapsed_remaining,
                    formatDuration(((state.snapshot?.elapsedTimeMillis ?: 0L) / 1_000L).toInt()),
                    formatDuration(((state.snapshot?.remainingTimeMillis ?: 0L) / 1_000L).toInt()),
                )
                if (maxWidth >= 340.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(progressText, color = AppColors.TextSecondary)
                        Text(timeText, color = AppColors.TextSecondary)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        Text(progressText, color = AppColors.TextSecondary)
                        Text(timeText, color = AppColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteControls(snapshot: RemotePrompterSnapshot, onAction: (RemoteUiAction) -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            // Speed control: two equal square icon buttons + the centered speed value only.
            // The delta is NOT baked into the button text (that caused the stray "0").
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeedIconButton(
                    icon = { Icon(Icons.Default.Remove, null) },
                    contentDescription = stringResource(R.string.speed_decrease),
                    onClick = { onAction(RemoteUiAction.DecreaseSpeed) },
                    modifier = Modifier.weight(1f).semantics { testTag = SpeedDecreaseTestTag },
                )
                Text(
                    stringResource(R.string.speed_multiplier, snapshot.speedMultiplier),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                SpeedIconButton(
                    icon = { Icon(Icons.Default.Add, null) },
                    contentDescription = stringResource(R.string.speed_increase),
                    onClick = { onAction(RemoteUiAction.IncreaseSpeed) },
                    modifier = Modifier.weight(1f).semantics { testTag = SpeedIncreaseTestTag },
                )
            }
            // Seek buttons: equal width, full text.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                SecondaryButton(stringResource(R.string.seek_backward), { onAction(RemoteUiAction.SeekBackward) }, Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.seek_forward), { onAction(RemoteUiAction.SeekForward) }, Modifier.weight(1f))
            }
            PrimaryButton(stringResource(R.string.pause), { onAction(RemoteUiAction.Pause) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Pause, null) }
            // Compact danger zone: bordered "hold to end" with stable height, no huge blank.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .combinedClickable(onClick = {}, onLongClick = { onAction(RemoteUiAction.EndPlayback) }),
                color = Color.Transparent,
                border = BorderStroke(1.dp, AppColors.Danger.copy(alpha = .6f)),
                shape = MaterialTheme.shapes.medium,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.hold_to_end),
                        color = AppColors.Danger,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedIconButton(
    icon: @Composable () -> Unit,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(52.dp).clip(MaterialTheme.shapes.medium),
        color = AppColors.Secondary.copy(alpha = .45f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, AppColors.Border),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onClick = onClick, onLongClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        ) {
            icon()
        }
    }
}

@Composable
private fun PausedRemotePanel(
    state: RemoteUiState,
    snapshot: RemotePrompterSnapshot,
    onAction: (RemoteUiAction) -> Unit,
    readingCursorUpdates: StateFlow<RemoteReadingCursor?>?,
) {
    NearbyTextCard(state, readingCursorUpdates)
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(stringResource(R.string.paused), style = MaterialTheme.typography.headlineMedium)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 360.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        PrimaryButton(stringResource(R.string.resume_now), { onAction(RemoteUiAction.ResumeImmediately) }, Modifier.weight(1f))
                        SecondaryButton(stringResource(R.string.resume_countdown), { onAction(RemoteUiAction.ResumeWithCountdown) }, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        PrimaryButton(stringResource(R.string.resume_now), { onAction(RemoteUiAction.ResumeImmediately) }, Modifier.fillMaxWidth())
                        SecondaryButton(stringResource(R.string.resume_countdown), { onAction(RemoteUiAction.ResumeWithCountdown) }, Modifier.fillMaxWidth())
                    }
                }
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
