package com.anatdx.yukifb.state

import com.anatdx.yukifb.backend.DeleteEntriesCapability
import com.anatdx.yukifb.backend.FileBrowserBackend
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.FileEntryType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserNavigationTest {
    @Test
    fun directoryAndBreadcrumbsCommitTogetherAfterLoading() = runTest {
        val backend = DelayedBackend()
        val controller = FileBrowserController(backend, backend.root, backgroundScope)
        controller.dispatch(FileBrowserAction.NavigateTo(backend.first))
        assertTrue(controller.state.value.isLoading)
        assertEquals(backend.root, controller.state.value.currentDirectory)
        assertEquals(listOf(backend.first, backend.second), controller.state.value.entries)
        backend.requests.removeFirst().resume(emptyList())
        runCurrent()
        assertEquals(backend.first, controller.state.value.currentDirectory)
        assertEquals(listOf(backend.root, backend.first), controller.state.value.breadcrumbs)
        assertFalse(controller.state.value.isLoading)
    }

    @Test
    fun failedNavigationKeepsTheSourceAndRetryLoadsTheFailedTarget() = runTest {
        val backend = DelayedBackend()
        val controller = FileBrowserController(backend, backend.root, backgroundScope)
        controller.dispatch(FileBrowserAction.NavigateTo(backend.first))
        backend.requests.removeFirst().resumeWithException(IllegalStateException("unavailable"))
        runCurrent()
        assertEquals(backend.root, controller.state.value.currentDirectory)
        assertNotNull(controller.state.value.error)
        controller.dispatch(FileBrowserAction.Refresh)
        assertNull(controller.state.value.error)
        backend.requests.removeFirst().resume(emptyList())
        runCurrent()
        assertEquals(backend.first, controller.state.value.currentDirectory)
    }

    @Test
    fun backendIgnoringCancellationCannotCommitAnOlderRequest() = runTest {
        val backend = DelayedBackend()
        val controller = FileBrowserController(backend, backend.root, backgroundScope)
        controller.dispatch(FileBrowserAction.NavigateTo(backend.first))
        val old = backend.requests.removeFirst()
        controller.dispatch(FileBrowserAction.NavigateTo(backend.second))
        backend.requests.removeFirst().resume(emptyList())
        runCurrent()
        old.resume(listOf(backend.first))
        runCurrent()
        assertEquals(backend.second, controller.state.value.currentDirectory)
        assertEquals(emptyList(), controller.state.value.entries)
        assertFalse(controller.state.value.isLoading)
    }

    @Test
    fun sameDirectoryRefreshAlsoRejectsAnOutOfOrderResponse() = runTest {
        val backend = DelayedBackend()
        val controller = FileBrowserController(backend, backend.root, backgroundScope)
        controller.dispatch(FileBrowserAction.NavigateTo(backend.first))
        backend.requests.removeFirst().resume(emptyList())
        runCurrent()
        controller.dispatch(FileBrowserAction.Refresh)
        val old = backend.requests.removeFirst()
        controller.dispatch(FileBrowserAction.Refresh)
        backend.requests.removeFirst().resume(listOf(backend.second))
        runCurrent()
        old.resumeWithException(IllegalStateException("stale failure"))
        runCurrent()
        assertEquals(listOf(backend.second), controller.state.value.entries)
        assertNull(controller.state.value.error)
    }

    @Test
    fun mutationInvalidatesPendingNavigation() = runTest {
        val backend = DelayedBackend()
        val controller = FileBrowserController(backend, backend.root, backgroundScope)
        controller.dispatch(FileBrowserAction.ToggleSelection(backend.second.id))
        controller.dispatch(FileBrowserAction.NavigateTo(backend.first))
        val old = backend.requests.removeFirst()
        controller.dispatch(FileBrowserAction.DeleteSelected)
        runCurrent()
        old.resume(emptyList())
        runCurrent()
        assertEquals(backend.root, controller.state.value.currentDirectory)
        assertEquals(listOf(backend.second.id), backend.deleted)
        assertFalse(controller.state.value.isLoading)
    }

    @Test
    fun backPriorityPreservesTheHostBoundary() {
        val backend = DelayedBackend()
        val child = FileBrowserUiState(backend.first, breadcrumbs = listOf(backend.root, backend.first))
        assertEquals(FileBrowserAction.NavigateUp, browserBackAction(child))
        val search = child.copy(search = FileBrowserSearchState(query = "init"))
        assertEquals(FileBrowserAction.StopSearch, browserBackAction(search))
        val selected = search.copy(selectedIds = setOf(backend.second.id))
        assertEquals(FileBrowserAction.ClearSelection, browserBackAction(selected))
        assertNull(browserBackAction(selected.copy(isLoading = true)))
        assertNull(browserBackAction(selected.copy(activeOperation = FileBrowserOperation.DELETE)))
        assertNull(browserBackAction(FileBrowserUiState(backend.root)))
    }

    @Test
    fun gestureCommitIsSingleAndCancellationOrTargetChangePreventsIt() {
        val session = BackGestureSession()
        session.begin("selection")
        assertTrue(session.update("selection"))
        assertTrue(session.complete("selection"))
        assertFalse(session.complete("selection"))
        session.begin("directory-a")
        session.cancel()
        assertFalse(session.complete("directory-a"))
        session.begin("directory-a")
        assertFalse(session.update("directory-b"))
        assertFalse(session.complete("directory-a"))
        session.begin("directory-b")
        assertTrue(session.complete("directory-b"))
        session.reset()
        assertTrue(session.complete("keyboard-back"))
    }

    private class DelayedBackend : FileBrowserBackend, DeleteEntriesCapability {
        val root = directory("root", null)
        val first = directory("first", root.id)
        val second = directory("second", root.id)
        val requests = ArrayDeque<Continuation<List<FileEntry>>>()
        var deleted = emptyList<EntryId>()

        override suspend fun stat(entryId: EntryId): FileEntry =
            listOf(root, first, second).first { it.id == entryId }

        override suspend fun list(directoryId: EntryId): List<FileEntry> =
            if (directoryId == root.id) listOf(first, second).filterNot { it.id in deleted }
            else suspendCoroutine { requests.addLast(it) }

        override suspend fun delete(entryIds: List<EntryId>) { deleted = entryIds }

        private fun directory(id: String, parent: EntryId?) = FileEntry(
            id = EntryId(id), parentId = parent, name = id, type = FileEntryType.DIRECTORY,
        )
    }
}
