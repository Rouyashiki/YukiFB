package com.anatdx.yukifb.hex.state

import com.anatdx.yukifb.hex.backend.AsciiBinaryTextCodec
import com.anatdx.yukifb.hex.backend.BinaryBytes
import com.anatdx.yukifb.hex.backend.BinaryCommitter
import com.anatdx.yukifb.hex.backend.BinaryDataSource
import com.anatdx.yukifb.hex.backend.BinaryEditStorage
import com.anatdx.yukifb.hex.backend.BinaryPiece
import com.anatdx.yukifb.hex.backend.BinarySaveRequest
import com.anatdx.yukifb.hex.backend.BinaryTextCodec
import com.anatdx.yukifb.hex.backend.MemoryBinaryEditStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.EOFException
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/**
 * Headless paged binary editor. The host supplies persistence and scratch storage.
 * Call [close] when the document is discarded; saving does not dispose undo history.
 */
class HexEditorController(
    source: BinaryDataSource,
    parentScope: CoroutineScope,
    private val storage: BinaryEditStorage = MemoryBinaryEditStorage(),
    private val committer: BinaryCommitter? = null,
    codecs: List<BinaryTextCodec> = listOf(AsciiBinaryTextCodec),
    /** Cache and operation bounds. */
    val options: HexEditorOptions = HexEditorOptions(),
) {
    private data class Document(val source: BinaryDataSource, val tree: PieceTree?, val generation: Long)
    private data class Change(val offset: Long, val removed: PieceTree?, val inserted: PieceTree?)
    private data class History(
        val changes: List<Change>, val before: BinarySelection, val after: BinarySelection,
        val beforeMarker: Long, val afterMarker: Long, val nibbleOffset: Long? = null,
    )

    /** Supported host encodings, in display order. */
    val codecs: List<BinaryTextCodec> = Collections.unmodifiableList(codecs.toList())
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val edits = Mutex()
    private val pages = BinaryPageCache(options.pageSize, options.cacheBytes)
    @Volatile private var document = Document(source, pieceTree(BinaryPiece.Original(0, source.size)), 0)
    @Volatile private var closed = false
    private var marker = 0L
    private var savedMarker = 0L
    private var nextMarker = 1L
    private val undo = ArrayList<History>()
    private val redo = ArrayList<History>()
    private var clipboard: PieceTree? = null
    private var searchJob: Job? = null
    private val searchSequence = AtomicLong()
    private var coalesceOffset: Long? = null
    private val mutableState: MutableStateFlow<HexEditorUiState>

    init {
        require(source.size >= 0 && source.revision.isNotEmpty())
        require(codecs.isNotEmpty() && codecs.map { it.id }.distinct().size == codecs.size)
        require(codecs.all { it.contextBytes in 0..4096 })
        mutableState = MutableStateFlow(HexEditorUiState(size = source.size, readOnly = committer == null, codecId = codecs.first().id))
    }

    /** Immutable editor state. */
    val state: StateFlow<HexEditorUiState> = mutableState.asStateFlow()

    /** Current host codec. */
    val codec: BinaryTextCodec get() = codecs.first { it.id == state.value.codecId }

    /** Dispatches a UI intent and reports failures through [state]. */
    fun dispatch(action: HexEditorAction): Job {
        if (action is HexEditorAction.Find || action is HexEditorAction.CancelSearch) {
            searchSequence.incrementAndGet()
            searchJob?.cancel()
            mutableState.update { it.copy(searching = false, progress = null) }
        }
        return scope.launch {
            try {
                when (action) {
                    is HexEditorAction.Confirmed -> applyConfirmed(action)
                    is HexEditorAction.Select -> select(action.selection, action.nibble)
                    is HexEditorAction.Move -> move(action.delta, action.extend)
                    is HexEditorAction.Mode -> { coalesceOffset = null; mutableState.update { it.copy(mode = action.mode) } }
                    is HexEditorAction.Codec -> {
                        require(codecs.any { it.id == action.id })
                        mutableState.update { it.copy(codecId = action.id) }
                    }
                    is HexEditorAction.Digit -> inputDigit(action.value)
                    is HexEditorAction.Input -> input(action.bytes)
                    HexEditorAction.Copy -> copySelection()
                    HexEditorAction.Cut -> cutSelection()
                    HexEditorAction.Paste -> paste()
                    HexEditorAction.Delete -> deleteSelection()
                    is HexEditorAction.Fill -> fill(action.value)
                    HexEditorAction.Undo -> undo()
                    HexEditorAction.Redo -> redo()
                    HexEditorAction.Save -> save()
                    is HexEditorAction.Find -> find(action.pattern, action.backwards)
                    is HexEditorAction.ReplaceMatch -> replaceMatch(action.pattern, action.replacement)
                    is HexEditorAction.ReplaceAll -> replaceAll(action.pattern, action.replacement)
                    HexEditorAction.CancelSearch -> Unit
                    HexEditorAction.DismissError -> mutableState.update { it.copy(error = null) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: error.javaClass.simpleName) }
            }
        }.also { if (action is HexEditorAction.Find) searchJob = it }
    }

    /** Moves the selection to valid byte boundaries. */
    fun select(selection: BinarySelection, nibble: Int = 0) {
        require(selection.anchor in 0..state.value.size && selection.caret in 0..state.value.size && nibble in 0..1)
        coalesceOffset = null
        mutableState.update { it.copy(selection = selection, nibble = nibble, searchMiss = false) }
    }

    private fun move(delta: Long, extend: Boolean) {
        val old = state.value.selection
        val size = state.value.size
        val next = if (delta >= 0) old.caret + minOf(delta, size - old.caret)
        else old.caret - minOf(if (delta == Long.MIN_VALUE) Long.MAX_VALUE else -delta, old.caret)
        select(BinarySelection(if (extend) old.anchor else next, next))
    }

    /** Reads only the requested edited range. Requests are bounded to one MiB. */
    suspend fun read(offset: Long, length: Int): BinaryBytes {
        checkOpen()
        require(length in 0..1024 * 1024)
        val snapshot = document
        require(offset in 0..snapshot.tree.size)
        return read(snapshot, offset, minOf(length.toLong(), snapshot.tree.size - offset).toInt())
    }

    /** Loads a display window with enough context to map characters crossing its edges. */
    suspend fun window(offset: Long, length: Int): HexEditorWindow = withContext(Dispatchers.Default) {
        checkOpen()
        require(length in 0..1024 * 1024 - 8192)
        val snapshot = document
        val decoder = codec
        val begin = (offset.coerceIn(0, snapshot.tree.size) - decoder.contextBytes).coerceAtLeast(0)
        val end = offset.coerceIn(0, snapshot.tree.size) + minOf(length.toLong() + decoder.contextBytes, snapshot.tree.size - offset.coerceIn(0, snapshot.tree.size))
        val bytes = read(snapshot, begin, (end - begin).toInt())
        val characters = decoder.decode(bytes, begin)
        require(characters.all { it.offset >= begin && it.byteCount > 0 && it.offset <= end && it.byteCount <= end - it.offset })
        HexEditorWindow(begin, bytes, Collections.unmodifiableList(characters.toList()), snapshot.generation)
    }

    private suspend fun read(snapshot: Document, offset: Long, length: Int): BinaryBytes {
        val output = ByteArray(length)
        var destination = 0
        for (piece in snapshot.tree.pieces(offset, length.toLong())) {
            currentCoroutineContext().ensureActive()
            val bytes = when (piece) {
                is BinaryPiece.Original -> pages.read(snapshot.source, piece.offset, piece.length.toInt())
                is BinaryPiece.Inserted -> storage.read(piece.offset, piece.length.toInt())
            }
            if (bytes.size.toLong() != piece.length) throw EOFException("Incomplete binary range")
            bytes.copyInto(output, destination)
            destination += bytes.size
        }
        return BinaryBytes.owned(output)
    }

    private suspend fun append(bytes: BinaryBytes): PieceTree? =
        if (bytes.size == 0) null else pieceTree(BinaryPiece.Inserted(storage.append(bytes), bytes.size.toLong()))

    private suspend fun capture(snapshot: Document, offset: Long, count: Long): PieceTree? {
        var captured: PieceTree? = null
        var position = 0L
        while (position < count) {
            currentCoroutineContext().ensureActive()
            val size = minOf(options.pageSize.toLong(), count - position).toInt()
            captured = join(captured, append(read(snapshot, offset + position, size)))
            position += size
            mutableState.update { it.copy(progress = position.toFloat() / count) }
            yield()
        }
        return captured
    }

    private fun checkOpen() { check(!closed && job.isActive) { "Editor is closed" } }

    private suspend fun <T> edit(block: suspend () -> T): T = edits.withLock {
        checkOpen()
        check(committer != null) { "Document is read-only" }
        searchSequence.incrementAndGet()
        searchJob?.cancel()
        mutableState.update { it.copy(busy = true, searching = false, error = null, progress = null) }
        try { block() } finally { mutableState.update { it.copy(busy = false, progress = null) } }
    }

    private fun checkLength(offset: Long, removed: Long, inserted: Long) {
        require(offset in 0..document.tree.size && removed >= 0 && removed <= document.tree.size - offset)
        require(checkedSum(document.tree.size - removed, inserted) <= (committer?.maxSize ?: 0)) { "Backend content limit exceeded" }
    }

    private fun publish(tree: PieceTree?, selection: BinarySelection, nibble: Int = 0) {
        document = document.copy(tree = tree, generation = document.generation + 1)
        mutableState.update {
            it.copy(size = tree.size, selection = selection, nibble = nibble, generation = document.generation,
                dirty = marker != savedMarker, canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty(), searchMiss = false)
        }
    }

    private fun record(changes: List<Change>, tree: PieceTree?, after: BinarySelection, nibble: Int = 0, nibbleOffset: Long? = null) {
        val previous = undo.lastOrNull()
        val next = nextMarker++
        if (nibbleOffset != null && coalesceOffset == nibbleOffset && previous?.nibbleOffset == nibbleOffset && marker != savedMarker) {
            undo[undo.lastIndex] = previous.copy(
                changes = listOf(previous.changes.single().copy(inserted = changes.single().inserted)),
                after = after, afterMarker = next, nibbleOffset = null,
            )
            coalesceOffset = null
        } else {
            undo += History(changes, state.value.selection, after, marker, next, nibbleOffset)
            coalesceOffset = nibbleOffset
        }
        redo.clear()
        marker = next
        publish(tree, after, nibble)
    }

    /** Replaces an arbitrary range as one undoable edit. */
    suspend fun replace(offset: Long, count: Long, bytes: BinaryBytes) = edit {
        replaceLocked(offset, count, appendAfterValidation(offset, count, bytes))
    }

    private suspend fun appendAfterValidation(offset: Long, count: Long, bytes: BinaryBytes): PieceTree? {
        checkLength(offset, count, bytes.size.toLong())
        return append(bytes)
    }

    private suspend fun replaceLocked(offset: Long, count: Long, inserted: PieceTree?) {
        checkLength(offset, count, inserted.size)
        if (count == 0L && inserted == null) return
        val removed = capture(document, offset, count)
        currentCoroutineContext().ensureActive()
        record(listOf(Change(offset, removed, inserted)), document.tree.replace(offset, count, inserted), BinarySelection(offset + inserted.size))
    }

    /** Inputs bytes using the current selection and insert/overwrite mode. */
    suspend fun input(bytes: BinaryBytes) = edit {
        val selected = state.value.selection
        val removed = inputRemoval(bytes.size.toLong())
        replaceLocked(selected.start, removed, appendAfterValidation(selected.start, removed, bytes))
    }

    private fun inputRemoval(count: Long): Long {
        val state = state.value
        if (state.mode == HexInputMode.Insert) return state.selection.length
        require(count <= state.size - state.selection.start) { "Switch to insert mode to extend the file" }
        return count
    }

    /** Inputs a nibble, coalescing the high/low pair into one undo step. */
    suspend fun inputDigit(value: Int) = edit {
        require(value in 0..15)
        val state = state.value
        val offset = state.selection.start
        val count = if (state.nibble == 1 && coalesceOffset == offset) 1L else inputRemoval(1)
        checkLength(offset, count, 1)
        val old = if (count > 0 && offset < state.size) read(document, offset, 1)[0].toInt() and 255 else 0
        val byte = if (state.nibble == 0) (value shl 4) or (old and 15) else (old and 240) or value
        val inserted = append(BinaryBytes.owned(byteArrayOf(byte.toByte())))
        val removed = capture(document, offset, count)
        currentCoroutineContext().ensureActive()
        val after = if (state.nibble == 0) offset else offset + 1
        record(listOf(Change(offset, removed, inserted)), document.tree.replace(offset, count, inserted),
            BinarySelection(after), 1 - state.nibble, if (state.nibble == 0 || coalesceOffset == offset) offset else null)
    }

    /** Copies selected bytes into session scratch storage. */
    suspend fun copySelection() = edits.withLock {
        checkOpen()
        mutableState.update { it.copy(busy = true, error = null) }
        try {
            val range = state.value.selection
            val copied = capture(document, range.start, range.length)
            currentCoroutineContext().ensureActive()
            clipboard = copied
            mutableState.update { it.copy(clipboardSize = copied.size) }
        } finally { mutableState.update { it.copy(busy = false, progress = null) } }
    }

    /** Copies and removes the selection in one operation. */
    suspend fun cutSelection() = edit {
        cutLocked(state.value.selection)
    }

    private suspend fun cutLocked(range: BinarySelection) {
        if (range.length == 0L) return
        val removed = capture(document, range.start, range.length)
        currentCoroutineContext().ensureActive()
        clipboard = removed
        mutableState.update { it.copy(clipboardSize = removed.size) }
        record(listOf(Change(range.start, removed, null)), document.tree.replace(range.start, range.length, null), BinarySelection(range.start))
    }

    /** Pastes the immutable internal clipboard. */
    suspend fun paste() = edit {
        if (clipboard != null) replaceLocked(state.value.selection.start, inputRemoval(clipboard.size), clipboard)
    }

    /** Removes the selection. */
    suspend fun deleteSelection() = edit {
        val range = state.value.selection
        replaceLocked(range.start, range.length, null)
    }

    /** Repeats one byte over the selection without materializing the whole fill. */
    suspend fun fill(value: Int) = edit {
        fillLocked(state.value.selection, value)
    }

    private suspend fun fillLocked(range: BinarySelection, value: Int) {
        require(value in 0..255)
        val bytes = BinaryBytes.owned(ByteArray(minOf(options.pageSize.toLong(), range.length).toInt()) { value.toByte() })
        val block = append(bytes)
        var inserted: PieceTree? = null
        var remaining = range.length
        while (remaining > 0) {
            currentCoroutineContext().ensureActive()
            val amount = minOf(bytes.size.toLong(), remaining)
            inserted = join(inserted, block.split(amount).first)
            remaining -= amount
            yield()
        }
        replaceLocked(range.start, range.length, inserted)
    }

    /** Restores one atomic edit, including edits made before the last save. */
    suspend fun undo() = edit {
        val history = undo.lastOrNull() ?: return@edit
        var tree = document.tree
        for (change in history.changes.asReversed()) tree = tree.replace(change.offset, change.inserted.size, change.removed)
        undo.removeAt(undo.lastIndex)
        redo += history
        marker = history.beforeMarker
        coalesceOffset = null
        publish(tree, history.before)
    }

    /** Reapplies one undone edit. */
    suspend fun redo() = edit {
        val history = redo.lastOrNull() ?: return@edit
        var tree = document.tree
        for (change in history.changes) tree = tree.replace(change.offset, change.removed.size, change.inserted)
        redo.removeAt(redo.lastIndex)
        undo += history
        marker = history.afterMarker
        coalesceOffset = null
        publish(tree, history.after)
    }

    /** Saves atomically; edit history and pending changes survive failed commits. */
    suspend fun save() = edit {
        if (!state.value.dirty) return@edit
        mutableState.update { it.copy(saving = true) }
        try {
            val snapshot = document
            val request = BinarySaveRequest(snapshot.source, snapshot.tree.size, snapshot.tree.pieces().toList(), storage)
            // Once persistence starts, finish receiving its outcome before exposing a new baseline.
            withContext(NonCancellable) {
                val saved = checkNotNull(committer).commit(request)
                check(saved.id == snapshot.source.id && saved.size == request.size && saved.revision.isNotEmpty()) { "Invalid commit result" }
                document = Document(saved, pieceTree(BinaryPiece.Original(0, saved.size)), snapshot.generation)
                savedMarker = marker
                coalesceOffset = null
                pages.clear()
                publish(document.tree, state.value.selection)
            }
        } finally { mutableState.update { it.copy(saving = false) } }
    }

    private fun prefix(pattern: BinaryBytes): IntArray {
        require(pattern.size in 1..options.maxInputBytes) { "Invalid search pattern length" }
        val prefix = IntArray(pattern.size)
        for (i in 1 until pattern.size) {
            var j = prefix[i - 1]
            while (j > 0 && pattern[i] != pattern[j]) j = prefix[j - 1]
            if (pattern[i] == pattern[j]) j++
            prefix[i] = j
        }
        return prefix
    }

    private suspend fun scan(
        snapshot: Document, pattern: BinaryBytes, begin: Long, end: Long,
        overlapping: Boolean = false, searchToken: Long? = null, backwards: Boolean = false,
        match: suspend (Long) -> Boolean,
    ) {
        val needle = if (backwards) BinaryBytes.owned(ByteArray(pattern.size) { pattern[pattern.size - 1 - it] }) else pattern
        val prefix = prefix(needle)
        var position = if (backwards) end else begin
        var matched = 0
        while (if (backwards) position > begin else position < end) {
            currentCoroutineContext().ensureActive()
            if (searchToken != null && (searchToken != searchSequence.get() || snapshot.generation != document.generation))
                throw CancellationException("Superseded search")
            val pageBegin = if (backwards) maxOf(begin, (position - 1) / options.pageSize * options.pageSize) else position
            val pageEnd = if (backwards) position else position + minOf(options.pageSize.toLong(), end - position)
            val bytes = read(snapshot, pageBegin, (pageEnd - pageBegin).toInt())
            // Keep the partial match across page boundaries in either direction.
            for (step in 0 until bytes.size) {
                val index = if (backwards) bytes.size - 1 - step else step
                while (matched > 0 && bytes[index] != needle[matched]) matched = prefix[matched - 1]
                if (bytes[index] == needle[matched]) matched++
                if (matched == needle.size) {
                    val offset = if (backwards) pageBegin + index else pageBegin + index + 1 - needle.size
                    if (!match(offset)) return
                    matched = if (overlapping) prefix[matched - 1] else 0
                }
            }
            position = if (backwards) pageBegin else pageEnd
            mutableState.update {
                if (searchToken == null || (searchToken == searchSequence.get() && it.generation == snapshot.generation && !it.busy))
                    it.copy(progress = (if (backwards) end - position else position - begin).toFloat() / (end - begin)) else it
            }
            yield()
        }
    }

    /** Finds the next or previous match, wrapping once, without accumulating all results. */
    suspend fun find(pattern: BinaryBytes, backwards: Boolean = false): Long? = withContext(Dispatchers.Default) {
        checkOpen()
        prefix(pattern)
        val token = searchSequence.incrementAndGet()
        val snapshot = document
        val selected = state.value.selection
        mutableState.update { it.copy(searching = true, searchMiss = false) }
        try {
            var found: Long? = null
            if (backwards) {
                val end = selected.start + minOf(pattern.size - 1L, snapshot.tree.size - selected.start)
                scan(snapshot, pattern, 0, end, true, token, backwards = true) { found = it; false }
                if (found == null) scan(snapshot, pattern, selected.start, snapshot.tree.size, true, token, backwards = true) { found = it; false }
            } else {
                scan(snapshot, pattern, selected.end, snapshot.tree.size, true, token) { found = it; false }
                val end = selected.end + minOf(pattern.size - 1L, snapshot.tree.size - selected.end)
                if (found == null) scan(snapshot, pattern, 0, end, true, token) { if (it < selected.end) found = it; found == null }
            }
            currentCoroutineContext().ensureActive()
            edits.withLock {
                if (token == searchSequence.get() && document.generation == snapshot.generation) {
                    // Moving back to the high nibble starts a new input pair, even on a miss.
                    coalesceOffset = null
                    mutableState.update {
                        it.copy(selection = found?.let { offset -> BinarySelection(offset, offset + pattern.size) } ?: it.selection,
                            nibble = 0, searchMiss = found == null)
                    }
                }
            }
            found
        } catch (error: Exception) {
            if (token != searchSequence.get() || snapshot.generation != document.generation)
                throw CancellationException("Superseded search", error)
            throw error
        } finally {
            mutableState.update { if (token == searchSequence.get()) it.copy(searching = false, progress = null) else it }
        }
    }

    /** Replaces only a selection that still equals [pattern]. */
    suspend fun replaceMatch(pattern: BinaryBytes, replacement: BinaryBytes) = edit {
        prefix(pattern)
        val range = state.value.selection
        require(range.length == pattern.size.toLong() && read(document, range.start, pattern.size).toByteArray().contentEquals(pattern.toByteArray())) { "Selection is not the search match" }
        replaceLocked(range.start, range.length, appendAfterValidation(range.start, range.length, replacement))
    }

    /** Replaces all non-overlapping original matches as one undoable operation. */
    suspend fun replaceAll(pattern: BinaryBytes, replacement: BinaryBytes): Int = withContext(Dispatchers.Default) {
        edit { replaceAllLocked(pattern, replacement) }
    }

    private suspend fun replaceAllLocked(pattern: BinaryBytes, replacement: BinaryBytes): Int {
        val snapshot = document
        val removed = append(pattern)
        val inserted = append(replacement)
        var tree = snapshot.tree
        var delta = 0L
        val changes = ArrayList<Change>()
        scan(snapshot, pattern, 0, snapshot.tree.size) { originalOffset ->
            require(changes.size < options.maxChangesPerOperation) { "Too many replacements in one operation" }
            val offset = originalOffset + delta
            val nextSize = checkedSum(tree.size - pattern.size, replacement.size.toLong())
            require(nextSize <= checkNotNull(committer).maxSize) { "Backend content limit exceeded" }
            tree = tree.replace(offset, pattern.size.toLong(), inserted)
            changes += Change(offset, removed, inserted)
            delta += replacement.size.toLong() - pattern.size
            true
        }
        currentCoroutineContext().ensureActive()
        if (changes.isNotEmpty()) record(changes, tree, BinarySelection(changes.first().offset))
        return changes.size
    }

    /** Applies the confirmed target under the edit lock; intervening content changes reject it. */
    suspend fun applyConfirmed(confirmed: HexEditorAction.Confirmed) = withContext(Dispatchers.Default) {
        edit {
            check(document.generation == confirmed.target.generation) { "The document changed; confirm the operation again" }
            val range = confirmed.target.selection
            select(range)
            when (val action = confirmed.action) {
                HexEditorAction.Cut -> cutLocked(range)
                HexEditorAction.Delete -> replaceLocked(range.start, range.length, null)
                is HexEditorAction.Fill -> fillLocked(range, action.value)
                is HexEditorAction.ReplaceAll -> replaceAllLocked(action.pattern, action.replacement)
                else -> error("Unsupported confirmed action")
            }
            Unit
        }
    }

    /** Stops background work and releases scratch storage. Caller must also stop direct suspend calls. */
    suspend fun close() {
        closed = true
        job.cancelAndJoin()
        withContext(NonCancellable) {
            edits.withLock { pages.clear(); storage.close(); clipboard = null; undo.clear(); redo.clear() }
        }
    }
}
