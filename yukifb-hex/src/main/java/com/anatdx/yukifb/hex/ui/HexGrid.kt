package com.anatdx.yukifb.hex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.anatdx.yukifb.hex.R
import com.anatdx.yukifb.hex.state.BinarySelection
import com.anatdx.yukifb.hex.state.HexEditorAction
import com.anatdx.yukifb.hex.state.HexEditorUiState
import com.anatdx.yukifb.hex.state.HexEditorWindow
import com.anatdx.yukifb.ui.YukiFBTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.floor

internal const val GRID_ROWS = 4096

/** Long absolute rows are represented by a bounded local LazyColumn window. */
internal fun gridBaseRow(target: Long, lastRow: Long): Long =
    (target - GRID_ROWS / 4).coerceIn(0, (lastRow - (GRID_ROWS - 1)).coerceAtLeast(0))

@Composable
internal fun HexGrid(
    state: HexEditorUiState,
    window: HexEditorWindow,
    onAction: (HexEditorAction) -> Unit,
    onViewport: (Long, Int) -> Unit,
    onColumns: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val sample = measurer.measure("0", style)
    val unit = sample.size.width.toFloat().coerceAtLeast(1f)
    val rowHeight = maxOf(with(density) { 24.dp.toPx() }, sample.size.height + with(density) { 6.dp.toPx() })
    val heightDp = with(density) { rowHeight.toDp() }
    val addressDigits = maxOf(4, state.size.toString(16).length)
    val addressWidth = (addressDigits + 1.5f) * unit
    val gap = unit
    val colors = YukiFBTheme.colors
    val palette = MaterialTheme.colorScheme
    val currentAction by rememberUpdatedState(onAction)
    val currentViewport by rememberUpdatedState(onViewport)
    val currentState by rememberUpdatedState(state)
    val currentRowHeight by rememberUpdatedState(rowHeight)
    val scope = rememberCoroutineScope()
    val selectRowLabel = stringResource(R.string.yukifb_hex_select_row)
    val previousLabel = stringResource(R.string.yukifb_hex_previous_byte)
    val nextLabel = stringResource(R.string.yukifb_hex_next_byte)

    BoxWithConstraints(modifier) {
        val available = with(density) { maxWidth.toPx() }
        val contentWidth = (available - addressWidth - gap - unit / 2).coerceAtLeast(4 * unit)
        val columns = listOf(16, 8, 4, 2, 1).firstOrNull {
            it * 4 * unit <= contentWidth
        } ?: 1
        // Share the spare width between both byte cells, retaining the measured font size.
        val cellPadding = (contentWidth / columns - 4 * unit) / 2
        val hexCell = 3 * unit + cellPadding
        val characterCell = unit + cellPadding
        val characterStart = addressWidth + columns * hexCell + gap
        val lastRow = state.size / columns
        var baseRow by remember(columns) { mutableLongStateOf(gridBaseRow(state.selection.caret / columns, lastRow)) }
        val list = rememberLazyListState()
        val count = (minOf(GRID_ROWS - 1L, lastRow - baseRow.coerceAtMost(lastRow)) + 1).toInt()

        LaunchedEffect(columns) { onColumns(columns) }
        LaunchedEffect(state.selection.caret, columns, state.size) {
            val target = state.selection.caret / columns
            val visible = list.layoutInfo.visibleItemsInfo
            val first = baseRow + list.firstVisibleItemIndex
            val last = baseRow + (visible.lastOrNull()?.index ?: list.firstVisibleItemIndex)
            if (target !in first..last || baseRow > lastRow) {
                if (target < baseRow || target - baseRow >= GRID_ROWS || baseRow > lastRow) baseRow = gridBaseRow(target, lastRow)
                list.scrollToItem((target - baseRow).toInt())
            }
        }
        LaunchedEffect(list, baseRow, columns, lastRow) {
            snapshotFlow { Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.layoutInfo.visibleItemsInfo.size) }
                .distinctUntilChanged().collect { (index, scroll, visibleCount) ->
                    val absolute = baseRow + index
                    if ((index < 128 && baseRow > 0) || (index > GRID_ROWS - 128 && lastRow - baseRow >= GRID_ROWS)) {
                        val nextBase = gridBaseRow(absolute, lastRow)
                        if (nextBase != baseRow) {
                            baseRow = nextBase
                            list.scrollToItem((absolute - nextBase).toInt(), scroll)
                        }
                    } else {
                        currentViewport(absolute * columns, (visibleCount.coerceAtLeast(8) + 4) * columns)
                    }
                }
        }

        // Gestures can outlive recomposition after an edit or a font/layout change.
        val currentHit by rememberUpdatedState<(Offset) -> Pair<Long, Int>> { point ->
            val first = list.layoutInfo.visibleItemsInfo.firstOrNull()
            val localRow = if (first == null) 0 else first.index + floor((point.y - first.offset) / rowHeight).toInt()
            val column = when {
                point.x >= characterStart -> floor((point.x - characterStart) / characterCell).toInt()
                else -> floor((point.x - addressWidth) / hexCell).toInt()
            }.coerceIn(0, columns - 1)
            val row = (baseRow + localRow).coerceIn(0, lastRow)
            val offset = row * columns + minOf(column.toLong(), currentState.size - row * columns)
            val nibble = if (point.x in addressWidth..characterStart && (point.x - addressWidth - column * hexCell) >= hexCell / 2) 1 else 0
            offset to nibble
        }

        Column {
            Canvas(Modifier.fillMaxWidth().height(heightDp)) {
                drawRect(colors.bottomBar)
                for (column in 0 until columns) {
                    val number = measurer.measure(column.toString(16).uppercase(), style)
                    drawText(number, palette.onSurfaceVariant, Offset(addressWidth + column * hexCell + (hexCell - number.size.width) / 2, (rowHeight - number.size.height) / 2))
                    drawText(number, palette.onSurfaceVariant, Offset(characterStart + column * characterCell + (characterCell - number.size.width) / 2, (rowHeight - number.size.height) / 2))
                }
            }
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().testTag("yukifb_hex_grid")
                    .pointerInput(columns, baseRow) {
                        detectTapGestures(
                            onTap = { point -> val (offset, nibble) = currentHit(point); currentAction(HexEditorAction.Select(BinarySelection(offset), nibble)) },
                            onDoubleTap = { point -> val (offset, _) = currentHit(point); currentAction(HexEditorAction.Select(BinarySelection(offset, offset + minOf(1L, currentState.size - offset)))) },
                        )
                    }
                    .pointerInput(columns, baseRow) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                            val anchor = currentHit(press.position).first
                            currentAction(HexEditorAction.Select(BinarySelection(anchor, anchor + minOf(1L, currentState.size - anchor))))
                            var pointer = press.id
                            while (true) {
                                // Claim selection movement before LazyColumn checks its scroll slop.
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == pointer } ?: break
                                if (change.isConsumed) break
                                change.consume()
                                if (!change.pressed) {
                                    pointer = event.changes.firstOrNull { it.pressed }?.id ?: break
                                    continue
                                }
                                if (!change.positionChangedIgnoreConsumed()) continue
                                val offset = currentHit(change.position).first
                                val selection = if (offset >= anchor) BinarySelection(anchor, offset + minOf(1L, currentState.size - offset))
                                else BinarySelection(anchor + minOf(1L, currentState.size - anchor), offset)
                                currentAction(HexEditorAction.Select(selection))
                                val edge = currentRowHeight * 2
                                val scrollBy = when {
                                    change.position.y < edge -> -currentRowHeight
                                    change.position.y > size.height - edge -> currentRowHeight
                                    else -> 0f
                                }
                                if (scrollBy != 0f) scope.launch { list.scrollBy(scrollBy) }
                            }
                        }
                    },
            ) {
                items(count, key = { baseRow + it }) { localRow ->
                    val row = baseRow + localRow
                    val offset = row * columns
                    val bytes = (0 until columns).mapNotNull { index ->
                        val position = if (index <= state.size - offset) offset + index - window.offset else -1L
                        if (position >= 0 && position < window.bytes.size && window.generation == state.generation)
                            (window.bytes[position.toInt()].toInt() and 255).toString(16).uppercase().padStart(2, '0') else null
                    }
                    val description = stringResource(R.string.yukifb_hex_row_description, offset.toString(16).uppercase(), bytes.joinToString(" "))
                    Canvas(Modifier.fillMaxWidth().height(heightDp).semantics {
                        contentDescription = description
                        customActions = listOf(
                            CustomAccessibilityAction(selectRowLabel) { currentAction(HexEditorAction.Select(BinarySelection(offset, offset + minOf(columns.toLong(), currentState.size - offset)))); true },
                            CustomAccessibilityAction(previousLabel) { currentAction(HexEditorAction.Move(-1)); true },
                            CustomAccessibilityAction(nextLabel) { currentAction(HexEditorAction.Move(1)); true },
                        )
                    }) {
                        drawRect(if (row % 2 == 0L) colors.editor else colors.listContainer.copy(alpha = colors.listContainer.alpha * 0.45f))
                        val address = measurer.measure(offset.toString(16).uppercase().padStart(addressDigits, '0'), style)
                        drawText(address, palette.onSurfaceVariant, Offset(0f, (rowHeight - address.size.height) / 2))
                        drawLine(palette.outlineVariant, Offset(addressWidth - unit / 2, 0f), Offset(addressWidth - unit / 2, rowHeight))
                        drawLine(palette.outlineVariant, Offset(characterStart - gap / 2, 0f), Offset(characterStart - gap / 2, rowHeight))
                        for (index in 0 until columns) {
                            if (index > state.size - offset) break
                            val position = offset + index
                            val selected = position >= state.selection.start && position < state.selection.end
                            val caret = position == state.selection.caret
                            val x = addressWidth + index * hexCell
                            if (selected || caret) {
                                val color = if (selected) colors.selectedContainer else palette.secondaryContainer
                                drawRect(color, Offset(x, 0f), Size(hexCell, rowHeight))
                                drawRect(color, Offset(characterStart + index * characterCell, 0f), Size(characterCell, rowHeight))
                            }
                            val windowIndex = position - window.offset
                            val value = if (position == state.size) "··" else if (window.generation == state.generation && windowIndex >= 0 && windowIndex < window.bytes.size)
                                (window.bytes[windowIndex.toInt()].toInt() and 255).toString(16).uppercase().padStart(2, '0') else "··"
                            val layout = measurer.measure(value, style)
                            val textX = x + (hexCell - layout.size.width) / 2
                            val nibbleWidth = layout.size.width / 2f
                            drawText(layout, palette.onSurface, Offset(textX, (rowHeight - layout.size.height) / 2))
                            if (caret) drawRect(palette.primary, Offset(textX + state.nibble * nibbleWidth, 2f), Size(nibbleWidth, rowHeight - 4f), style = Stroke(1.5f))
                        }
                        if (window.generation == state.generation) {
                            clipRect(characterStart, 0f, characterStart + columns * characterCell, rowHeight) {
                                for (character in window.characters) {
                                    if (character.offset - offset >= columns || character.offset + character.byteCount <= offset) continue
                                    val x = characterStart + (character.offset - offset).toFloat() * characterCell
                                    val layout = measurer.measure(AnnotatedString(character.text), style)
                                    val origin = Offset(x + (character.byteCount * characterCell - layout.size.width) / 2, (rowHeight - layout.size.height) / 2)
                                    drawText(layout, palette.onSurface, origin)
                                    val begin = maxOf(character.offset, state.selection.start)
                                    val end = minOf(character.offset + character.byteCount, state.selection.end)
                                    if (begin < end) clipRect(
                                        characterStart + (begin - offset).toFloat() * characterCell, 0f,
                                        characterStart + (end - offset).toFloat() * characterCell, rowHeight,
                                    ) { drawText(layout, palette.onSecondaryContainer, origin) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
