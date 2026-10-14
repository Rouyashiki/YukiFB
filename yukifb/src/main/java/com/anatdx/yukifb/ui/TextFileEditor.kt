package com.anatdx.yukifb.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatdx.yukifb.R
import com.anatdx.yukifb.model.TextFileEncoding
import com.anatdx.yukifb.model.TextFileSaveRequest
import com.anatdx.yukifb.model.TextLineEnding
import com.anatdx.yukifb.state.TextFileEditorState
import com.anatdx.yukifb.state.countTextLines
import com.anatdx.yukifb.state.lineNumberText
import kotlin.jvm.JvmName

/**
 * Renders the 0.1.0 text editor API for source and binary compatibility.
 *
 * This overload keeps format controls hidden because its save callback cannot
 * communicate a selected encoding to the host.
 */
@Deprecated(
    message = "Use the format-aware overload whose onSave callback receives TextFileSaveRequest",
)
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
    TextFileEditor(
        fileName = fileName,
        state = state,
        onSave = { _: TextFileSaveRequest -> onSave(state.text) },
        onClose = onClose,
        modifier = modifier,
        isSaving = isSaving,
        readOnly = readOnly,
        showLineNumbers = showLineNumbers,
        availableEncodings = emptyList(),
        availableLineEndings = emptyList(),
    )
}

/**
 * Renders a lightweight plain-text file editor.
 *
 * YukiFB does not decode or persist the file. [onSave] receives the current text
 * with the selected line ending already applied plus the encoding selected by the
 * user. The host should call [TextFileEditorState.markSaved] after a successful write.
 *
 * [onEncodingChange] observes save-format changes. When [onReopenWithEncoding] is
 * supplied, the encoding menu also offers a reopen action; the host should decode
 * the original bytes again and call [TextFileEditorState.replaceDocument]. Hosts can
 * limit or hide format controls with [availableEncodings] and [availableLineEndings].
 *
 * Hosts should also consult [TextFileEditorState.hasUnsavedChanges] before handling
 * navigation that originates outside this surface, such as the system back action.
 */
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
@JvmName("TextFileEditorWithFormat")
fun TextFileEditor(
    fileName: String,
    state: TextFileEditorState,
    onSave: (TextFileSaveRequest) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    readOnly: Boolean = false,
    showLineNumbers: Boolean = true,
    availableEncodings: List<TextFileEncoding> = TextFileEncoding.entries,
    availableLineEndings: List<TextLineEnding> = TextLineEnding.entries,
    onEncodingChange: (TextFileEncoding) -> Unit = {},
    onReopenWithEncoding: ((TextFileEncoding) -> Unit)? = null,
) {
    TextFileEditor(
        fileName = fileName, state = state, onSave = onSave, onClose = onClose,
        confirmClose = true, modifier = modifier, isSaving = isSaving,
        readOnly = readOnly, showLineNumbers = showLineNumbers,
        availableEncodings = availableEncodings, availableLineEndings = availableLineEndings,
        onEncodingChange = onEncodingChange, onReopenWithEncoding = onReopenWithEncoding,
    )
}

