package com.anatdx.yukifb.hex.state

import com.anatdx.yukifb.hex.backend.*
import com.anatdx.yukifb.model.EntryId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class HexEditConfirmationTest {
    private val original = byteArrayOf(10, 20, 30, 40)
    private val actions = listOf(
        HexEditorAction.Delete,
        HexEditorAction.Cut,
        HexEditorAction.Fill(99),
        HexEditorAction.ReplaceAll(BinaryBytes.copyOf(byteArrayOf(10)), BinaryBytes.copyOf(byteArrayOf(99))),
    )

    private class Source(private val beforeRead: suspend () -> Unit = {}) : BinaryDataSource {
        override val id = EntryId("confirmation")
        override val revision = "1"
        override val size = 4L
        override suspend fun read(offset: Long, length: Int): BinaryBytes {
            beforeRead()
            return BinaryBytes.copyOf(byteArrayOf(10, 20, 30, 40).copyOfRange(offset.toInt(), offset.toInt() + length))
        }
    }

    @Test fun lateSearchCannotRetargetConfirmedEdits() = runTest {
        for (action in actions) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val source = Source { entered.complete(Unit); release.await() }
            val editor = HexEditorController(source, this, committer = BinaryCommitter { source })
            try {
                editor.select(BinarySelection(0, 1))
                val search = editor.dispatch(HexEditorAction.Find(BinaryBytes.copyOf(byteArrayOf(30))))
                entered.await()
                val confirmation = HexEditorAction.Confirmed(action, HexEditTarget(editor.state.value.selection, editor.state.value.generation))
                release.complete(Unit)
                search.join()
                assertEquals(BinarySelection(2, 3), editor.state.value.selection)
                editor.dispatch(confirmation).join()
                assertNull(editor.state.value.error)
                val expected = if (action == HexEditorAction.Delete || action == HexEditorAction.Cut) byteArrayOf(20, 30, 40) else byteArrayOf(99, 20, 30, 40)
                assertContentEquals(expected, editor.read(0, 4).toByteArray())
                if (action == HexEditorAction.Cut) assertEquals(1L, editor.state.value.clipboardSize)
                editor.undo()
                assertContentEquals(original, editor.read(0, 4).toByteArray())
                assertEquals(BinarySelection(0, 1), editor.state.value.selection)
                editor.redo()
                assertContentEquals(expected, editor.read(0, 4).toByteArray())
            } finally { release.complete(Unit); editor.close() }
        }
    }

    @Test fun changedContentRejectsEveryConfirmedActionWithoutAnotherUndoStep() = runTest {
        for (action in actions) {
            val source = Source()
            val editor = HexEditorController(source, this, committer = BinaryCommitter { source })
            try {
                val confirmation = HexEditorAction.Confirmed(action, HexEditTarget(BinarySelection(0, 1), 0))
                editor.replace(0, 1, BinaryBytes.copyOf(byteArrayOf(55)))
                editor.dispatch(confirmation).join()
                assertNotNull(editor.state.value.error)
                assertContentEquals(byteArrayOf(55, 20, 30, 40), editor.read(0, 4).toByteArray())
                assertEquals(0L, editor.state.value.clipboardSize)
                editor.undo()
                assertContentEquals(original, editor.read(0, 4).toByteArray())
                assertFalse(editor.state.value.canUndo)
            } finally { editor.close() }
        }
    }

    @Test fun queuedConfirmationChecksGenerationAfterAcquiringTheEditLock() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val memory = MemoryBinaryEditStorage()
        val storage = object : BinaryEditStorage by memory {
            override suspend fun append(bytes: BinaryBytes): Long {
                entered.complete(Unit)
                release.await()
                return memory.append(bytes)
            }
        }
        val source = Source()
        val editor = HexEditorController(source, this, storage, BinaryCommitter { source })
        try {
            val confirmation = HexEditorAction.Confirmed(HexEditorAction.Delete, HexEditTarget(BinarySelection(0, 1), 0))
            val first = editor.dispatch(HexEditorAction.Input(BinaryBytes.copyOf(byteArrayOf(55))))
            entered.await()
            val queued = editor.dispatch(confirmation)
            release.complete(Unit)
            first.join()
            queued.join()
            assertNotNull(editor.state.value.error)
            assertContentEquals(byteArrayOf(55, 20, 30, 40), editor.read(0, 4).toByteArray())
        } finally { release.complete(Unit); editor.close() }
    }
}
