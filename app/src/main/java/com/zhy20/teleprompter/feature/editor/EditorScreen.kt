package com.zhy20.teleprompter.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.components.PrimaryButton
import com.zhy20.teleprompter.core.model.SaveState
import kotlinx.coroutines.delay

@Composable
fun EditorScreen(
    scriptId: String,
    appState: AppState,
    onBack: () -> Unit,
    onSetup: (String) -> Unit,
) {
    val script = appState.script(scriptId)
    var title by remember(scriptId) { mutableStateOf(script.title) }
    var body by remember(scriptId) { mutableStateOf(script.content.plainText()) }
    var previousBody by remember(scriptId) { mutableStateOf(body) }
    var boldActive by remember { mutableStateOf(false) }

    LaunchedEffect(title, body) {
        appState.saveState = SaveState.Saving
        delay(550)
        appState.updateScript(scriptId, title, body)
        appState.saveState = SaveState.Saved
    }

    Column(Modifier.fillMaxSize()) {
        EditorHeader(
            title = title,
            onTitleChange = { title = it },
            saveState = appState.saveState,
            boldActive = boldActive,
            onBold = { boldActive = !boldActive },
            onUndo = { val current = body; body = previousBody; previousBody = current },
            onBack = onBack,
            onSetup = { onSetup(scriptId) },
        )
        BasicTextField(
            value = body,
            onValueChange = { updated -> previousBody = body; body = updated },
            modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                color = AppColors.TextPrimary,
                fontWeight = if (boldActive) FontWeight.Bold else FontWeight.Normal,
            ),
            cursorBrush = SolidColor(AppColors.Primary),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize()) {
                    if (body.isEmpty()) Text(stringResource(R.string.script_body_hint), color = AppColors.TextWeak, style = MaterialTheme.typography.headlineMedium)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun EditorHeader(
    title: String,
    onTitleChange: (String) -> Unit,
    saveState: SaveState,
    boldActive: Boolean,
    onBold: () -> Unit,
    onUndo: () -> Unit,
    onBack: () -> Unit,
    onSetup: () -> Unit,
) {
    Surface(color = AppColors.Surface, border = BorderStroke(1.dp, AppColors.Border)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(AppSpacing.sm)) {
            val expanded = maxWidth >= 700.dp
            if (expanded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    TitleField(title, onTitleChange, Modifier.weight(1f))
                    EditorTools(saveState, boldActive, onBold, onUndo)
                    PrimaryButton(stringResource(R.string.enter_prompt_settings), onSetup) { Icon(Icons.Default.PlayArrow, null) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                        TitleField(title, onTitleChange, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        EditorTools(saveState, boldActive, onBold, onUndo)
                        Spacer(Modifier.weight(1f))
                        PrimaryButton(stringResource(R.string.start_prompting), onSetup)
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleField(value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp).padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm),
        singleLine = true,
        textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = MaterialTheme.typography.headlineMedium.fontSize, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(AppColors.Primary),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(stringResource(R.string.script_title_hint), color = AppColors.TextWeak, style = MaterialTheme.typography.headlineMedium)
                inner()
            }
        },
    )
}

@Composable
private fun EditorTools(saveState: SaveState, boldActive: Boolean, onBold: () -> Unit, onUndo: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onUndo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo)) }
        IconToggleButton(checked = boldActive, onCheckedChange = { onBold() }) {
            Icon(Icons.Default.FormatBold, stringResource(R.string.bold), tint = if (boldActive) AppColors.Primary else AppColors.TextSecondary)
        }
        val (icon, label, color) = when (saveState) {
            SaveState.Saving -> Triple(Icons.Default.Save, stringResource(R.string.saving), AppColors.TextWeak)
            SaveState.Saved -> Triple(Icons.Default.Check, stringResource(R.string.saved), AppColors.Success)
            SaveState.Error -> Triple(Icons.Default.Refresh, stringResource(R.string.save_failed), AppColors.Danger)
        }
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}