/**
 * Editor with an explicit close policy. Set [confirmClose] to false only when
 * [onClose] routes both toolbar and system back through a host-owned dirty guard.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
@JvmName("TextFileEditorWithClosePolicy")
fun TextFileEditor(
    fileName: String,
    state: TextFileEditorState,
    onSave: (TextFileSaveRequest) -> Unit,
    onClose: () -> Unit,
    confirmClose: Boolean,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    readOnly: Boolean = false,
    showLineNumbers: Boolean = true,
    availableEncodings: List<TextFileEncoding> = TextFileEncoding.entries,
    availableLineEndings: List<TextLineEnding> = TextLineEnding.entries,
    onEncodingChange: (TextFileEncoding) -> Unit = {},
    onReopenWithEncoding: ((TextFileEncoding) -> Unit)? = null,
) {
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val currentText = state.text
    val dirty = state.hasUnsavedChanges

    fun requestClose() {
        if (confirmClose && dirty && !readOnly) {
            showDiscardConfirmation = true
        } else {
            onClose()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = yukiFBColors.background,
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
                onSave = { onSave(state.createSaveRequest()) },
            )
        },
        bottomBar = {
            TextEditorStatusBar(
                lineCount = countTextLines(currentText),
                characterCount = currentText.length,
                dirty = dirty,
                encoding = state.encoding,
                lineEnding = state.lineEnding,
                mixedLineEndings = state.sourceLineEndings.isMixed,
                availableEncodings = availableEncodings,
                availableLineEndings = availableLineEndings,
                saveFormatControlsEnabled = !isSaving && !readOnly,
                reopenEncodingEnabled = !isSaving,
                onEncodingChange = { encoding ->
                    state.selectEncoding(encoding)
                    onEncodingChange(encoding)
                },
                onReopenWithEncoding = onReopenWithEncoding,
                onLineEndingChange = state::selectLineEnding,
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
        BrowserAlertDialog(
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = yukiFBColors.topBar),
        title = {
            Text(
                text = if (dirty) "$fileName *" else fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !saving) {
                BrowserIcon(
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
                BrowserIcon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.yukifb_editor_undo),
                )
            }
            IconButton(
                enabled = canRedo && !saving && !readOnly,
                onClick = onRedo,
            ) {
                BrowserIcon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.yukifb_editor_redo),
                )
            }
            IconButton(
                enabled = dirty && !saving && !readOnly,
                onClick = onSave,
            ) {
                val effects = yukiFBEffectsSpec<Float>()
                AnimatedContent(
                    targetState = saving, label = "editor save state",
                    transitionSpec = { fadeIn(effects).togetherWith(fadeOut(effects)) },
                ) { inProgress ->
                if (inProgress) {
                    BrowserLoadingIndicator(modifier = Modifier.size(24.dp))
                } else {
                    BrowserIcon(
                        if (dirty) Icons.Default.Save else Icons.Default.Check,
                        contentDescription = stringResource(
                            R.string.yukifb_editor_save_file,
                        ),
                    )
                }
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
        color = yukiFBColors.editor,
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
    encoding: TextFileEncoding,
    lineEnding: TextLineEnding,
    mixedLineEndings: Boolean,
    availableEncodings: List<TextFileEncoding>,
    availableLineEndings: List<TextLineEnding>,
    saveFormatControlsEnabled: Boolean,
    reopenEncodingEnabled: Boolean,
    onEncodingChange: (TextFileEncoding) -> Unit,
    onReopenWithEncoding: ((TextFileEncoding) -> Unit)?,
    onLineEndingChange: (TextLineEnding) -> Unit,
) {
    val encodingOptions = remember(encoding, availableEncodings) {
        (listOf(encoding) + availableEncodings).distinct()
    }
    val lineEndingOptions = remember(lineEnding, availableLineEndings) {
        (listOf(lineEnding) + availableLineEndings).distinct()
    }

    Surface(
        tonalElevation = 2.dp,
        color = yukiFBColors.bottomBar,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.yukifb_editor_status,
                    pluralStringResource(
                        R.plurals.yukifb_editor_line_count,
                        lineCount,
                        lineCount,
                    ),
                    pluralStringResource(
                        R.plurals.yukifb_editor_character_count,
                        characterCount,
                        characterCount,
                    ),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(12.dp))
            if (availableEncodings.isNotEmpty()) {
                EditorEncodingMenu(
                    selected = encoding,
                    contentDescription = stringResource(
                        R.string.yukifb_editor_encoding_value,
                        encoding.displayName,
                    ),
                    options = encodingOptions,
                    saveEnabled = saveFormatControlsEnabled,
                    reopenEnabled = reopenEncodingEnabled,
                    hasUnsavedChanges = dirty,
                    onSelectForSave = onEncodingChange,
                    onReopen = onReopenWithEncoding,
                )
            }
            if (availableLineEndings.isNotEmpty()) {
                val lineEndingLabel = if (mixedLineEndings) {
                    stringResource(
                        R.string.yukifb_editor_mixed_line_endings,
                        lineEnding.name,
                    )
                } else {
                    lineEnding.name
                }
                EditorFormatMenu(
                    selected = lineEnding,
                    selectedLabel = lineEndingLabel,
                    contentDescription = stringResource(
                        R.string.yukifb_editor_line_ending_value,
                        lineEndingLabel,
                    ),
                    options = lineEndingOptions,
                    optionLabel = TextLineEnding::name,
                    enabled = saveFormatControlsEnabled,
                    onSelect = onLineEndingChange,
                )
            }
            if (dirty) {
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = stringResource(R.string.yukifb_editor_modified),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun EditorEncodingMenu(
    selected: TextFileEncoding,
    contentDescription: String,
    options: List<TextFileEncoding>,
    saveEnabled: Boolean,
    reopenEnabled: Boolean,
    hasUnsavedChanges: Boolean,
    onSelectForSave: (TextFileEncoding) -> Unit,
    onReopen: ((TextFileEncoding) -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    var showReopenDialog by remember { mutableStateOf(false) }
    var reopenEncoding by remember(selected) { mutableStateOf(selected) }
    val canReopen = reopenEnabled && onReopen != null

    Box {
        TextButton(
            modifier = Modifier.semantics {
                this.contentDescription = contentDescription
            },
            enabled = (saveEnabled || canReopen) && options.isNotEmpty(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            onClick = { expanded = true },
        ) {
            Text(
                text = selected.displayName,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    leadingIcon = if (option == selected) {
                        {
                            BrowserIcon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                    enabled = saveEnabled,
                    onClick = {
                        expanded = false
                        onSelectForSave(option)
                    },
                )
            }
            if (onReopen != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.yukifb_editor_reopen_encoding)) },
                    enabled = canReopen,
                    onClick = {
                        expanded = false
                        reopenEncoding = selected
                        showReopenDialog = true
                    },
                )
            }
        }
    }

    if (showReopenDialog && onReopen != null) {
        BrowserAlertDialog(
            onDismissRequest = { showReopenDialog = false },
            title = { Text(stringResource(R.string.yukifb_editor_reopen_encoding_title)) },
            text = {
                Column {
                    if (hasUnsavedChanges) {
                        Text(
                            modifier = Modifier.padding(bottom = 8.dp),
                            text = stringResource(
                                R.string.yukifb_editor_reopen_encoding_warning,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reopenEncoding = option }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = reopenEncoding == option,
                                onClick = { reopenEncoding = option },
                            )
                            Text(option.displayName)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenDialog = false }) {
                    Text(stringResource(R.string.yukifb_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReopenDialog = false
                        onReopen(reopenEncoding)
                    },
                ) {
                    Text(stringResource(R.string.yukifb_editor_reopen))
                }
            },
        )
    }
}

@Composable
private fun <T> EditorFormatMenu(
    selected: T,
    selectedLabel: String,
    contentDescription: String,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(
            modifier = Modifier.semantics {
                this.contentDescription = contentDescription
            },
            enabled = enabled && options.isNotEmpty(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            onClick = { expanded = true },
        ) {
            Text(
                text = selectedLabel,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = if (option == selected) {
                        {
                            BrowserIcon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
