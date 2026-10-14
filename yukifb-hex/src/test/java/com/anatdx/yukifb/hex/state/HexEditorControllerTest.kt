package com.anatdx.yukifb.hex.state

import com.anatdx.yukifb.hex.backend.*
import com.anatdx.yukifb.model.EntryId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.*

class HexEditorControllerTest {
    private class Source(private val bytes: ByteArray, override val revision: String = "1") : BinaryDataSource {
        override val id = EntryId("opaque")
        override val size = bytes.size.toLong()
        override suspend fun read(offset: Long, length: Int): BinaryBytes = BinaryBytes.copyOf(bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + length)))
    }

    private val persist = BinaryCommitter { request ->
        val bytes = java.io.ByteArrayOutputStream()
        for (piece in request.pieces) {
            val chunk = when (piece) {
                is BinaryPiece.Original -> request.source.read(piece.offset, piece.length.toInt())
                is BinaryPiece.Inserted -> request.readInserted(piece.offset, piece.length.toInt())
            }
            bytes.write(chunk.toByteArray())
        }
        Source(bytes.toByteArray(), (request.source.revision.toInt() + 1).toString())
    }

    @Test fun randomizedEditsMatchIndependentByteListIncludingSaveAndUndo() = runTest {
        val random = Random(314159)
        val initial = ByteArray(97) { it.toByte() }
        val controller = HexEditorController(Source(initial), this, committer = persist)
        var expected = initial.toList()
        val history = ArrayList<List<Byte>>()
        repeat(500) { step ->
            if (history.isNotEmpty() && step % 7 == 0) {
                val after = expected
                controller.undo()
                expected = history.removeAt(history.lastIndex)
                assertContentEquals(expected.toByteArray(), controller.read(0, expected.size).toByteArray())
                controller.redo()
                history += expected
                expected = after
            } else {
                val offset = random.nextInt(expected.size + 1)
                val count = random.nextInt(expected.size - offset + 1)
                val inserted = random.nextBytes(random.nextInt(1, 30))
                history += expected
                expected = expected.take(offset) + inserted.toList() + expected.drop(offset + count)
                controller.replace(offset.toLong(), count.toLong(), BinaryBytes.copyOf(inserted))
            }
            if (step % 13 == 0) { controller.save(); assertFalse(controller.state.value.dirty) }
            assertContentEquals(expected.toByteArray(), controller.read(0, expected.size).toByteArray(), "step $step")
        }
        controller.close()
    }

    @Test fun savingPreservesUndoAndSavedHistoryPosition() = runTest {
        val controller = HexEditorController(Source(byteArrayOf(1, 2, 3)), this, committer = persist)
        controller.replace(1, 1, BinaryBytes.copyOf(byteArrayOf(8, 9)))
        controller.save()
        controller.undo()
        assertContentEquals(byteArrayOf(1, 2, 3), controller.read(0, 100).toByteArray())
        assertTrue(controller.state.value.dirty)
        controller.redo()
        assertContentEquals(byteArrayOf(1, 8, 9, 3), controller.read(0, 100).toByteArray())
        assertFalse(controller.state.value.dirty)
        controller.close()
    }

    @Test fun nibblePairIsOneUndoAndInsertDoesNotConsumeNextByte() = runTest {
        val controller = HexEditorController(Source(byteArrayOf(0x12, 0x34)), this, committer = persist)
        controller.inputDigit(10)
        controller.inputDigit(11)
        assertContentEquals(byteArrayOf(0xab.toByte(), 0x34), controller.read(0, 10).toByteArray())
        controller.undo()
        assertContentEquals(byteArrayOf(0x12, 0x34), controller.read(0, 10).toByteArray())
        controller.dispatch(HexEditorAction.Mode(HexInputMode.Insert)).join()
        controller.inputDigit(15)
        controller.inputDigit(14)
        assertContentEquals(byteArrayOf(0xfe.toByte(), 0x12, 0x34), controller.read(0, 10).toByteArray())
        controller.undo()
        assertContentEquals(byteArrayOf(0x12, 0x34), controller.read(0, 10).toByteArray())
        controller.close()
    }

    @Test fun largeOffsetsDoNotMaterializeDocumentAndWindowCrossesPageBoundary() = runTest {
        var requested = 0L
        val source = object : BinaryDataSource {
            override val id = EntryId("large")
            override val revision = "stable"
            override val size = 128L * 1024 * 1024 * 1024
            override suspend fun read(offset: Long, length: Int): BinaryBytes {
                requested += length
                assertTrue(length <= 65536)
                return BinaryBytes.copyOf(ByteArray(length) { ((offset + it) and 255).toByte() })
            }
        }
        val controller = HexEditorController(source, this)
        val offset = 96L * 1024 * 1024 * 1024 - 4
        val bytes = controller.read(offset, 16)
        assertContentEquals(ByteArray(16) { ((offset + it) and 255).toByte() }, bytes.toByteArray())
        controller.select(BinarySelection(offset))
        assertEquals(offset, controller.state.value.selection.caret)
        assertEquals(offset, controller.find(BinaryBytes.copyOf(ByteArray(4) { ((offset + it) and 255).toByte() })))
        assertTrue(requested <= 2 * 65536)
        controller.close()
    }

    @Test fun searchMatchStartsNewNibblePairWithSeparateUndoHistory() = runTest {
        for (backwards in listOf(false, true)) {
            val controller = HexEditorController(Source(byteArrayOf()), this, committer = persist)
            try {
                controller.dispatch(HexEditorAction.Mode(HexInputMode.Insert)).join()
                controller.inputDigit(0xA)
                assertEquals(0L, controller.find(BinaryBytes.copyOf(byteArrayOf(0xA0.toByte())), backwards))
                assertEquals(BinarySelection(0, 1), controller.state.value.selection)
                controller.inputDigit(0xB)
                controller.inputDigit(0xC)
                assertContentEquals(byteArrayOf(0xBC.toByte()), controller.read(0, 16).toByteArray())
                controller.undo()
                assertContentEquals(byteArrayOf(0xA0.toByte()), controller.read(0, 16).toByteArray())
                controller.undo()
                assertEquals(0L, controller.state.value.size)
                controller.redo()
                controller.redo()
                assertContentEquals(byteArrayOf(0xBC.toByte()), controller.read(0, 16).toByteArray())
            } finally { controller.close() }
        }
    }

    @Test fun searchMissEndsPendingNibblePairBeforeInsertion() = runTest {
        for (backwards in listOf(false, true)) {
            val controller = HexEditorController(Source(byteArrayOf()), this, committer = persist)
            try {
                controller.dispatch(HexEditorAction.Mode(HexInputMode.Insert)).join()
                controller.inputDigit(0xA)
                assertNull(controller.find(BinaryBytes.copyOf(byteArrayOf(0xFF.toByte())), backwards))
                assertTrue(controller.state.value.searchMiss)
                controller.inputDigit(0xB)
                controller.inputDigit(0xC)
                assertContentEquals(byteArrayOf(0xBC.toByte(), 0xA0.toByte()), controller.read(0, 16).toByteArray())
                controller.undo()
                assertContentEquals(byteArrayOf(0xA0.toByte()), controller.read(0, 16).toByteArray())
                controller.redo()
                assertContentEquals(byteArrayOf(0xBC.toByte(), 0xA0.toByte()), controller.read(0, 16).toByteArray())
            } finally { controller.close() }
        }
    }

    @Test fun searchCrossesPagesAndReplaceAllDoesNotRescanInsertedMatches() = runTest {
        val bytes = ByteArray(1023) { 0 } + "abab abab".toByteArray()
        val controller = HexEditorController(Source(bytes), this, committer = persist, options = HexEditorOptions(pageSize = 1024, cacheBytes = 2048))
        val pattern = BinaryBytes.copyOf("abab".toByteArray())
        assertEquals(1023L, controller.find(pattern))
        assertEquals(1028L, controller.find(pattern))
        assertEquals(2, controller.replaceAll(pattern, BinaryBytes.copyOf("abababab".toByteArray())))
        assertEquals(bytes.size + 8L, controller.state.value.size)
        controller.save()
        controller.undo()
        assertContentEquals(bytes, controller.read(0, bytes.size).toByteArray())
        controller.close()
    }

    @Test fun backwardsSearchNearHugeCaretAndWrapReadOnlyNearbyPages() = runTest {
        var requested = 0L
        val source = object : BinaryDataSource {
            override val id = EntryId("reverse-large")
            override val revision = "1"
            override val size = 128L * 1024 * 1024 * 1024
            override suspend fun read(offset: Long, length: Int): BinaryBytes {
                assertTrue(offset >= size - 65536, "A nearby previous match must not scan the file prefix")
                requested += length
                return BinaryBytes.copyOf(ByteArray(length) { if (offset + it == size - 2) 0x7f else 0 })
            }
        }
        val controller = HexEditorController(source, this)
        try {
            val pattern = BinaryBytes.copyOf(byteArrayOf(0x7f))
            controller.select(BinarySelection(source.size - 1))
            assertEquals(source.size - 2, controller.find(pattern, backwards = true))
            assertTrue(requested <= 65536, "Read $requested bytes for a match one byte before the caret")
            controller.select(BinarySelection(0))
            assertEquals(source.size - 2, controller.find(pattern, backwards = true))
            assertTrue(requested <= 65536, "Wrapping must reuse the cached end page")
        } finally { controller.close() }
    }

    @Test fun backwardsSearchMatchesReferenceAcrossPagesOverlapsAndCaretBoundaries() = runTest {
        val bytes = ByteArray(5100) { (it % 7).toByte() }
        val controller = HexEditorController(Source(bytes), this, options = HexEditorOptions(pageSize = 1024, cacheBytes = 2048))
        try {
            // Long patterns span multiple pages; the periodic bytes also create overlapping matches.
            for (length in listOf(1, 3, 1031, 2051)) {
                val pattern = bytes.copyOfRange(1023, 1023 + length)
                val matches = (0..bytes.size - length).filter { start -> pattern.indices.all { bytes[start + it] == pattern[it] } }
                for (caret in listOf(0, 1, 1023, 1024, 2048, bytes.size)) {
                    controller.select(BinarySelection(caret.toLong()))
                    val expected = matches.lastOrNull { it < caret } ?: matches.lastOrNull()
                    assertEquals(expected?.toLong(), controller.find(BinaryBytes.copyOf(pattern), backwards = true), "length=$length caret=$caret")
                }
            }
            assertNull(controller.find(BinaryBytes.copyOf(byteArrayOf(0x7f)), backwards = true))
        } finally { controller.close() }
    }

    @Test fun cancelledBackwardsSearchCannotPublishSelection() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val source = object : BinaryDataSource {
            override val id = EntryId("reverse-cancel")
            override val revision = "1"
            override val size = 4096L
            override suspend fun read(offset: Long, length: Int): BinaryBytes {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                return BinaryBytes.copyOf(ByteArray(length) { 1 })
            }
        }
        val controller = HexEditorController(source, this)
        try {
            controller.select(BinarySelection(source.size))
            val search = controller.dispatch(HexEditorAction.Find(BinaryBytes.copyOf(byteArrayOf(1)), backwards = true))
            entered.await()
            controller.dispatch(HexEditorAction.CancelSearch).join()
            release.complete(Unit)
            search.join()
            assertEquals(BinarySelection(source.size), controller.state.value.selection)
            assertFalse(controller.state.value.searching)
            assertNull(controller.state.value.error)
        } finally { release.complete(Unit); controller.close() }
    }

    @Test fun storageFailureAndCancellationDoNotPublishPartialEdits() = runTest {
        val memory = MemoryBinaryEditStorage()
        var fail = false
        val storage = object : BinaryEditStorage by memory {
            override suspend fun append(bytes: BinaryBytes): Long {
                if (fail) throw CancellationException("cancelled")
                fail = true
                return memory.append(bytes)
            }
        }
        val controller = HexEditorController(Source(byteArrayOf(1, 2, 3)), this, storage, persist)
        assertFailsWith<CancellationException> { controller.replace(0, 2, BinaryBytes.copyOf(byteArrayOf(9))) }
        assertContentEquals(byteArrayOf(1, 2, 3), controller.read(0, 3).toByteArray())
        assertFalse(controller.state.value.dirty)
        assertFalse(controller.state.value.busy)
        controller.close()
    }

    @Test fun failedCommitKeepsPendingContentAndUndo() = runTest {
        val controller = HexEditorController(Source(byteArrayOf(1)), this, committer = BinaryCommitter { throw BinaryRevisionConflictException() })
        controller.replace(0, 1, BinaryBytes.copyOf(byteArrayOf(9)))
        assertFailsWith<BinaryRevisionConflictException> { controller.save() }
        assertTrue(controller.state.value.dirty)
        assertContentEquals(byteArrayOf(9), controller.read(0, 1).toByteArray())
        controller.undo()
        assertContentEquals(byteArrayOf(1), controller.read(0, 1).toByteArray())
        controller.close()
    }

    @Test fun scratchExhaustionLeavesDocumentAndHistoryUnchanged() = runTest {
        val controller = HexEditorController(Source(byteArrayOf(1, 2, 3)), this,
            storage = MemoryBinaryEditStorage(maxBytes = 2), committer = persist)
        assertFailsWith<IllegalArgumentException> { controller.replace(0, 3, BinaryBytes.copyOf(byteArrayOf(9))) }
        assertContentEquals(byteArrayOf(1, 2, 3), controller.read(0, 3).toByteArray())
        assertFalse(controller.state.value.dirty)
        assertFalse(controller.state.value.canUndo)
        assertFalse(controller.state.value.busy)
        controller.close()
    }

    @Test fun searchWrapsToOnlyMatchAndFindsMatchesCrossingCaret() = runTest {
        val controller = HexEditorController(Source("xxabcdef".toByteArray()), this)
        val pattern = BinaryBytes.copyOf("abc".toByteArray())
        assertEquals(2L, controller.find(pattern))
        assertEquals(2L, controller.find(pattern))
        assertEquals(2L, controller.find(pattern, backwards = true))
        controller.select(BinarySelection(3))
        assertEquals(2L, controller.find(pattern))
        controller.select(BinarySelection(3))
        assertEquals(2L, controller.find(pattern, backwards = true))
        controller.close()
    }

    @Test fun supersededSearchCannotPublishLateSelection() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val source = object : BinaryDataSource {
            override val id = EntryId("slow")
            override val revision = "1"
            override val size = 3L
            override suspend fun read(offset: Long, length: Int): BinaryBytes {
                if (reads.incrementAndGet() == 1) {
                    entered.complete(Unit)
                    withContext(NonCancellable) { release.await() }
                }
                return BinaryBytes.copyOf(byteArrayOf(1, 2, 3).copyOfRange(offset.toInt(), offset.toInt() + length))
            }
        }
        val controller = HexEditorController(source, this)
        val first = controller.dispatch(HexEditorAction.Find(BinaryBytes.copyOf(byteArrayOf(1))))
        entered.await()
        controller.dispatch(HexEditorAction.Find(BinaryBytes.copyOf(byteArrayOf(3)))).join()
        assertEquals(BinarySelection(2, 3), controller.state.value.selection)
        release.complete(Unit)
        first.join()
        assertEquals(BinarySelection(2, 3), controller.state.value.selection)
        assertFalse(controller.state.value.searching)
        assertNull(controller.state.value.error)
        controller.close()
    }

    @Test fun emptyDocumentSupportsInsertionAndDeletionToZero() = runTest {
        val controller = HexEditorController(Source(byteArrayOf()), this, committer = persist)
        assertFailsWith<IllegalArgumentException> { controller.inputDigit(1) }
        controller.dispatch(HexEditorAction.Mode(HexInputMode.Insert)).join()
        controller.inputDigit(1)
        controller.inputDigit(2)
        controller.select(BinarySelection(0, 1))
        controller.deleteSelection()
        controller.save()
        assertEquals(0L, controller.state.value.size)
        controller.undo()
        assertContentEquals(byteArrayOf(0x12), controller.read(0, 10).toByteArray())
        controller.close()
    }

    @Test fun overwriteSelectionNeverResizesOrExtendsWhileInsertReplacesSelection() = runTest {
        val controller = HexEditorController(Source(byteArrayOf(1, 2, 3)), this, committer = persist)
        controller.select(BinarySelection(1, 3))
        controller.input(BinaryBytes.copyOf(byteArrayOf(9)))
        assertContentEquals(byteArrayOf(1, 9, 3), controller.read(0, 4).toByteArray())
        controller.select(BinarySelection(2, 3))
        assertFailsWith<IllegalArgumentException> { controller.input(BinaryBytes.copyOf(byteArrayOf(7, 8))) }
        assertEquals(3L, controller.state.value.size)
        controller.dispatch(HexEditorAction.Mode(HexInputMode.Insert)).join()
        controller.select(BinarySelection(1, 3))
        controller.input(BinaryBytes.copyOf(byteArrayOf(4, 5, 6)))
        assertContentEquals(byteArrayOf(1, 4, 5, 6), controller.read(0, 8).toByteArray())
        controller.undo()
        assertContentEquals(byteArrayOf(1, 9, 3), controller.read(0, 4).toByteArray())
        controller.close()
    }
}
