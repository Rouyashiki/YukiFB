package com.anatdx.yukifb.hex.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.anatdx.yukifb.hex.R
import com.anatdx.yukifb.hex.backend.*
import com.anatdx.yukifb.hex.state.*
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.ui.YukiFBProvider
import com.anatdx.yukifb.ui.YukiFBStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class HexEditorInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var controller: HexEditorController
    private var closed = false
    private var requestedBytes = 0L
    private var editorWidth by mutableStateOf(Dp.Unspecified)
    private val back = object : NavigationEventInput() { fun complete() = dispatchOnBackCompleted() }
    private val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
    }

    private fun label(id: Int) = compose.activity.getString(id)

    private fun show(large: Boolean = false, fontScale: Float = 1f, dark: Boolean = false, initialSize: Long = 4096L, width: Dp = Dp.Unspecified, densityScale: Float = 1f, readOverride: (suspend (Long, Int) -> BinaryBytes)? = null) {
        editorWidth = width
        compose.runOnUiThread { owner.navigationEventDispatcher.addInput(back) }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val source = remember {
                object : BinaryDataSource {
                    override val id = EntryId("fixture")
                    override val revision = "1"
                    override val size = if (large) 128L * 1024 * 1024 * 1024 else initialSize
                    override suspend fun read(offset: Long, length: Int): BinaryBytes {
                        requestedBytes += length
                        readOverride?.let { return it(offset, length) }
                        return BinaryBytes.copyOf(ByteArray(length) { i ->
                            when (offset + i) { 2L -> 0xe4.toByte(); 3L -> 0xb8.toByte(); 4L -> 0xad.toByte(); else -> (32 + (offset + i) % 95).toByte() }
                        })
                    }
                }
            }
            controller = remember {
                HexEditorController(source, scope, committer = if (large) null else BinaryCommitter { request ->
                    val output = ByteArrayOutputStream()
                    for (piece in request.pieces) {
                        output.write(when (piece) {
                            is BinaryPiece.Original -> request.source.read(piece.offset, piece.length.toInt()).toByteArray()
                            is BinaryPiece.Inserted -> request.readInserted(piece.offset, piece.length.toInt()).toByteArray()
                        })
                    }
                    val saved = output.toByteArray()
                    object : BinaryDataSource {
                        override val id = source.id
                        override val revision = "saved"
                        override val size = saved.size.toLong()
                        override suspend fun read(offset: Long, length: Int) = BinaryBytes.copyOf(saved.copyOfRange(offset.toInt(), minOf(saved.size, offset.toInt() + length)))
                    }
                }, codecs = listOf(FixtureCodec))
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * densityScale, fontScale), LocalNavigationEventDispatcherOwner provides owner) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else MaterialTheme.colorScheme) {
                    YukiFBProvider(style = if (dark) YukiFBStyle.Classic else YukiFBStyle.Expressive) {
                        HexEditor("firmware.bin", controller, { closed = true },
                            if (editorWidth.isSpecified) Modifier.width(editorWidth).fillMaxHeight() else Modifier.fillMaxSize(), confirmClose = true)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun release() {
        if (::controller.isInitialized) runBlocking { controller.close() }
        compose.runOnUiThread { owner.navigationEventDispatcher.removeInput(back) }
    }

    @Test fun keypadSaveUndoAndCloseConfirmation() {
        show()
        compose.onNodeWithText("A").performClick()
        compose.onNodeWithText("B").performClick()
        compose.waitUntil { controller.state.value.selection.caret == 1L && !controller.state.value.busy }
        assertEquals(0xab.toByte(), runBlocking { controller.read(0, 1)[0] })
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_save)).performClick()
        compose.waitUntil { !controller.state.value.dirty && !controller.state.value.busy }
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_undo)).performClick()
        compose.waitUntil { controller.state.value.dirty && !controller.state.value.busy }
        assertEquals(32.toByte(), runBlocking { controller.read(0, 1)[0] })
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_back)).performClick()
        compose.onNodeWithText(label(R.string.yukifb_hex_unsaved)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.yukifb_hex_cancel)).performClick()
        assertFalse(closed)
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_back)).performClick()
        compose.onNodeWithText(label(R.string.yukifb_hex_discard)).performClick()
        assertTrue(closed)
    }

    @Test fun multiByteSelectionAndDenseLayoutRender() {
        show()
        compose.runOnIdle { controller.select(BinarySelection(3, 4)) }
        compose.waitForIdle()
        compose.onNodeWithTag("yukifb_hex_grid").assertIsDisplayed()
        assertEquals(1L, controller.state.value.selection.length)
        val mapping = runBlocking { controller.window(0, 32) }.characters.first { it.offset == 2L }
        assertEquals(3, mapping.byteCount)
        assertEquals("中", mapping.text)
        screenshot("hex-expressive-partial-character.png")
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_more)).performClick()
        compose.onNodeWithText(label(R.string.yukifb_hex_delete)).performClick()
        compose.onNodeWithText(label(R.string.yukifb_hex_confirm_edit)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.yukifb_hex_cancel)).performClick()
        assertEquals(4096L, controller.state.value.size)
    }

    @Test fun deleteConfirmationKeepsTargetWhenSearchFinishes() {
        confirmWhileSearchFinishes(R.string.yukifb_hex_delete)
    }

    @Test fun cutConfirmationKeepsTargetWhenSearchFinishes() {
        confirmWhileSearchFinishes(R.string.yukifb_hex_cut)
        assertEquals(1L, controller.state.value.clipboardSize)
    }

    @Test fun fillInputKeepsTargetWhenSearchFinishesBeforeConfirmation() {
        confirmWhileSearchFinishes(R.string.yukifb_hex_fill)
    }

    private fun confirmWhileSearchFinishes(actionLabel: Int) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val match = 65536L
        show(initialSize = match + 16, readOverride = { offset, length ->
            if (offset >= match) { entered.complete(Unit); release.await() }
            BinaryBytes.copyOf(ByteArray(length) { index -> if (offset + index == match) 30 else 10 })
        })
        try {
            compose.runOnIdle {
                controller.select(BinarySelection(0, 1))
                controller.dispatch(HexEditorAction.Find(BinaryBytes.copyOf(byteArrayOf(30))))
            }
            compose.waitUntil(5000) { entered.isCompleted }
            compose.onNodeWithContentDescription(label(R.string.yukifb_hex_more)).performClick()
            compose.onNodeWithText(label(actionLabel)).performClick()
            release.complete(Unit)
            compose.waitUntil(5000) { controller.state.value.selection == BinarySelection(match, match + 1) }
            // Fill captures the target before its value dialog, not after the search completes.
            if (actionLabel == R.string.yukifb_hex_fill) compose.onNodeWithText(label(R.string.yukifb_hex_apply)).performClick()
            compose.onNodeWithText(label(R.string.yukifb_hex_confirm_edit)).assertIsDisplayed()
            compose.onNodeWithText(label(R.string.yukifb_hex_apply)).performClick()
            compose.waitUntil(5000) { controller.state.value.dirty && !controller.state.value.busy }
            assertNull(controller.state.value.error)
            if (actionLabel == R.string.yukifb_hex_fill) {
                assertEquals(0.toByte(), runBlocking { controller.read(0, 1)[0] })
                assertEquals(30.toByte(), runBlocking { controller.read(match, 1)[0] })
            } else {
                assertEquals(match + 15, controller.state.value.size)
                assertEquals(30.toByte(), runBlocking { controller.read(match - 1, 1)[0] })
            }
        } finally { release.complete(Unit) }
    }

    @Test fun changedContentRejectsOpenConfirmation() {
        show(initialSize = 16)
        compose.runOnIdle { controller.select(BinarySelection(0, 1)) }
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_more)).performClick()
        compose.onNodeWithText(label(R.string.yukifb_hex_delete)).performClick()
        compose.runOnIdle { runBlocking { controller.replace(0, 1, BinaryBytes.copyOf(byteArrayOf(99))) } }
        compose.onNodeWithText(label(R.string.yukifb_hex_apply)).performClick()
        compose.waitUntil(5000) { controller.state.value.error != null && !controller.state.value.busy }
        assertEquals(16L, controller.state.value.size)
        assertEquals(99.toByte(), runBlocking { controller.read(0, 1)[0] })
    }

    @Test fun tappingAndDraggingNewRowsUseCurrentFileLength() {
        show(initialSize = 0)
        val grid = compose.onNodeWithTag("yukifb_hex_grid")
        // Start both gesture handlers before inserting more rows.
        grid.performTouchInput { click(Offset(1f, 10f)) }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.runOnIdle { runBlocking {
            controller.replace(0, 0, BinaryBytes.copyOf(ByteArray(32) { it.toByte() }))
            controller.select(BinarySelection(0))
        } }
        val rowDescription = compose.activity.getString(R.string.yukifb_hex_row_description, "8", "08 09 0A 0B 0C 0D 0E 0F")
        compose.waitUntil { compose.onAllNodesWithContentDescription(rowDescription).fetchSemanticsNodes().isNotEmpty() }
        val row = compose.onNodeWithContentDescription(rowDescription)
        row.performTouchInput { click(Offset(1f, height / 2f)) }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(BinarySelection(8), controller.state.value.selection)

        compose.runOnIdle { controller.select(BinarySelection(0)) }
        val rowBounds = row.fetchSemanticsNode().boundsInRoot
        val gridBounds = grid.fetchSemanticsNode().boundsInRoot
        grid.performTouchInput {
            // Keep this gesture outside the preceding tap's double-tap window.
            advanceEventTime(1000)
            down(Offset(1f, rowBounds.center.y - gridBounds.top))
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitUntil { controller.state.value.selection == BinarySelection(8, 9) }
        grid.performTouchInput {
            repeat(8) { moveBy(Offset(0f, rowBounds.height / 8)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(BinarySelection(8, 17), controller.state.value.selection)
    }

    @Test fun hugeDocumentJumpUsesBoundedReadsAndHidesEditing() {
        show(large = true)
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_save)).assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_more)).performClick()
        compose.onNodeWithText(label(R.string.yukifb_hex_goto)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("1800000000")
        compose.onNodeWithText(label(R.string.yukifb_hex_apply)).performClick()
        compose.waitUntil { controller.state.value.selection.caret == 0x1800000000L }
        compose.waitForIdle()
        assertTrue(requestedBytes < 1024 * 1024)
        screenshot("hex-large-file.png")
    }

    @Test fun longPressOwnsFastDragAndReleasesScrollingOnUp() {
        show()
        val description = compose.activity.getString(R.string.yukifb_hex_row_description, "8", "28 29 2A 2B 2C 2D 2E 2F")
        compose.waitUntil { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        val row = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag("yukifb_hex_grid")
        val bounds = grid.fetchSemanticsNode().boundsInRoot
        grid.performTouchInput { down(Offset(1f, row.center.y - bounds.top)) }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitUntil { controller.state.value.selection == BinarySelection(8, 9) }
        grid.performTouchInput {
            // The first move already exceeds scroll touch slop.
            moveBy(Offset(0f, row.height * 2))
            up()
        }
        compose.waitForIdle()
        assertEquals(BinarySelection(8, 25), controller.state.value.selection)

        grid.performTouchInput { advanceEventTime(1000); swipeUp() }
        compose.waitUntil { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isEmpty() }
        assertEquals(BinarySelection(8, 25), controller.state.value.selection)
    }

    @Test fun cancelledLongPressAllowsNextScrollGesture() {
        show()
        val description = compose.activity.getString(R.string.yukifb_hex_row_description, "8", "28 29 2A 2B 2C 2D 2E 2F")
        compose.waitUntil { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        val row = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag("yukifb_hex_grid")
        val bounds = grid.fetchSemanticsNode().boundsInRoot
        grid.performTouchInput { down(Offset(1f, row.center.y - bounds.top)) }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitUntil { controller.state.value.selection == BinarySelection(8, 9) }
        grid.performTouchInput { cancel() }
        compose.waitForIdle()
        grid.performTouchInput { advanceEventTime(1000); swipeUp() }
        compose.waitUntil { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isEmpty() }
        assertEquals(BinarySelection(8, 9), controller.state.value.selection)
    }

    @Test fun largeFontClassicDarkLayoutKeepsKeypadAccessible() {
        show(fontScale = 1.6f, dark = true, width = 320.dp)
        assertKeypadToolbarFits()
        compose.onNodeWithText("F").assertIsDisplayed().performClick()
        compose.waitUntil { controller.state.value.dirty }
        screenshot("hex-classic-large-font.png")
    }

    @Test fun characterCellsFillPhoneWidthAndFollowResize() {
        show(initialSize = 0x2aa760, width = 360.dp)
        assertKeypadToolbarFits()
        assertCharacterCellsReachRightEdge(7)
        val grid = compose.onNodeWithTag("yukifb_hex_grid")
        for ((fraction, nibble) in listOf(0.32f to 0, 0.35f to 1)) {
            grid.performTouchInput { advanceEventTime(1000); click(Offset(width * fraction, 4f)) }
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            assertEquals(BinarySelection(2), controller.state.value.selection)
            assertEquals(nibble, controller.state.value.nibble)
        }
        screenshot("hex-responsive-phone.png")

        compose.runOnIdle { editorWidth = 320.dp; controller.select(BinarySelection(0)) }
        compose.waitForIdle()
        assertKeypadToolbarFits()
        assertCharacterCellsReachRightEdge(7)
        screenshot("hex-responsive-narrow.png")
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_hide_keyboard)).performClick()
        compose.onNodeWithTag("yukifb_hex_keypad_toolbar").assertDoesNotExist()
    }

    @Test fun wideGridUsesSixteenColumnsForCursorMovement() {
        show(width = 720.dp, densityScale = 0.5f)
        assertCharacterCellsReachRightEdge(15)
        // The last visible byte is at the end of the sixteen-byte row.
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_next_byte)).performClick()
        compose.waitUntil { controller.state.value.selection.caret == 16L }
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_previous_row)).performClick()
        compose.waitUntil { controller.state.value.selection.caret == 0L }
        screenshot("hex-responsive-wide.png")
    }

    private fun assertCharacterCellsReachRightEdge(lastColumn: Long) {
        val grid = compose.onNodeWithTag("yukifb_hex_grid")
        val before = grid.captureToImage().toPixelMap()
        val selections = mutableListOf<Long>()
        // These taps occupied the unused right margin in the old layout.
        for (fraction in listOf(0.80f, 0.86f, 0.92f, 0.98f)) {
            grid.performTouchInput {
                advanceEventTime(1000)
                click(Offset(width * fraction, 4f))
            }
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            selections += controller.state.value.selection.caret
        }
        assertEquals(lastColumn, selections.last())
        assertTrue("Character cells must remain separately selectable across the right edge: $selections", selections.distinct().size >= 3)
        val after = grid.captureToImage().toPixelMap()
        val x = (after.width * 0.98f).toInt()
        assertNotEquals("The last character cell must draw its highlight near the right edge", before[x, 4], after[x, 4])
    }

    private fun assertKeypadToolbarFits() {
        val toolbar = compose.onNodeWithTag("yukifb_hex_keypad_toolbar").fetchSemanticsNode().boundsInRoot
        for (id in listOf(R.string.yukifb_hex_previous_row, R.string.yukifb_hex_previous_byte, R.string.yukifb_hex_next_byte, R.string.yukifb_hex_next_row, R.string.yukifb_hex_hide_keyboard)) {
            val button = compose.onNodeWithContentDescription(label(id)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Toolbar action must fit without horizontal scrolling", button.left >= toolbar.left && button.right <= toolbar.right && button.top >= toolbar.top && button.bottom <= toolbar.bottom)
        }
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_high_nibble)).assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_low_nibble)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.yukifb_hex_overwrite)).assertIsDisplayed().performClick()
        compose.waitUntil { controller.state.value.mode == HexInputMode.Insert }
        compose.onNodeWithText(label(R.string.yukifb_hex_insert)).assertIsDisplayed().performClick()
        compose.waitUntil { controller.state.value.mode == HexInputMode.Overwrite }
    }

    @Test fun systemBackUsesUnsavedConfirmation() {
        show()
        compose.onNodeWithText("A").performClick()
        compose.waitUntil { controller.state.value.dirty && !controller.state.value.busy }
        compose.runOnIdle { back.complete() }
        compose.waitUntil { compose.onAllNodesWithText(label(R.string.yukifb_hex_unsaved)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(label(R.string.yukifb_hex_unsaved)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.yukifb_hex_cancel)).performClick()
        assertFalse(closed)
        compose.runOnIdle { back.complete() }
        compose.waitUntil { compose.onAllNodesWithText(label(R.string.yukifb_hex_unsaved)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(label(R.string.yukifb_hex_save)).performClick()
        compose.waitUntil { closed }
        assertFalse(controller.state.value.dirty)
    }

    @Test fun searchReplaceAndInspectorUseCurrentBytes() {
        show()
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_more)).performClick()
        compose.onNodeWithText(label(R.string.yukifb_hex_search)).performClick()
        compose.onNodeWithText(label(R.string.yukifb_hex_pattern)).performTextInput("21")
        compose.onNodeWithText(label(R.string.yukifb_hex_replacement)).performTextInput("FE")
        compose.onNodeWithText(label(R.string.yukifb_hex_next)).performClick()
        compose.waitUntil { controller.state.value.selection == BinarySelection(1, 2) }
        compose.onNodeWithText(label(R.string.yukifb_hex_replace)).performClick()
        compose.waitUntil { controller.state.value.dirty && !controller.state.value.busy }
        assertEquals(0xfe.toByte(), runBlocking { controller.read(1, 1)[0] })
        compose.runOnIdle { controller.select(BinarySelection(0)) }
        compose.onNodeWithContentDescription(label(R.string.yukifb_hex_more)).performClick()
        compose.onNodeWithText(label(R.string.yukifb_hex_inspector)).performClick()
        compose.onNodeWithText("u16: 65056").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.yukifb_hex_big_endian)).performClick()
        compose.onNodeWithText("u16: 8446").assertIsDisplayed()
        compose.runOnIdle { controller.select(BinarySelection(0, 1)) }
        compose.onNodeWithText("u16: " + label(R.string.yukifb_hex_incomplete)).assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(compose.activity.getExternalFilesDir(null), "hex-tests").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private object FixtureCodec : BinaryTextCodec {
    override val id = "fixture-utf8"
    override val label = "UTF-8"
    override fun encode(text: String) = BinaryBytes.copyOf(text.toByteArray())
    override fun decode(bytes: BinaryBytes, offset: Long): List<BinaryCharacter> {
        val result = ArrayList<BinaryCharacter>()
        var i = 0
        while (i < bytes.size) {
            if (i + 2 < bytes.size && bytes[i] == 0xe4.toByte() && bytes[i + 1] == 0xb8.toByte() && bytes[i + 2] == 0xad.toByte()) {
                result += BinaryCharacter(offset + i, 3, "中")
                i += 3
            } else {
                val value = bytes[i].toInt() and 255
                result += BinaryCharacter(offset + i, 1, if (value in 32..126) value.toChar().toString() else "·")
                i++
            }
        }
        return result
    }
}
