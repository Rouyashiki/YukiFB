package com.anatdx.yukifb.state

import com.anatdx.yukifb.backend.FileChecksumCapability
import com.anatdx.yukifb.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FileChecksumControllerTest {
    private val entry = FileEntry(EntryId("opaque"), null, "file", FileEntryType.REGULAR_FILE)
    private val algorithms = listOf(FileChecksumAlgorithm("a", "A", 8), FileChecksumAlgorithm("b", "B", 8))
    private fun host(block: suspend (Set<String>, (FileByteProgress) -> Unit) -> List<FileChecksumValue>) = object : FileChecksumCapability {
        override fun algorithms(entry: FileEntry) = algorithms
        override suspend fun calculate(entryId: EntryId, algorithmIds: Set<String>, onProgress: (FileByteProgress) -> Unit): List<FileChecksumValue> {
            assertEquals(entry.id, entryId)
            return block(algorithmIds, onProgress)
        }
    }

    @Test fun waitsForExplicitCalculationAndCoalescesRepeatedClicks() = runTest {
        var calls = 0
        val controller = FileChecksumController(entry, host { ids, progress ->
            calls++
            progress(FileByteProgress(1, 1))
            ids.map { FileChecksumValue(it, "00abcdef") }
        }, this, setOf("a", "unknown"))
        runCurrent()
        assertEquals(0, calls)
        assertEquals(setOf("a"), controller.state.value.selectedIds)
        repeat(2) { controller.dispatch(FileChecksumAction.Calculate) }
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals("00ABCDEF", controller.state.value.results.single().hex)
        assertEquals(FileByteProgress(1, 1), controller.state.value.progress)
        assertFalse(controller.state.value.running)
    }

    @Test fun emptySelectionNeverInvokesHostAndUnknownToggleIsIgnored() = runTest {
        val controller = FileChecksumController(entry, host { _, _ -> error("Unexpected calculation") }, this)
        controller.dispatch(FileChecksumAction.Toggle("unknown"))
        controller.dispatch(FileChecksumAction.Calculate)
        advanceUntilIdle()
        assertTrue(controller.state.value.selectedIds.isEmpty())
        assertFalse(controller.state.value.running)
    }

    @Test fun comparisonChecksAllResultsAndNormalizesWhitespaceAndCase() {
        val state = FileChecksumUiState(entry, algorithms, setOf("a", "b"), results = listOf(
            FileChecksumValue("a", "00ABCDEF"), FileChecksumValue("b", "00ABCDEF"),
        ))
        assertEquals(FileChecksumComparison.EMPTY, state.comparison)
        val match = state.copy(comparisonInput = " 00 ab\ncd\tef ")
        assertEquals(FileChecksumComparison.MATCH, match.comparison)
        assertEquals(setOf("a", "b"), match.matchingIds)
        assertEquals(FileChecksumComparison.DIFFERENT, state.copy(comparisonInput = "00000000").comparison)
        for (invalid in listOf("xyz", "abc", "0000000G")) assertEquals(FileChecksumComparison.INVALID, state.copy(comparisonInput = invalid).comparison)
        assertEquals(FileChecksumComparison.NOT_COMPUTED, match.copy(results = emptyList()).comparison)
    }

    @Test fun failureCanRetryAndMalformedBatchesNeverPublishPartialResults() = runTest {
        val responses = listOf(
            emptyList(), listOf(FileChecksumValue("a", "00000000"), FileChecksumValue("a", "00000000")),
            listOf(FileChecksumValue("a", "0")), listOf(FileChecksumValue("a", "0000000Z")),
            listOf(FileChecksumValue("a", "12345678")),
        ).iterator()
        val controller = FileChecksumController(entry, host { _, _ -> responses.next() }, this, setOf("a"))
        repeat(4) {
            controller.dispatch(FileChecksumAction.Calculate)
            advanceUntilIdle()
            assertNotNull(controller.state.value.error)
            assertTrue(controller.state.value.results.isEmpty())
        }
        controller.dispatch(FileChecksumAction.Calculate)
        advanceUntilIdle()
        assertNull(controller.state.value.error)
        assertEquals("12345678", controller.state.value.results.single().hex)
        controller.dispatch(FileChecksumAction.Toggle("b"))
        assertTrue(controller.state.value.results.isEmpty())
        assertEquals(setOf("a", "b"), controller.state.value.selectedIds)
    }

    @Test fun cancellationWaitsForCleanupAndRejectsLateProgressAndResults() = runTest {
        val started = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        var calls = 0
        val controller = FileChecksumController(entry, host { _, progress ->
            calls++
            started.complete(Unit)
            withContext(NonCancellable) { finishCleanup.await() }
            progress(FileByteProgress(99, 100))
            listOf(FileChecksumValue("a", "12345678"))
        }, this, setOf("a"))
        controller.dispatch(FileChecksumAction.Calculate)
        started.await()
        controller.dispatch(FileChecksumAction.Toggle("b"))
        controller.dispatch(FileChecksumAction.Calculate)
        assertTrue(controller.state.value.cancelling)
        assertTrue(controller.state.value.running)
        finishCleanup.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertFalse(controller.state.value.running)
        assertTrue(controller.state.value.results.isEmpty())
        assertEquals(FileByteProgress(), controller.state.value.progress)
        assertNull(controller.state.value.error)
    }

    @Test fun cancellationBeforeLaunchDoesNotLeaveBusyState() = runTest {
        val controller = FileChecksumController(entry, host { _, _ -> error("Must not start") }, this, setOf("a"))
        controller.dispatch(FileChecksumAction.Calculate)
        controller.cancel()
        advanceUntilIdle()
        assertFalse(controller.state.value.running)
        assertFalse(controller.state.value.cancelling)
    }

    @Test fun hostFailureIsExposedAndRecalculationClearsPreviousResults() = runTest {
        var fail = false
        val controller = FileChecksumController(entry, host { _, _ ->
            if (fail) throw FileContentChangedException()
            listOf(FileChecksumValue("a", "12345678"))
        }, this, setOf("a"))
        controller.dispatch(FileChecksumAction.Calculate)
        advanceUntilIdle()
        fail = true
        controller.dispatch(FileChecksumAction.Calculate)
        assertTrue(controller.state.value.results.isEmpty())
        advanceUntilIdle()
        assertIs<FileContentChangedException>(controller.state.value.error)
    }
}
