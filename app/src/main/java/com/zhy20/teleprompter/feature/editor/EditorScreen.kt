package com.zhy20.teleprompter.feature.editor

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.toAnnotatedString
import com.zhy20.teleprompter.core.design.components.PrimaryButton
import com.zhy20.teleprompter.core.model.RichTextDocument
import com.zhy20.teleprompter.core.model.RichTextEditorState
import com.zhy20.teleprompter.core.model.SaveState
import com.zhy20.teleprompter.core.model.SaveIconTone
import com.zhy20.teleprompter.core.model.ScriptSpanStyle
import com.zhy20.teleprompter.core.model.TextSelection
import com.zhy20.teleprompter.core.model.toSaveIconPresentation
import kotlinx.coroutines.delay

@Composable
fun EditorScreen(
    scriptId: String,
    appState: AppState,
    onBack: () -> Unit,
    onSetup: (String) -> Unit,
    previewSaveState: SaveState? = null,
) {
    val script = appState.script(scriptId)
    var title by remember(scriptId) { mutableStateOf(script.title) }
    var editorState by remember(scriptId) { mutableStateOf(appState.editorState(scriptId)) }
    var hasEdited by remember(scriptId) { mutableStateOf(false) }
    var savedAfterEdit by remember(scriptId) { mutableStateOf(false) }
    var saveRetry by remember(scriptId) { mutableStateOf(0) }
    var editorFocused by remember(scriptId) { mutableStateOf(false) }
    val editorScrollState = rememberScrollState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(scriptId, previewSaveState) {
        if (previewSaveState == null) appState.saveState = SaveState.Initial
    }
    LaunchedEffect(title, editorState.document, hasEdited, saveRetry) {
        if (previewSaveState != null) return@LaunchedEffect
        if (!hasEdited) return@LaunchedEffect
        appState.saveState = SaveState.Saving
        delay(420)
        appState.updateEditor(scriptId, title, editorState)
        appState.saveState = SaveState.Saved
        savedAfterEdit = true
    }
    LaunchedEffect(editorFocused, imeBottom, editorState.selection) {
        if (editorFocused && imeBottom > 0) {
            // Bringing the field into view is a no-op while its cursor is already visible, so
            // selection changes keep the cursor above the IME without forcing a scroll jump.
            delay(80)
            bringIntoViewRequester.bringIntoView()
        }
    }

    val textFieldValue = remember(editorState.document, editorState.selection) {
        TextFieldValue(
            annotatedString = editorState.document.toAnnotatedString(),
            selection = TextRange(editorState.selection.start, editorState.selection.end),
        )
    }

    Column(Modifier.fillMaxSize()) {
        EditorHeader(
            title = title,
            onTitleChange = { title = it; hasEdited = true },
            saveState = previewSaveState ?: appState.saveState,
            savedAfterEdit = previewSaveState == SaveState.Saved || savedAfterEdit,
            editorState = editorState,
            onToggleStyle = { style ->
                val updated = editorState.toggleStyle(style)
                if (updated != editorState) {
                    editorState = updated
                    hasEdited = true
                }
            },
            onUndo = {
                val updated = editorState.undo()
                if (updated != editorState) {
                    editorState = updated
                    hasEdited = true
                }
            },
            onRetrySave = { saveRetry += 1 },
            onBack = onBack,
            onSetup = { appState.updateEditor(scriptId, title, editorState); onSetup(scriptId) },
        )
        BoxWithConstraints(Modifier.fillMaxSize().weight(1f)) {
            val editorMinHeight = maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(editorScrollState).imePadding().navigationBarsPadding()
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl),
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { updated ->
                        val nextSelection = TextSelection(updated.selection.start, updated.selection.end)
                        editorState = if (updated.text == editorState.text) {
                            editorState.withSelection(nextSelection)
                        } else {
                            hasEdited = true
                            editorState.replaceText(updated.text, nextSelection)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = editorMinHeight).bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusChanged { editorFocused = it.isFocused },
                    // Inline spans use the shared mapper, while the editor base must stay normal
                    // so unformatted text never inherits the headline token's bold weight.
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Normal,
                    ),
                    cursorBrush = SolidColor(AppColors.Primary),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().heightIn(min = editorMinHeight)) {
                            if (editorState.text.isEmpty()) {
                                Text(
                                    stringResource(R.string.script_body_hint),
                                    color = AppColors.TextWeak,
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(AppSpacing.EditorTail))
            }
        }
    }
}

