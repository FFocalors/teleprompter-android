package com.zhy20.teleprompter.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppElevation
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.model.RemoteConnectionState

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val clickableModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Card(
        modifier = clickableModifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.Card),
    ) { content() }
}

@Composable
fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    content: @Composable () -> Unit,
) {
    AppCard(modifier) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (supportingText != null) {
                Text(supportingText, color = AppColors.TextWeak, style = MaterialTheme.typography.bodyMedium)
            }
            content()
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Primary,
            contentColor = AppColors.TextPrimary,
            disabledContainerColor = AppColors.Secondary,
            disabledContentColor = AppColors.TextWeak,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = AppSpacing.lg),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(AppSpacing.xs))
        }
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, AppColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextPrimary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = AppSpacing.lg),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(AppSpacing.xs))
        }
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
fun ChoiceRow(
    choices: List<Pair<String, Boolean>>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        choices.forEachIndexed { index, (label, selected) ->
            Surface(
                modifier = Modifier.weight(1f).height(48.dp).clickable { onSelected(index) },
                color = if (selected) AppColors.Secondary.copy(alpha = 0.55f) else Color.Transparent,
                contentColor = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AppColors.Primary else AppColors.Border),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusLabel(state: RemoteConnectionState) {
    val (label, color) = when (state) {
        RemoteConnectionState.Disconnected -> stringResource(R.string.disconnected) to AppColors.TextWeak
        RemoteConnectionState.Waiting -> stringResource(R.string.waiting_connection) to AppColors.Warning
        RemoteConnectionState.Connected -> stringResource(R.string.connected) to AppColors.Success
        RemoteConnectionState.ConnectionLost -> stringResource(R.string.connection_lost) to AppColors.Danger
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(7.dp), shape = CircleShape, color = color) {}
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun RemoteStatusCard(
    state: RemoteConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 230.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, null, tint = AppColors.Primary)
                            Spacer(Modifier.width(AppSpacing.xs))
                            Text(stringResource(R.string.remote_controller), fontWeight = FontWeight.Bold, maxLines = 2)
                        }
                        ConnectionStatusLabel(state)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = AppColors.Primary)
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text(stringResource(R.string.remote_controller), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        ConnectionStatusLabel(state)
                    }
                }
            }
            Text(
                if (state == RemoteConnectionState.Connected) stringResource(R.string.device_connected) else stringResource(R.string.connect_remote),
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
