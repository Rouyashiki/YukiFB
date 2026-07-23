package com.anatdx.yukifb.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatdx.yukifb.R
import com.anatdx.yukifb.state.TextFileEditorState
import com.anatdx.yukifb.state.countTextLines
import com.anatdx.yukifb.state.lineNumberText

/**
 * Renders a lightweight plain-text file editor.
 *
 * YukiFB does not decode or persist the file. [onSave] receives the current text;
 * the host should call [TextFileEditorState.markSaved] after a successful write.
 * Hosts should also consult [TextFileEditorState.hasUnsavedChanges] before handling
 * navigation that originates outside this surface, such as the system back action.
 */
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
fun TextFileEditor(
    fileName: String,
    state: TextFileEditorState,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    readOnly: Boolean = false,
    showLineNumbers: Boolean = true,
) {
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val currentText = state.text
    val dirty = state.hasUnsavedChanges

    fun requestClose() {
        if (dirty && !readOnly) {
            showDiscardConfirmation = true
        } else {
            onClose()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TextEditorTopBar(
                fileName = fileName,
                dirty = dirty,
                saving = isSaving,
                readOnly = readOnly,
                canUndo = state.textFieldState.undoState.canUndo,
                canRedo = state.textFieldState.undoState.canRedo,
                onClose = ::requestClose,
                onUndo = { state.textFieldState.undoState.undo() },
                onRedo = { state.textFieldState.undoState.redo() },
                onSave = { onSave(currentText) },
            )
        },
        bottomBar = {
            TextEditorStatusBar(
                lineCount = countTextLines(currentText),
                characterCount = currentText.length,
                dirty = dirty,
            )
        },
    ) { contentPadding ->
        TextEditorBody(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            state = state,
            enabled = !isSaving,
            readOnly = readOnly,
            showLineNumbers = showLineNumbers,
        )
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text(stringResource(R.string.yukifb_editor_discard_title)) },
            text = { Text(stringResource(R.string.yukifb_editor_discard_message)) },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text(stringResource(R.string.yukifb_editor_continue_editing))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        onClose()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.yukifb_editor_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditorTopBar(
    fileName: String,
    dirty: Boolean,
    saving: Boolean,
    readOnly: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = if (dirty) "$fileName *" else fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.yukifb_close),
                )
            }
        },
        actions = {
            IconButton(
                enabled = canUndo && !saving && !readOnly,
                onClick = onUndo,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.yukifb_editor_undo),
                )
            }
            IconButton(
                enabled = canRedo && !saving && !readOnly,
                onClick = onRedo,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.yukifb_editor_redo),
                )
            }
            IconButton(
                enabled = dirty && !saving && !readOnly,
                onClick = onSave,
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = stringResource(
                            R.string.yukifb_editor_save_file,
                        ),
                    )
                }
            }
        },
    )
}

@Composable
private fun TextEditorBody(
    state: TextFileEditorState,
    enabled: Boolean,
    readOnly: Boolean,
    showLineNumbers: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentText = state.text
    val lineCount = remember(currentText) { countTextLines(currentText) }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutterWidth = if (showLineNumbers) 52.dp else 0.dp
            val minimumTextWidth = if (maxWidth > gutterWidth) {
                maxWidth - gutterWidth
            } else {
                maxWidth
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
                    .padding(vertical = 12.dp),
            ) {
                if (showLineNumbers) {
                    Text(
                        modifier = Modifier
                            .width(gutterWidth)
                            .padding(horizontal = 10.dp),
                        text = lineNumberText(lineCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.End,
                    )
                    VerticalDivider()
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll),
                ) {
                    BasicTextField(
                        state = state.textFieldState,
                        modifier = Modifier
                            .widthIn(min = minimumTextWidth)
                            .padding(horizontal = 12.dp),
                        enabled = enabled,
                        readOnly = readOnly,
                        textStyle = textStyle,
                        lineLimits = TextFieldLineLimits.MultiLine(),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextEditorStatusBar(
    lineCount: Int,
    characterCount: Int,
    dirty: Boolean,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.yukifb_editor_status,
                    lineCount,
                    characterCount,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.weight(1f))
            if (dirty) {
                Text(
                    text = stringResource(R.string.yukifb_editor_modified),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