@Composable
internal fun EditorHeader(
    title: String,
    onTitleChange: (String) -> Unit,
    saveState: SaveState,
    savedAfterEdit: Boolean,
    editorState: RichTextEditorState,
    onToggleStyle: (ScriptSpanStyle) -> Unit,
    onUndo: () -> Unit,
    onRetrySave: () -> Unit,
    onBack: () -> Unit,
    onSetup: () -> Unit,
) {
    Surface(color = AppColors.Surface, border = BorderStroke(1.dp, AppColors.Border)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(AppSpacing.sm)) {
            val expanded = maxWidth >= 700.dp
            if (expanded) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    TitleField(title, onTitleChange, Modifier.weight(1f))
                    EditorTools(saveState, savedAfterEdit, editorState, onToggleStyle, onUndo, onRetrySave)
                    PrimaryButton(stringResource(R.string.enter_prompt_settings), onSetup) { Icon(Icons.Default.PlayArrow, null) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                        TitleField(title, onTitleChange, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        EditorTools(saveState, savedAfterEdit, editorState, onToggleStyle, onUndo, onRetrySave)
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
        textStyle = TextStyle(
            color = AppColors.TextPrimary,
            fontSize = MaterialTheme.typography.headlineMedium.fontSize,
            fontWeight = FontWeight.Bold,
        ),
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
private fun EditorTools(
    saveState: SaveState,
    savedAfterEdit: Boolean,
    editorState: RichTextEditorState,
    onToggleStyle: (ScriptSpanStyle) -> Unit,
    onUndo: () -> Unit,
    onRetrySave: () -> Unit,
) {
    val canFormat = !editorState.selection.isCollapsed
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUndo, enabled = editorState.canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo))
        }
        StyleToggle(ScriptSpanStyle.Bold, { Icon(Icons.Default.FormatBold, stringResource(R.string.bold)) }, editorState, canFormat, onToggleStyle)
        StyleToggle(ScriptSpanStyle.Italic, { Icon(Icons.Default.FormatItalic, stringResource(R.string.italic)) }, editorState, canFormat, onToggleStyle)
        StyleToggle(ScriptSpanStyle.Underline, { Icon(Icons.Default.FormatUnderlined, stringResource(R.string.underline)) }, editorState, canFormat, onToggleStyle)
        SaveStateIcon(saveState, savedAfterEdit, onRetrySave)
    }
}

@Composable
private fun SaveStateIcon(state: SaveState, savedAfterEdit: Boolean, onRetry: () -> Unit) {
    val presentation = state.toSaveIconPresentation(savedAfterEdit)
    val icon = when (state) {
        SaveState.Initial, SaveState.Saving -> Icons.Default.Save
        SaveState.Saved -> Icons.Default.Check
        SaveState.Error -> Icons.Default.Refresh
    }
    val description = when (state) {
        SaveState.Initial -> stringResource(R.string.save_status_initial)
        SaveState.Saving -> stringResource(R.string.save_status_saving)
        SaveState.Saved -> stringResource(R.string.save_status_saved)
        SaveState.Error -> stringResource(R.string.save_status_error)
    }
    val color = when (presentation.tone) {
        SaveIconTone.Neutral -> AppColors.TextPrimary
        SaveIconTone.Success -> AppColors.Success
        SaveIconTone.Error -> AppColors.Danger
    }
    if (presentation.retryEnabled) {
        IconButton(onClick = onRetry) { Icon(icon, description, tint = color) }
    } else {
        Icon(icon, description, tint = color)
    }
}

@Composable
private fun StyleToggle(
    style: ScriptSpanStyle,
    icon: @Composable () -> Unit,
    editorState: RichTextEditorState,
    enabled: Boolean,
    onToggleStyle: (ScriptSpanStyle) -> Unit,
) {
    val selected = RichTextDocument.isStyleFullyApplied(editorState.document, editorState.selection, style)
    IconToggleButton(checked = selected, onCheckedChange = { onToggleStyle(style) }, enabled = enabled) { icon() }
}
