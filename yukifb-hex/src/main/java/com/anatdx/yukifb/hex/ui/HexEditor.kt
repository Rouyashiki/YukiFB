package com.anatdx.yukifb.hex.ui

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.West
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anatdx.yukifb.hex.R
import com.anatdx.yukifb.hex.backend.BinaryBytes
import com.anatdx.yukifb.hex.backend.BinaryTextCodec
import com.anatdx.yukifb.hex.state.*
import com.anatdx.yukifb.ui.YukiFBBackHandler
import com.anatdx.yukifb.ui.YukiFBIcon
import com.anatdx.yukifb.ui.YukiFBStyle
import com.anatdx.yukifb.ui.YukiFBMotion
import com.anatdx.yukifb.ui.YukiFBTheme
import com.anatdx.yukifb.ui.rememberYukiFBBackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Binds a host-owned controller to the editor. The host must close the controller on disposal.
 * Set [confirmClose] to false when the host handles dirty-document confirmation and system back.
 */
@Composable
fun HexEditor(
    fileName: String,
    controller: HexEditorController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    confirmClose: Boolean = true,
) {
    val state by controller.state.collectAsState()
    var viewport by remember(controller) { mutableStateOf(0L to 512) }
    var window by remember(controller) { mutableStateOf(HexEditorWindow()) }
    var inspected by remember(controller) { mutableStateOf(BinaryBytes.Empty) }
    var failure by remember(controller) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(controller, viewport, state.generation, state.codecId, retry) {
        try {
            val loaded = controller.window(viewport.first.coerceAtMost(state.size), viewport.second)
            if (loaded.generation == controller.state.value.generation) window = loaded
            failure = null
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { failure = error.message ?: error.javaClass.simpleName }
    }
    LaunchedEffect(controller, state.selection, state.generation, retry) {
        try {
            val count = minOf(8L, if (state.selection.length == 0L) state.size - state.selection.caret else state.selection.length).toInt()
            inspected = controller.read(state.selection.start, count)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { inspected = BinaryBytes.Empty; failure = error.message ?: error.javaClass.simpleName }
    }
    HexEditor(fileName, state, window, inspected, controller.codecs, controller::dispatch,
        { offset, length -> viewport = offset to length }, controller::read, onClose, modifier,
        confirmClose, controller.options.maxInputBytes, failure, { retry++ })
}

/**
 * Renders hoisted binary editor state. Reading and editing stay in the supplied callbacks.
 * [onViewport] requests a bounded byte window; [readBytes] supports bounded clipboard export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexEditor(
    fileName: String,
    state: HexEditorUiState,
    window: HexEditorWindow,
    inspectionBytes: BinaryBytes,
    codecs: List<BinaryTextCodec>,
    onAction: (HexEditorAction) -> Unit,
    onViewport: (Long, Int) -> Unit,
    readBytes: suspend (Long, Int) -> BinaryBytes,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    confirmClose: Boolean = true,
    maxClipboardBytes: Int = 65536,
    readFailure: String? = null,
    onRetryRead: () -> Unit = {},
) {
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var menu by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf(false) }
    var inspector by remember { mutableStateOf(false) }
    var littleEndian by remember { mutableStateOf(true) }
    var encodingDialog by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var initialInput by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<HexEditorAction.Confirmed?>(null) }
    var fillTarget by remember { mutableStateOf<HexEditTarget?>(null) }
    var closeDialog by remember { mutableStateOf(false) }
    var closeAfterSave by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var columns by remember { mutableIntStateOf(8) }
    val codec = codecs.first { it.id == state.codecId }
    val colors = YukiFBTheme.colors
    val edited = !state.readOnly && !state.busy
    val selected = state.selection.length > 0

    fun editTarget() = HexEditTarget(state.selection, state.generation)
    fun confirm(action: HexEditorAction) { pending = HexEditorAction.Confirmed(action, editTarget()) }

    val requestClose = {
        if (!state.busy) {
            if (confirmClose && state.dirty) closeDialog = true else onClose()
        }
    }
    fun export(text: Boolean) {
        scope.launch {
            try {
                if (state.selection.length > maxClipboardBytes) {
                    localError = resources.getQuantityString(R.plurals.yukifb_hex_clipboard_limit, maxClipboardBytes, maxClipboardBytes)
                } else {
                    val bytes = readBytes(state.selection.start, state.selection.length.toInt())
                    val value = if (text) codec.decodeText(bytes) else formatHexBytes(bytes)
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(fileName, value)))
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { localError = resources.getString(R.string.yukifb_hex_error, error.message.orEmpty()) }
        }
    }
    LaunchedEffect(state.saving, state.dirty, state.error, closeAfterSave) {
        if (closeAfterSave && !state.saving && !state.dirty) { closeAfterSave = false; onClose() }
        else if (state.error != null) closeAfterSave = false
    }
    if (confirmClose) {
        YukiFBBackHandler(rememberYukiFBBackState(), "hex:${state.dirty}:${state.busy}", previewEnabled = !state.dirty && !state.busy, onBack = requestClose)
    }

    Scaffold(
        modifier = modifier.onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val action = when {
                event.isCtrlPressed && event.key == Key.Z -> if (event.isShiftPressed) HexEditorAction.Redo else HexEditorAction.Undo
                event.isCtrlPressed && event.key == Key.Y -> HexEditorAction.Redo
                event.isCtrlPressed && event.key == Key.S -> HexEditorAction.Save
                event.isCtrlPressed && event.key == Key.C -> HexEditorAction.Copy
                event.isCtrlPressed && event.key == Key.V -> HexEditorAction.Paste
                event.key == Key.DirectionLeft -> HexEditorAction.Move(-1, event.isShiftPressed)
                event.key == Key.DirectionRight -> HexEditorAction.Move(1, event.isShiftPressed)
                event.key == Key.DirectionUp -> HexEditorAction.Move(-columns.toLong(), event.isShiftPressed)
                event.key == Key.DirectionDown -> HexEditorAction.Move(columns.toLong(), event.isShiftPressed)
                else -> null
            }
            if (action != null) {
                val navigation = action is HexEditorAction.Move || action == HexEditorAction.Copy
                if (navigation || edited) onAction(action)
                true
            } else if (!event.isCtrlPressed && edited) {
                val digit = event.nativeKeyEvent.unicodeChar.toChar().digitToIntOrNull(16)
                if (digit != null) { onAction(HexEditorAction.Digit(digit)); true } else false
            } else false
        },
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBar),
                title = {
                    Column {
                        Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (state.readOnly) R.string.yukifb_hex_read_only else if (state.dirty) R.string.yukifb_hex_modified else R.string.yukifb_hex_title), style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { IconButton(onClick = requestClose, enabled = !state.busy) { YukiFBIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.yukifb_hex_back)) } },
                actions = {
                    if (!state.readOnly) {
                        IconButton(onClick = { onAction(HexEditorAction.Undo) }, enabled = edited && state.canUndo) { YukiFBIcon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.yukifb_hex_undo)) }
                        IconButton(onClick = { onAction(HexEditorAction.Redo) }, enabled = edited && state.canRedo) { YukiFBIcon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.yukifb_hex_redo)) }
                        IconButton(onClick = { onAction(HexEditorAction.Save) }, enabled = edited && state.dirty) { YukiFBIcon(Icons.Default.Save, stringResource(R.string.yukifb_hex_save)) }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { YukiFBIcon(Icons.Default.MoreVert, stringResource(R.string.yukifb_hex_more)) }
                        DropdownMenu(menu, { menu = false }) {
                            @Composable fun item(label: Int, enabled: Boolean = true, action: () -> Unit) {
                                DropdownMenuItem(text = { Text(stringResource(label)) }, enabled = enabled, onClick = { menu = false; action() })
                            }
                            item(R.string.yukifb_hex_search) { search = !search }
                            item(R.string.yukifb_hex_goto) { dialog = "goto"; initialInput = "" }
                            item(R.string.yukifb_hex_encoding) { encodingDialog = true }
                            item(R.string.yukifb_hex_inspector) { inspector = !inspector }
                            item(R.string.yukifb_hex_select_all, !state.busy) { onAction(HexEditorAction.Select(BinarySelection(0, state.size))) }
                            item(R.string.yukifb_hex_copy, selected && !state.busy) { onAction(HexEditorAction.Copy) }
                            item(R.string.yukifb_hex_copy_hex, selected) { export(false) }
                            item(R.string.yukifb_hex_copy_text, selected) { export(true) }
                            if (!state.readOnly) {
                                item(R.string.yukifb_hex_cut, selected && edited) { confirm(HexEditorAction.Cut) }
                                item(R.string.yukifb_hex_delete, selected && edited) { confirm(HexEditorAction.Delete) }
                                item(R.string.yukifb_hex_fill, selected && edited) { fillTarget = editTarget(); dialog = "fill"; initialInput = "00" }
                                item(R.string.yukifb_hex_paste, state.clipboardSize > 0 && edited) { onAction(HexEditorAction.Paste) }
                                item(R.string.yukifb_hex_system_paste, edited) {
                                    scope.launch {
                                        val text = clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString().orEmpty()
                                        if (text.length > maxClipboardBytes * 3) localError = resources.getQuantityString(R.plurals.yukifb_hex_clipboard_limit, maxClipboardBytes, maxClipboardBytes)
                                        else { initialInput = text; dialog = "input" }
                                    }
                                }
                                item(R.string.yukifb_hex_input, edited) { initialInput = ""; dialog = "input" }
                                item(if (keypad) R.string.yukifb_hex_hide_keyboard else R.string.yukifb_hex_keyboard) { keypad = !keypad }
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = colors.bottomBar) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    HexPanel(!state.readOnly && keypad) {
                        HexKeypad(state, columns, onAction, edited, { keypad = false })
                    }
                    val value = if (inspectionBytes.size > 0) (inspectionBytes[0].toInt() and 255).toString(16).uppercase().padStart(2, '0') else "—"
                    Text(stringResource(R.string.yukifb_hex_status, state.selection.start.toString(16).uppercase(), state.size.toString(16).uppercase(), state.selection.length.toString(), value),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy || state.searching) {
                val progress = state.progress
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            val error = localError ?: (state.error ?: readFailure)?.let { stringResource(R.string.yukifb_hex_error, it) }
            if (error != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { localError = null; onAction(HexEditorAction.DismissError); onRetryRead() }) { Text(stringResource(R.string.yukifb_hex_retry)) }
                }
            }
            HexPanel(search) { HexSearchPanel(state, codec, maxClipboardBytes, onAction, ::confirm) }
            HexPanel(inspector) {
                Column(Modifier.fillMaxWidth().heightIn(max = 208.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    Row {
                        FilterChip(selected = littleEndian, onClick = { littleEndian = true }, label = { Text(stringResource(R.string.yukifb_hex_little_endian)) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = !littleEndian, onClick = { littleEndian = false }, label = { Text(stringResource(R.string.yukifb_hex_big_endian)) })
                    }
                    inspectBinaryValues(inspectionBytes, littleEndian).chunked(2).forEach { values ->
                        Row(Modifier.fillMaxWidth()) {
                            values.forEach { value -> Text("${value.type}: ${value.value ?: stringResource(R.string.yukifb_hex_incomplete)}", Modifier.weight(1f).padding(vertical = 3.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                        }
                    }
                }
            }
            HexGrid(state, window, { action -> focus.requestFocus(); onAction(action) }, onViewport, { columns = it },
                Modifier.weight(1f).fillMaxWidth().focusRequester(focus).focusable())
        }
    }

    if (dialog != null) {
        HexInputDialog(checkNotNull(dialog), initialInput, state.size, codec, maxClipboardBytes,
            onDismiss = { dialog = null }, onOffset = { onAction(HexEditorAction.Select(BinarySelection(it))) },
            onInput = { onAction(HexEditorAction.Input(it)) }, onFill = { value ->
                fillTarget?.let { pending = HexEditorAction.Confirmed(HexEditorAction.Fill(value), it) }
            })
    }
    if (encodingDialog) AlertDialog(
        onDismissRequest = { encodingDialog = false }, title = { Text(stringResource(R.string.yukifb_hex_encoding)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            codecs.forEach { encoding -> TextButton(onClick = { onAction(HexEditorAction.Codec(encoding.id)); encodingDialog = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                RadioButton(selected = encoding.id == state.codecId, onClick = null)
                Text(encoding.label, Modifier.padding(start = 8.dp))
            } }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = { encodingDialog = false }) { Text(stringResource(R.string.yukifb_hex_cancel)) } },
    )
    if (pending != null) AlertDialog(
        onDismissRequest = { pending = null }, title = { Text(stringResource(R.string.yukifb_hex_confirm_edit)) },
        text = { Text(if (pending?.action is HexEditorAction.ReplaceAll) stringResource(R.string.yukifb_hex_confirm_all) else stringResource(R.string.yukifb_hex_confirm_selection, pending?.target?.selection?.length.toString())) },
        confirmButton = { TextButton(onClick = { pending?.let(onAction); pending = null }, enabled = edited) { Text(stringResource(R.string.yukifb_hex_apply)) } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.yukifb_hex_cancel)) } },
    )
    if (closeDialog) AlertDialog(
        onDismissRequest = { closeDialog = false }, title = { Text(stringResource(R.string.yukifb_hex_unsaved)) },
        text = { Text(stringResource(R.string.yukifb_hex_unsaved_message)) },
        confirmButton = { TextButton(onClick = { closeDialog = false; closeAfterSave = true; onAction(HexEditorAction.Save) }) { Text(stringResource(R.string.yukifb_hex_save)) } },
        dismissButton = { Row {
            TextButton(onClick = { closeDialog = false; onClose() }) { Text(stringResource(R.string.yukifb_hex_discard)) }
            TextButton(onClick = { closeDialog = false }) { Text(stringResource(R.string.yukifb_hex_cancel)) }
        } },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HexPanel(visible: Boolean, content: @Composable () -> Unit) {
    val motion = YukiFBTheme.motion
    val effects: FiniteAnimationSpec<Float> = when (motion) {
        YukiFBMotion.None -> snap()
        YukiFBMotion.Reduced -> tween(120)
        YukiFBMotion.Full -> MaterialTheme.motionScheme.fastEffectsSpec()
    }
    val enter = fadeIn(effects)
    val exit = fadeOut(effects)
    AnimatedVisibility(visible,
        enter = if (motion == YukiFBMotion.Full) enter + expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) else enter,
        exit = if (motion == YukiFBMotion.Full) exit + shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) else exit,
    ) { content() }
}

@Composable
private fun HexKeypad(state: HexEditorUiState, columns: Int, onAction: (HexEditorAction) -> Unit, enabled: Boolean, onHide: () -> Unit) {
    val expressive = YukiFBTheme.style == YukiFBStyle.Expressive
    Column {
        FlowRow(
            Modifier.fillMaxWidth().testTag("yukifb_hex_keypad_toolbar"),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                val icons = listOf(Icons.Default.ArrowUpward, Icons.Default.West, Icons.Default.East, Icons.Default.ArrowDownward)
                listOf(-columns.toLong() to R.string.yukifb_hex_previous_row, -1L to R.string.yukifb_hex_previous_byte, 1L to R.string.yukifb_hex_next_byte, columns.toLong() to R.string.yukifb_hex_next_row).forEachIndexed { index, (delta, label) ->
                    IconButton(onClick = { onAction(HexEditorAction.Move(delta)) }, modifier = Modifier.size(48.dp)) {
                        YukiFBIcon(icons[index], stringResource(label))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onAction(HexEditorAction.Mode(if (state.mode == HexInputMode.Insert) HexInputMode.Overwrite else HexInputMode.Insert)) },
                    modifier = Modifier.heightIn(min = 48.dp), enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text(stringResource(if (state.mode == HexInputMode.Insert) R.string.yukifb_hex_insert else R.string.yukifb_hex_overwrite)) }
                IconButton(onClick = onHide, modifier = Modifier.size(48.dp)) {
                    YukiFBIcon(Icons.Default.KeyboardHide, stringResource(R.string.yukifb_hex_hide_keyboard))
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            val keyColumns = if (maxWidth >= 48.dp * 8) 8 else 4
            Column {
                (0..15).chunked(keyColumns).forEach { keys -> Row(Modifier.fillMaxWidth()) {
                    keys.forEach { value ->
                        val keyModifier = Modifier.weight(1f).heightIn(min = 48.dp).padding(2.dp)
                        val inputEnabled = enabled && (state.mode == HexInputMode.Insert || state.selection.start < state.size)
                        if (expressive) FilledTonalButton(onClick = { onAction(HexEditorAction.Digit(value)) }, modifier = keyModifier, enabled = inputEnabled, contentPadding = PaddingValues(0.dp)) { Text(value.toString(16).uppercase()) }
                        else TextButton(onClick = { onAction(HexEditorAction.Digit(value)) }, modifier = keyModifier, enabled = inputEnabled, contentPadding = PaddingValues(0.dp)) { Text(value.toString(16).uppercase()) }
                    }
                } }
            }
        }
    }
}

@Composable
private fun HexInputDialog(kind: String, initial: String, size: Long, codec: BinaryTextCodec, maxBytes: Int, onDismiss: () -> Unit, onOffset: (Long) -> Unit, onInput: (BinaryBytes) -> Unit, onFill: (Int) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var hex by remember { mutableStateOf(true) }
    var invalid by remember { mutableStateOf(false) }
    val title = when (kind) { "goto" -> R.string.yukifb_hex_goto; "fill" -> R.string.yukifb_hex_fill; else -> R.string.yukifb_hex_input }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title)) }, text = { Column {
        if (kind != "fill") Row {
            FilterChip(selected = hex, onClick = { hex = true }, label = { Text(stringResource(R.string.yukifb_hex_hex)) })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = !hex, onClick = { hex = false }, label = { Text(stringResource(if (kind == "goto") R.string.yukifb_hex_decimal else R.string.yukifb_hex_text)) })
        }
        OutlinedTextField(value, onValueChange = { if (it.length <= maxBytes * 3) value = it; invalid = false }, isError = invalid,
            label = { Text(stringResource(if (kind == "fill") R.string.yukifb_hex_byte_value else if (kind == "goto") R.string.yukifb_hex_offset else R.string.yukifb_hex_input)) },
            supportingText = { if (invalid) Text(stringResource(R.string.yukifb_hex_invalid_input)) })
    } }, confirmButton = { TextButton(onClick = {
        try {
            when (kind) {
                "goto" -> onOffset(parseHexEditorOffset(value, hex, size))
                "fill" -> { val bytes = parseHexBytes(value, 1); require(bytes.size == 1); onFill(bytes[0].toInt() and 255) }
                else -> { val bytes = if (hex) parseHexBytes(value, maxBytes) else codec.encode(value); require(bytes.size <= maxBytes); onInput(bytes) }
            }
            onDismiss()
        } catch (_: IllegalArgumentException) { invalid = true }
    }) { Text(stringResource(R.string.yukifb_hex_apply)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.yukifb_hex_cancel)) } })
}

@Composable
private fun HexSearchPanel(state: HexEditorUiState, codec: BinaryTextCodec, maxBytes: Int, onAction: (HexEditorAction) -> Unit, onConfirm: (HexEditorAction) -> Unit) {
    var pattern by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var hex by remember { mutableStateOf(true) }
    var invalid by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun bytes(text: String): BinaryBytes = (if (hex) parseHexBytes(text, maxBytes) else codec.encode(text)).also { require(it.size <= maxBytes) }
    fun action(backwards: Boolean = false, replace: Boolean = false, all: Boolean = false) {
        try {
            val needle = bytes(pattern).also { require(it.size > 0) }
            val intent = when {
                all -> HexEditorAction.ReplaceAll(needle, bytes(replacement))
                replace -> HexEditorAction.ReplaceMatch(needle, bytes(replacement))
                else -> HexEditorAction.Find(needle, backwards)
            }
            invalid = false
            keyboard?.hide()
            if (all) onConfirm(intent) else onAction(intent)
        } catch (_: IllegalArgumentException) { invalid = true }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row {
            OutlinedTextField(pattern, { if (it.length <= maxBytes * 3) pattern = it; invalid = false }, Modifier.weight(1f), label = { Text(stringResource(R.string.yukifb_hex_pattern)) }, singleLine = true, isError = invalid)
            if (!state.readOnly) {
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(replacement, { if (it.length <= maxBytes * 3) replacement = it; invalid = false }, Modifier.weight(1f), label = { Text(stringResource(R.string.yukifb_hex_replacement)) }, singleLine = true, isError = invalid)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { hex = !hex }) { Text(stringResource(if (hex) R.string.yukifb_hex_hex else R.string.yukifb_hex_text)) }
            TextButton(onClick = { action(backwards = true) }, enabled = !state.busy) { Text(stringResource(R.string.yukifb_hex_previous)) }
            TextButton(onClick = { action() }, enabled = !state.busy) { Text(stringResource(R.string.yukifb_hex_next)) }
            if (state.searching) TextButton(onClick = { onAction(HexEditorAction.CancelSearch) }) { Text(stringResource(R.string.yukifb_hex_cancel)) }
            if (!state.readOnly) {
                TextButton(onClick = { action(replace = true) }, enabled = !state.busy) { Text(stringResource(R.string.yukifb_hex_replace)) }
                TextButton(onClick = { action(all = true) }, enabled = !state.busy) { Text(stringResource(R.string.yukifb_hex_replace_all)) }
            }
        }
        if (invalid || state.searchMiss) Text(stringResource(if (invalid) R.string.yukifb_hex_invalid_input else R.string.yukifb_hex_no_match), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
    }
}
