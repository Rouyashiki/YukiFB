package com.anatdx.yukifb.state

import com.anatdx.yukifb.backend.FileBrowserBackend
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.FileEntryType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserSearchTest {
    @Test
    fun searchRecursesWithoutBackendSearchCapability() = runTest {
        val root = directory("root", null, "root")
        val etc = directory("etc", root.id, "etc")
        val init = file("init", root.id, "init.rc")
        val config = file("config", etc.id, "init.config")
        val backend = TreeBackend(
            entries = listOf(root, etc, init, config),
            children = mapOf(
                root.id to listOf(etc, init),
                etc.id to listOf(config),
            ),
        )
        val controller = FileBrowserController(backend, root, backgroundScope)
        advanceUntilIdle()

        controller.dispatch(FileBrowserAction.StartSearch)
        controller.dispatch(FileBrowserAction.UpdateSearchQuery("INIT"))
        advanceTimeBy(200)
        runCurrent()

        val search = requireNotNull(controller.state.value.search)
        assertFalse(search.isSearching)
        assertEquals(listOf(init.id, config.id), search.results.map { it.entry.id })
        assertEquals(
            listOf(root.id, etc.id),
            search.results.last().parentBreadcrumbs.map { it.id },
        )
    }

    @Test
    fun changingQueryCancelsPendingSearch() = runTest {
        val root = directory("root", null, "root")
        val alpha = file("alpha", root.id, "alpha.txt")
        val beta = file("beta", root.id, "beta.txt")
        val backend = TreeBackend(
            entries = listOf(root, alpha, beta),
            children = mapOf(root.id to listOf(alpha, beta)),
        )
        val controller = FileBrowserController(backend, root, backgroundScope)
        advanceUntilIdle()

        controller.dispatch(FileBrowserAction.StartSearch)
        controller.dispatch(FileBrowserAction.UpdateSearchQuery("alpha"))
        controller.dispatch(FileBrowserAction.UpdateSearchQuery("beta"))
        advanceTimeBy(200)
        runCurrent()

        assertEquals(
            listOf(beta.id),
            controller.state.value.search?.results?.map { it.entry.id },
        )
    }

    @Test
    fun hiddenDirectoriesAreNotTraversedWhenHiddenFilesAreDisabled() = runTest {
        val root = directory("root", null, "root")
        val hidden = directory("hidden", root.id, ".hidden")
        val secret = file("secret", hidden.id, "secret.txt")
        val backend = TreeBackend(
            entries = listOf(root, hidden, secret),
            children = mapOf(
                root.id to listOf(hidden),
                hidden.id to listOf(secret),
            ),
        )
        val controller = FileBrowserController(backend, root, backgroundScope)
        advanceUntilIdle()
        controller.dispatch(FileBrowserAction.ToggleHiddenFiles)
        controller.dispatch(FileBrowserAction.StartSearch)
        controller.dispatch(FileBrowserAction.UpdateSearchQuery("secret"))
        advanceTimeBy(200)
        runCurrent()

        assertTrue(controller.state.value.search?.results.orEmpty().isEmpty())
        assertEquals(1, controller.state.value.search?.scannedDirectoryCount)
    }

    @Test
    fun directoryIdsBreakTraversalCycles() = runTest {
        val root = directory("root", null, "root")
        val child = directory("child", root.id, "child")
        val backend = TreeBackend(
            entries = listOf(root, child),
            children = mapOf(
                root.id to listOf(child),
                child.id to listOf(root),
            ),
        )
        val controller = FileBrowserController(backend, root, backgroundScope)
        advanceUntilIdle()

        controller.dispatch(FileBrowserAction.StartSearch)
        controller.dispatch(FileBrowserAction.UpdateSearchQuery("root"))
        advanceTimeBy(200)
        runCurrent()

        assertFalse(controller.state.value.search?.isSearching ?: true)
        assertEquals(2, controller.state.value.search?.scannedDirectoryCount)
        assertTrue(controller.state.value.search?.results.orEmpty().isEmpty())
    }

    @Test
    fun stoppingSearchClearsSearchState() = runTest {
        val root = directory("root", null, "root")
        val controller = FileBrowserController(
            backend = TreeBackend(listOf(root), mapOf(root.id to emptyList())),
            initialDirectory = root,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        controller.dispatch(FileBrowserAction.StartSearch)
        controller.dispatch(FileBrowserAction.StopSearch)

        assertNull(controller.state.value.search)
    }

    private class TreeBackend(
        entries: List<FileEntry>,
        private val children: Map<EntryId, List<FileEntry>>,
    ) : FileBrowserBackend {
        private val entriesById = entries.associateBy { it.id }

        override suspend fun stat(entryId: EntryId): FileEntry =
            requireNotNull(entriesById[entryId])

        override suspend fun list(directoryId: EntryId): List<FileEntry> =
            children[directoryId].orEmpty()
    }

    private companion object {
        fun directory(
            id: String,
            parentId: EntryId?,
            name: String,
        ): FileEntry = FileEntry(
            id = EntryId(id),
            parentId = parentId,
            name = name,
            type = FileEntryType.DIRECTORY,
        )

        fun file(
            id: String,
            parentId: EntryId,
            name: String,
        ): FileEntry = FileEntry(
            id = EntryId(id),
            parentId = parentId,
            name = name,
            type = FileEntryType.REGULAR_FILE,
        )
    }
}
