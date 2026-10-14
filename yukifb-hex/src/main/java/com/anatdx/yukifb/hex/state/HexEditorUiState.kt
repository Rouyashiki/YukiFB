package com.anatdx.yukifb.hex.state

import com.anatdx.yukifb.hex.backend.BinaryBytes
import com.anatdx.yukifb.hex.backend.BinaryCharacter

/** Byte boundaries forming a direction-preserving, end-exclusive selection. */
data class BinarySelection(
    /** Fixed selection boundary. */
    val anchor: Long = 0,
    /** Moving selection boundary or insertion point. */
    val caret: Long = anchor,
) {
    /** First selected byte. */
    val start: Long get() = minOf(anchor, caret)
    /** Boundary after the final selected byte. */
    val end: Long get() = maxOf(anchor, caret)
    /** Number of selected bytes. */
    val length: Long get() = end - start
}

/** Selection and content generation captured before asking the user to confirm an edit. */
data class HexEditTarget(
    /** Byte range shown when confirmation began. */
    val selection: BinarySelection,
    /** Content generation that must still be current when the edit starts. */
    val generation: Long,
)

/** Byte input policy. */
enum class HexInputMode {
    /** Change existing bytes without extending EOF. */
    Overwrite,
    /** Insert new bytes before the caret. */
    Insert,
}

/** Immutable, headless editor state. */
data class HexEditorUiState(
    /** Current document length. */
    val size: Long = 0,
    /** Selected byte boundaries. */
    val selection: BinarySelection = BinarySelection(),
    /** Active half-byte: zero for high, one for low. */
    val nibble: Int = 0,
    /** Current input policy. */
    val mode: HexInputMode = HexInputMode.Overwrite,
    /** Whether persistence is unavailable. */
    val readOnly: Boolean = true,
    /** Whether this content differs from the saved history position. */
    val dirty: Boolean = false,
    /** Whether undo is available. */
    val canUndo: Boolean = false,
    /** Whether redo is available. */
    val canRedo: Boolean = false,
    /** Whether a content-changing operation is in progress. */
    val busy: Boolean = false,
    /** Whether a save is in progress. */
    val saving: Boolean = false,
    /** Whether search is in progress. */
    val searching: Boolean = false,
    /** Progress in a long operation, or null for indeterminate work. */
    val progress: Float? = null,
    /** Content generation used to reject stale viewport results. */
    val generation: Long = 0,
    /** Encoding selected from the host's codec list. */
    val codecId: String = "ASCII",
    /** Number of bytes in the internal clipboard. */
    val clipboardSize: Long = 0,
    /** Last search found no match. */
    val searchMiss: Boolean = false,
    /** Backend failure detail; the UI supplies the localized surrounding message. */
    val error: String? = null,
)

/** A bounded window and decoded characters, including the codec's edge context. */
data class HexEditorWindow(
    /** Absolute byte offset of [bytes]. */
    val offset: Long = 0,
    /** Immutable bounded bytes. */
    val bytes: BinaryBytes = BinaryBytes.Empty,
    /** Character mappings intersecting this window. */
    val characters: List<BinaryCharacter> = emptyList(),
    /** Content generation represented by this window. */
    val generation: Long = 0,
)

/** Bounded cache and operation limits; lengths and document positions remain 64-bit. */
data class HexEditorOptions(
    /** Size of backend read requests. */
    val pageSize: Int = 64 * 1024,
    /** Maximum cached source bytes. */
    val cacheBytes: Int = 4 * 1024 * 1024,
    /** Maximum individual search pattern and system-clipboard byte count. */
    val maxInputBytes: Int = 64 * 1024,
    /** Maximum edits in one atomic replace-all operation. */
    val maxChangesPerOperation: Int = 65_536,
) {
    init {
        require(pageSize in 1024..1024 * 1024 && cacheBytes >= pageSize)
        require(maxInputBytes in 1..1024 * 1024 && maxChangesPerOperation > 0)
    }
}

/** UI intents; content-changing intents are serialized by the controller. */
sealed interface HexEditorAction {
    /** Applies a confirmed destructive action to its captured target, rejecting changed content.
     * @property action Cut, Delete, Fill or ReplaceAll; other actions cannot be confirmed this way.
     * @property target Selection and content generation shown to the user before confirmation.
     */
    data class Confirmed(val action: HexEditorAction, val target: HexEditTarget) : HexEditorAction {
        init {
            require(action == Cut || action == Delete || action is Fill || action is ReplaceAll)
        }
    }

    /** Changes the byte selection and active half-byte.
     * @property selection End-exclusive selected byte boundaries.
     * @property nibble Zero for the high nibble, one for the low nibble.
     */
    data class Select(val selection: BinarySelection, val nibble: Int = 0) : HexEditorAction
    /** Moves the caret by a signed byte count.
     * @property delta Signed movement, clamped at document boundaries.
     * @property extend Keep the existing selection anchor.
     */
    data class Move(val delta: Long, val extend: Boolean = false) : HexEditorAction
    /** Changes the byte input policy.
     * @property mode New insert/overwrite policy.
     */
    data class Mode(val mode: HexInputMode) : HexEditorAction
    /** Changes the host codec used for display and text operations.
     * @property id Identity of a configured host codec.
     */
    data class Codec(val id: String) : HexEditorAction
    /** Enters one hexadecimal digit.
     * @property value Digit in the range 0 through 15.
     */
    data class Digit(val value: Int) : HexEditorAction
    /** Inserts or overwrites with an explicit bounded input.
     * @property bytes Immutable input bytes.
     */
    data class Input(val bytes: BinaryBytes) : HexEditorAction
    /** Copies the selected bytes to the internal clipboard. */
    data object Copy : HexEditorAction
    /** Copies and removes the selected bytes; confirmation belongs to the caller. */
    data object Cut : HexEditorAction
    /** Pastes the internal clipboard using the current mode. */
    data object Paste : HexEditorAction
    /** Removes the selection; confirmation belongs to the caller. */
    data object Delete : HexEditorAction
    /** Repeats a byte over the selection.
     * @property value Unsigned byte in the range 0 through 255.
     */
    data class Fill(val value: Int) : HexEditorAction
    /** Restores the previous edit. */
    data object Undo : HexEditorAction
    /** Reapplies the next edit. */
    data object Redo : HexEditorAction
    /** Atomically persists the current document. */
    data object Save : HexEditorAction
    /** Searches from the current selection, wrapping once at the document boundary.
     * @property pattern Nonempty bounded byte sequence.
     * @property backwards Search toward the document beginning first.
     */
    data class Find(val pattern: BinaryBytes, val backwards: Boolean = false) : HexEditorAction
    /** Replaces the selected match only if it still equals the pattern.
     * @property pattern Expected selected bytes.
     * @property replacement New bytes, possibly empty.
     */
    data class ReplaceMatch(val pattern: BinaryBytes, val replacement: BinaryBytes) : HexEditorAction
    /** Replaces non-overlapping matches from a frozen document; caller confirms first.
     * @property pattern Nonempty bounded search bytes.
     * @property replacement New bytes, possibly empty.
     */
    data class ReplaceAll(val pattern: BinaryBytes, val replacement: BinaryBytes) : HexEditorAction
    /** Cancels the active search. */
    data object CancelSearch : HexEditorAction
    /** Clears the reported failure. */
    data object DismissError : HexEditorAction
}
