package com.zhy20.teleprompter.feature.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.toAnnotatedString
import com.zhy20.teleprompter.core.model.TextSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PersistentEditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onSetup: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flush()
        }
    }
    val errorMessage = when (state.errorMessage) {
        EditorError.ScriptNotFound -> stringResource(R.string.script_not_found)
        EditorError.LoadFailed -> stringResource(R.string.load_failed)
        EditorError.SaveFailed -> stringResource(R.string.save_failed)
        null -> null
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) snackbarHostState.showSnackbar(message = errorMessage, withDismissAction = true)
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.errorMessage == EditorError.ScriptNotFound || state.errorMessage == EditorError.LoadFailed -> {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(if (state.errorMessage == EditorError.ScriptNotFound) R.string.script_not_found else R.string.load_failed))
                    androidx.compose.material3.TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                }
            }
            else -> PersistentEditorContent(
                state = state,
                onTitleChange = viewModel::updateTitle,
                onTextChange = viewModel::replaceText,
                onToggleStyle = viewModel::toggleStyle,
                onUndo = viewModel::undo,
                onRetry = viewModel::retrySave,
                onBack = { viewModel.flush { saved -> if (saved) onBack() } },
                onSetup = { viewModel.flush { saved -> if (saved) onSetup(state.scriptId) } },
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PersistentEditorContent(
    state: EditorUiState,
    onTitleChange: (String) -> Unit,
    onTextChange: (String, TextSelection) -> Unit,
    onToggleStyle: (com.zhy20.teleprompter.core.model.ScriptSpanStyle) -> Unit,
    onUndo: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSetup: () -> Unit,
) {
    val editorScrollState = rememberScrollState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val coroutineScope = rememberCoroutineScope()
    val textFieldValue = remember(state.editor.document, state.editor.selection) {
        TextFieldValue(
            annotatedString = state.editor.document.toAnnotatedString(),
            selection = TextRange(state.editor.selection.start, state.editor.selection.end),
        )
    }

    Column(Modifier.fillMaxSize()) {
        EditorHeader(
            title = state.title,
            onTitleChange = onTitleChange,
            saveState = state.saveState,
            savedAfterEdit = state.savedRevision > 0,
            editorState = state.editor,
            onToggleStyle = onToggleStyle,
            onUndo = onUndo,
            onRetrySave = onRetry,
            onBack = onBack,
            onSetup = onSetup,
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
                        onTextChange(updated.text, TextSelection(updated.selection.start, updated.selection.end))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = editorMinHeight).bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && imeBottom > 0) {
                                coroutineScope.launch {
                                    delay(80)
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Normal,
                    ),
                    cursorBrush = SolidColor(AppColors.Primary),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().heightIn(min = editorMinHeight)) {
                            if (state.editor.text.isEmpty()) {
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
