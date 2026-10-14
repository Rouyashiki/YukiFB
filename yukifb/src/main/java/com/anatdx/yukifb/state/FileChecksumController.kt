package com.anatdx.yukifb.state

import com.anatdx.yukifb.backend.FileChecksumCapability
import com.anatdx.yukifb.model.FileByteProgress
import com.anatdx.yukifb.model.FileChecksumAlgorithm
import com.anatdx.yukifb.model.FileChecksumValue
import com.anatdx.yukifb.model.FileEntry
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Outcome of comparing an explicitly entered checksum with the current completed batch. */
enum class FileChecksumComparison { EMPTY, INVALID, NOT_COMPUTED, MATCH, DIFFERENT }

/** Immutable checksum presentation, including selection, progress, results and comparison. */
data class FileChecksumUiState(
    val entry: FileEntry,
    val algorithms: List<FileChecksumAlgorithm>,
    val selectedIds: Set<String>,
    val running: Boolean = false,
    val cancelling: Boolean = false,
    val progress: FileByteProgress = FileByteProgress(),
    val results: List<FileChecksumValue> = emptyList(),
    val comparisonInput: String = "",
    val error: Throwable? = null,
) {
    private val normalizedInput: String get() = comparisonInput.filterNot(Char::isWhitespace)

    /** IDs of calculated algorithms whose values equal the entered hexadecimal value. */
    val matchingIds: Set<String> get() = results.filter { it.hex.equals(normalizedInput, ignoreCase = true) }.map { it.algorithmId }.toSet()

    /** Uncomputed algorithms never produce a mismatch indication. */
    val comparison: FileChecksumComparison get() {
        val text = normalizedInput
        return when {
            text.isEmpty() -> FileChecksumComparison.EMPTY
            !text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } || algorithms.none { it.hexLength == text.length } -> FileChecksumComparison.INVALID
            results.none { it.hex.length == text.length } -> FileChecksumComparison.NOT_COMPUTED
            matchingIds.isNotEmpty() -> FileChecksumComparison.MATCH
            else -> FileChecksumComparison.DIFFERENT
        }
    }
}

/** Intents accepted by the headless checksum controller. */
sealed interface FileChecksumAction {
    /** Toggles one supported algorithm and invalidates the preceding batch. */
    data class Toggle(val algorithmId: String) : FileChecksumAction
    /** Starts the explicitly selected batch, unless a prior operation is still cleaning up. */
    data object Calculate : FileChecksumAction
    /** Cancels and discards the active batch. */
    data object Cancel : FileChecksumAction
    /** Sets the value to compare; this does not calculate or access a clipboard. */
    data class Compare(val text: String) : FileChecksumAction
}

/**
 * Coordinates a checksum dialog without Android dependencies. Dispatch actions on the owning
 * scope's thread; host progress may arrive from worker threads. Keep the controller alive until
 * [state] stops reporting running after cancellation, so transport cleanup is not overlapped.
 */
class FileChecksumController(
    entry: FileEntry,
    private val capability: FileChecksumCapability,
    private val scope: CoroutineScope,
    initiallySelected: Set<String> = emptySet(),
) {
    private val algorithms = capability.algorithms(entry).toList().also { values ->
        require(values.map { it.id }.distinct().size == values.size)
    }
    private val mutableState = MutableStateFlow(FileChecksumUiState(entry, algorithms, initiallySelected.intersect(algorithms.map { it.id }.toSet())))
    private val generation = AtomicLong()
    @Volatile private var active: Job? = null

    /** Current immutable presentation. */
    val state: StateFlow<FileChecksumUiState> = mutableState.asStateFlow()

    /** Applies a UI intent; no computation starts automatically. */
    fun dispatch(action: FileChecksumAction) {
        when (action) {
            is FileChecksumAction.Toggle -> if (algorithms.any { it.id == action.algorithmId }) {
                cancel()
                mutableState.update { it.copy(selectedIds = if (action.algorithmId in it.selectedIds) it.selectedIds - action.algorithmId else it.selectedIds + action.algorithmId) }
            }
            FileChecksumAction.Calculate -> calculate()
            FileChecksumAction.Cancel -> cancel()
            is FileChecksumAction.Compare -> mutableState.update { it.copy(comparisonInput = action.text) }
        }
    }

    /** Cancels the batch and suppresses results even from an uncooperative host implementation. */
    fun cancel() {
        generation.incrementAndGet()
        mutableState.update { it.copy(results = emptyList(), error = null, cancelling = it.running) }
        active?.cancel()
    }

    private fun calculate() {
        val snapshot = state.value
        if (active != null || snapshot.selectedIds.isEmpty()) return
        val token = generation.incrementAndGet()
        mutableState.update { it.copy(running = true, cancelling = false, results = emptyList(), error = null, progress = FileByteProgress()) }
        val task = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val values = capability.calculate(snapshot.entry.id, snapshot.selectedIds) { progress ->
                    mutableState.update { if (generation.get() == token) it.copy(progress = progress) else it }
                }
                require(values.size == snapshot.selectedIds.size && values.map { it.algorithmId }.toSet() == snapshot.selectedIds) { "Incomplete checksum response" }
                val normalized = algorithms.filter { it.id in snapshot.selectedIds }.map { algorithm ->
                    val value = values.single { it.algorithmId == algorithm.id }
                    require(value.hex.length == algorithm.hexLength && value.hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "Invalid checksum response" }
                    value.copy(hex = value.hex.uppercase(Locale.ROOT))
                }
                mutableState.update { if (generation.get() == token) it.copy(results = normalized) else it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { if (generation.get() == token) it.copy(error = failure) else it }
            }
        }
        active = task
        task.invokeOnCompletion {
            if (active === task) {
                active = null
                mutableState.update { it.copy(running = false, cancelling = false) }
            }
        }
        task.start()
    }
}
