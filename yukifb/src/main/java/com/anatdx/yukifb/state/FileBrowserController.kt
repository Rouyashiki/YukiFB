package com.anatdx.yukifb.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.anatdx.yukifb.backend.CopyEntriesCapability
import com.anatdx.yukifb.backend.CreateEntryCapability
import com.anatdx.yukifb.backend.DeleteEntriesCapability
import com.anatdx.yukifb.backend.FileBrowserBackend
import com.anatdx.yukifb.backend.FileBrowserCapabilities
import com.anatdx.yukifb.backend.HardLinkCapability
import com.anatdx.yukifb.backend.MoveEntriesCapability
import com.anatdx.yukifb.backend.RenameEntryCapability
import com.anatdx.yukifb.backend.SymbolicLinkCapability
import com.anatdx.yukifb.backend.UnixMetadataCapability
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.ArrayDeque

/**
 * Headless state and operation coordinator for a [FileBrowserBackend].
 *
 * The supplied [scope] controls the controller lifetime. Backends are responsible
 * for moving blocking work away from that scope's dispatcher.
 */
class FileBrowserController(
    private val backend: FileBrowserBackend,
    initialDirectory: FileEntry,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(
        FileBrowserUiState(
            currentDirectory = initialDirectory,
            capabilities = FileBrowserCapabilities.from(backend),
        ),
    )
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var loadRevision = 0L
    private var failedDirectory: FileEntry? = null

    /** Observable browser state. */
    val state: StateFlow<FileBrowserUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Handles an action emitted by the Compose UI or a host application. */
    fun dispatch(action: FileBrowserAction) {
        when (action) {
            is FileBrowserAction.NavigateTo -> navigateTo(action.directory)
            FileBrowserAction.NavigateUp -> navigateUp()
            FileBrowserAction.Refresh -> refresh()
            FileBrowserAction.StartSearch -> startSearch()
            is FileBrowserAction.UpdateSearchQuery -> {
                updateSearchQuery(action.query)
            }

            FileBrowserAction.StopSearch -> stopSearch()
            is FileBrowserAction.ToggleSelection -> toggleSelection(action.entryId)
            FileBrowserAction.SelectAll -> selectAll()
            FileBrowserAction.ClearSelection -> clearSelection()
            is FileBrowserAction.SetSort -> _state.update { it.copy(sort = action.sort) }
            FileBrowserAction.ToggleHiddenFiles -> toggleHiddenFiles()

            FileBrowserAction.CopySelected -> stageClipboard(ClipboardMode.COPY)
            FileBrowserAction.MoveSelected -> stageClipboard(ClipboardMode.MOVE)
            FileBrowserAction.HardLinkSelected -> stageClipboard(ClipboardMode.HARD_LINK)
            FileBrowserAction.Paste -> paste()
            FileBrowserAction.ClearClipboard -> {
                _state.update { it.copy(clipboard = null) }
            }

            is FileBrowserAction.CreateFile -> createFile(action.name)
            is FileBrowserAction.CreateDirectory -> createDirectory(action.name)
            is FileBrowserAction.CreateSymbolicLink -> {
                createSymbolicLink(action.name, action.target)
            }

            is FileBrowserAction.Rename -> rename(action.entry, action.newName)
            FileBrowserAction.DeleteSelected -> deleteSelected()
            is FileBrowserAction.UpdateMetadata -> {
                updateMetadata(action.entryIds, action.patch)
            }

            FileBrowserAction.ClearError -> _state.update { it.copy(error = null) }
        }
    }

    private fun navigateTo(directory: FileEntry) {
        if (!directory.isDirectory || _state.value.isBusy) return
        searchJob?.cancel()
        searchJob = null
        _state.update { it.copy(search = it.search?.copy(isSearching = false)) }
        requestDirectory(directory, navigating = true)
    }

    private fun navigateUp() {
        if (_state.value.isBusy) return
        val parent = _state.value.breadcrumbs.getOrNull(
            _state.value.breadcrumbs.lastIndex - 1,
        ) ?: return
        navigateTo(parent)
    }

    private fun refresh() {
        if (_state.value.isBusy) return
        val directory = failedDirectory ?: _state.value.currentDirectory
        requestDirectory(directory, navigating = directory.id != _state.value.currentDirectory.id)
    }

    private fun requestDirectory(directory: FileEntry, navigating: Boolean) {
        val revision = ++loadRevision
        loadJob?.cancel()
        failedDirectory = null
        loadJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            _state.update { it.copy(isLoading = true, error = null) }
            loadDirectory(directory, revision, navigating)
        }
    }

    private fun startSearch() {
        if (_state.value.isBusy) return
        searchJob?.cancel()
        searchJob = null
        _state.update {
            it.copy(
                selectedIds = emptySet(),
                search = FileBrowserSearchState(),
                error = null,
            )
        }
    }

    private fun updateSearchQuery(query: String) {
        if (_state.value.search == null || _state.value.isBusy) return
        searchJob?.cancel()
        searchJob = null
        val searchTerm = query.trim()
        _state.update { current ->
            current.copy(
                selectedIds = emptySet(),
                search = current.search?.copy(
                    query = query,
                    results = emptyList(),
                    isSearching = searchTerm.isNotEmpty(),
                    scannedDirectoryCount = 0,
                    skippedDirectoryCount = 0,
                    resultLimitReached = false,
                ),
                error = current.error?.takeUnless {
                    it.operation == FileBrowserOperation.SEARCH
                },
            )
        }
        if (searchTerm.isEmpty()) return

        val root = _state.value.currentDirectory
        val rootBreadcrumbs = _state.value.breadcrumbs
            .takeIf { it.lastOrNull()?.id == root.id }
            ?: listOf(root)
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            searchRecursively(
                root = root,
                rootBreadcrumbs = rootBreadcrumbs,
                query = query,
                searchTerm = searchTerm,
                showHiddenFiles = _state.value.showHiddenFiles,
            )
        }
    }

    private fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
        _state.update {
            it.copy(
                selectedIds = emptySet(),
                search = null,
                error = it.error?.takeUnless { error ->
                    error.operation == FileBrowserOperation.SEARCH
                },
            )
        }
    }

    private fun toggleHiddenFiles() {
        val activeQuery = _state.value.search?.query
        _state.update { it.copy(showHiddenFiles = !it.showHiddenFiles) }
        if (activeQuery != null) updateSearchQuery(activeQuery)
    }

    private suspend fun searchRecursively(
        root: FileEntry,
        rootBreadcrumbs: List<FileEntry>,
        query: String,
        searchTerm: String,
        showHiddenFiles: Boolean,
    ) {
        val pending = ArrayDeque<SearchDirectory>()
        pending += SearchDirectory(root, rootBreadcrumbs, depth = 0)
        val visitedDirectoryIds = hashSetOf(root.id)
        val matchedEntryIds = hashSetOf(root.id)
        val results = mutableListOf<FileBrowserSearchResult>()
        var scannedDirectoryCount = 0
        var skippedDirectoryCount = 0
        var publishedResultCount = 0
        var publishedSkippedDirectoryCount = 0
        var resultLimitReached = false

        while (pending.isNotEmpty() && !resultLimitReached) {
            yield()
            val current = pending.removeFirst()
            val children = try {
                backend.list(current.directory.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (current.directory.id == root.id) {
                    updateCurrentSearch(root.id, query) {
                        it.copy(isSearching = false)
                    }
                    _state.update { state ->
                        if (
                            state.currentDirectory.id == root.id &&
                            state.search?.query == query
                        ) {
                            state.copy(
                                error = FileBrowserError(
                                    operation = FileBrowserOperation.SEARCH,
                                    cause = failure,
                                ),
                            )
                        } else {
                            state
                        }
                    }
                    return
                }
                skippedDirectoryCount += 1
                publishSearchProgress(
                    rootId = root.id,
                    query = query,
                    results = results,
                    scannedDirectoryCount = scannedDirectoryCount,
                    skippedDirectoryCount = skippedDirectoryCount,
                    resultLimitReached = false,
                    isSearching = true,
                )
                publishedResultCount = results.size
                publishedSkippedDirectoryCount = skippedDirectoryCount
                continue
            }

            scannedDirectoryCount += 1
            for ((index, child) in children.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (index > 0 && index % SEARCH_ENTRY_YIELD_INTERVAL == 0) {
                    yield()
                }
                if (!showHiddenFiles && child.name.startsWith('.')) continue

                if (
                    child.name.contains(searchTerm, ignoreCase = true) &&
                    matchedEntryIds.add(child.id)
                ) {
                    results += FileBrowserSearchResult(
                        entry = child,
                        parentBreadcrumbs = current.breadcrumbs,
                    )
                    if (results.size >= MAX_SEARCH_RESULTS) {
                        resultLimitReached = true
                        break
                    }
                }

                if (child.isDirectory && visitedDirectoryIds.add(child.id)) {
                    if (current.depth < MAX_SEARCH_DEPTH) {
                        pending += SearchDirectory(
                            directory = child,
                            breadcrumbs = current.breadcrumbs + child,
                            depth = current.depth + 1,
                        )
                    } else {
                        skippedDirectoryCount += 1
                    }
                }
            }

            if (
                results.size != publishedResultCount ||
                scannedDirectoryCount % SEARCH_PROGRESS_INTERVAL == 0 ||
                skippedDirectoryCount != publishedSkippedDirectoryCount ||
                resultLimitReached
            ) {
                publishSearchProgress(
                    rootId = root.id,
                    query = query,
                    results = results,
                    scannedDirectoryCount = scannedDirectoryCount,
                    skippedDirectoryCount = skippedDirectoryCount,
                    resultLimitReached = resultLimitReached,
                    isSearching = !resultLimitReached && pending.isNotEmpty(),
                )
                publishedResultCount = results.size
                publishedSkippedDirectoryCount = skippedDirectoryCount
            }
        }

        publishSearchProgress(
            rootId = root.id,
            query = query,
            results = results,
            scannedDirectoryCount = scannedDirectoryCount,
            skippedDirectoryCount = skippedDirectoryCount,
            resultLimitReached = resultLimitReached,
            isSearching = false,
        )
    }

    private fun publishSearchProgress(
        rootId: EntryId,
        query: String,
        results: List<FileBrowserSearchResult>,
        scannedDirectoryCount: Int,
        skippedDirectoryCount: Int,
        resultLimitReached: Boolean,
        isSearching: Boolean,
    ) {
        updateCurrentSearch(rootId, query) {
            it.copy(
                results = results.toList(),
                isSearching = isSearching,
                scannedDirectoryCount = scannedDirectoryCount,
                skippedDirectoryCount = skippedDirectoryCount,
                resultLimitReached = resultLimitReached,
            )
        }
    }

    private fun updateCurrentSearch(
        rootId: EntryId,
        query: String,
        transform: (FileBrowserSearchState) -> FileBrowserSearchState,
    ) {
        _state.update { current ->
            val search = current.search
            if (
                current.currentDirectory.id != rootId ||
                search == null ||
                search.query != query
            ) {
                current
            } else {
                current.copy(search = transform(search))
            }
        }
    }

    private suspend fun loadDirectory(directory: FileEntry, revision: Long, navigating: Boolean) {
        try {
            val entries = backend.list(directory.id)
            val breadcrumbs = loadBreadcrumbs(directory)
            if (revision != loadRevision) return
            val existingIds = entries.asSequence().map { it.id }.toSet()
            _state.update {
                it.copy(
                    currentDirectory = directory,
                    breadcrumbs = breadcrumbs,
                    entries = entries,
                    selectedIds = if (navigating) emptySet() else it.selectedIds.intersect(existingIds),
                    search = if (navigating) null else it.search,
                    isLoading = false,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            if (revision == loadRevision) {
                failedDirectory = directory
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = FileBrowserError(
                            operation = FileBrowserOperation.LOAD,
                            cause = failure,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun loadBreadcrumbs(directory: FileEntry): List<FileEntry> {
        val reversed = mutableListOf(directory)
        val visited = mutableSetOf(directory.id)
        var parentId = directory.parentId

        while (parentId != null && reversed.size < MAX_BREADCRUMB_DEPTH) {
            check(visited.add(parentId)) {
                "Backend returned a parent cycle at $parentId"
            }
            val parent = backend.stat(parentId)
            reversed += parent
            parentId = parent.parentId
        }
        return reversed.asReversed()
    }

    private fun toggleSelection(entryId: EntryId) {
        _state.update { current ->
            val selection = current.selectedIds.toMutableSet()
            if (!selection.add(entryId)) selection.remove(entryId)
            current.copy(selectedIds = selection)
        }
    }

    private fun selectAll() {
        _state.update { current ->
            current.copy(
                selectedIds = current.visibleEntries().mapTo(linkedSetOf()) { it.id },
            )
        }
    }

    private fun clearSelection() {
        _state.update { it.copy(selectedIds = emptySet()) }
    }

    private fun stageClipboard(mode: ClipboardMode) {
        val current = _state.value
        val entries = current.visibleEntries().filter { it.id in current.selectedIds }
        val supported = when (mode) {
            ClipboardMode.COPY -> current.capabilities.canCopy
            ClipboardMode.MOVE -> current.capabilities.canMove
            ClipboardMode.HARD_LINK -> {
                current.capabilities.canCreateHardLinks &&
                    entries.size == 1 &&
                    !entries.single().isDirectory
            }
        }
        if (!supported || entries.isEmpty()) return
        _state.update {
            it.copy(
                clipboard = FileBrowserClipboard(entries, mode),
                selectedIds = emptySet(),
            )
        }
    }

    private fun paste() {
        val current = _state.value
        val clipboard = current.clipboard ?: return
        val destinationId = current.currentDirectory.id
        when (clipboard.mode) {
            ClipboardMode.COPY -> runMutation(FileBrowserOperation.COPY) {
                val capability = backend as CopyEntriesCapability
                capability.copy(clipboard.entries.map { it.id }, destinationId)
            }

            ClipboardMode.MOVE -> runMutation(
                operation = FileBrowserOperation.MOVE,
                clearClipboard = true,
            ) {
                val capability = backend as MoveEntriesCapability
                capability.move(clipboard.entries.map { it.id }, destinationId)
            }

            ClipboardMode.HARD_LINK -> runMutation(
                operation = FileBrowserOperation.CREATE_LINK,
                clearClipboard = true,
            ) {
                val capability = backend as HardLinkCapability
                val target = clipboard.entries.single()
                capability.createHardLink(destinationId, target.name, target.id)
            }
        }
    }

    private fun createFile(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || !_state.value.capabilities.canCreateEntries) return
        val parentId = _state.value.currentDirectory.id
        runMutation(FileBrowserOperation.CREATE) {
            (backend as CreateEntryCapability).createFile(parentId, cleanName)
        }
    }

    private fun createDirectory(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || !_state.value.capabilities.canCreateEntries) return
        val parentId = _state.value.currentDirectory.id
        runMutation(FileBrowserOperation.CREATE) {
            (backend as CreateEntryCapability).createDirectory(parentId, cleanName)
        }
    }

    private fun createSymbolicLink(name: String, target: String) {
        val cleanName = name.trim()
        if (
            cleanName.isEmpty() ||
            target.isEmpty() ||
            !_state.value.capabilities.canCreateSymbolicLinks
        ) {
            return
        }
        val parentId = _state.value.currentDirectory.id
        runMutation(FileBrowserOperation.CREATE_LINK) {
            (backend as SymbolicLinkCapability).createSymbolicLink(
                parentId = parentId,
                name = cleanName,
                target = target,
            )
        }
    }

    private fun rename(entry: FileEntry, newName: String) {
        val cleanName = newName.trim()
        if (
            cleanName.isEmpty() ||
            cleanName == entry.name ||
            !_state.value.capabilities.canRename
        ) {
            return
        }
        runMutation(FileBrowserOperation.RENAME) {
            (backend as RenameEntryCapability).rename(entry.id, cleanName)
        }
    }

    private fun deleteSelected() {
        val current = _state.value
        val entryIds = current.selectedIds.toList()
        if (entryIds.isEmpty() || !current.capabilities.canDelete) return
        runMutation(
            operation = FileBrowserOperation.DELETE,
            clearSelection = true,
        ) {
            (backend as DeleteEntriesCapability).delete(entryIds)
        }
    }

    private fun updateMetadata(
        entryIds: List<EntryId>,
        patch: com.anatdx.yukifb.model.UnixMetadataPatch,
    ) {
        if (entryIds.isEmpty() || !_state.value.capabilities.canEditUnixMetadata) return
        runMutation(FileBrowserOperation.UPDATE_METADATA) {
            (backend as UnixMetadataCapability).updateMetadata(entryIds, patch)
        }
    }

    private fun runMutation(
        operation: FileBrowserOperation,
        clearSelection: Boolean = false,
        clearClipboard: Boolean = false,
        block: suspend () -> Unit,
    ) {
        if (_state.value.isBusy) return
        loadRevision++
        failedDirectory = null
        loadJob?.cancel()
        loadJob = null
        searchJob?.cancel()
        searchJob = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            _state.update {
                it.copy(
                    isLoading = false,
                    activeOperation = operation,
                    search = it.search?.copy(isSearching = false),
                    error = null,
                )
            }
            try {
                block()
                val directory = _state.value.currentDirectory
                val entries = backend.list(directory.id)
                val breadcrumbs = loadBreadcrumbs(directory)
                _state.update {
                    it.copy(
                        entries = entries,
                        breadcrumbs = breadcrumbs,
                        selectedIds = if (clearSelection) {
                            emptySet()
                        } else {
                            it.selectedIds.intersect(entries.mapTo(hashSetOf()) { entry ->
                                entry.id
                            })
                        },
                        clipboard = if (clearClipboard) null else it.clipboard,
                        isLoading = false,
                        activeOperation = null,
                    )
                }
                rerunActiveSearch()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        activeOperation = null,
                        error = FileBrowserError(operation, failure),
                    )
                }
            }
        }
    }

    private fun rerunActiveSearch() {
        val query = _state.value.search?.query ?: return
        updateSearchQuery(query)
    }

    private data class SearchDirectory(
        val directory: FileEntry,
        val breadcrumbs: List<FileEntry>,
        val depth: Int,
    )

    private companion object {
        const val MAX_BREADCRUMB_DEPTH = 256
        const val MAX_SEARCH_DEPTH = 256
        const val MAX_SEARCH_RESULTS = 1_000
        const val SEARCH_DEBOUNCE_MILLIS = 200L
        const val SEARCH_PROGRESS_INTERVAL = 16
        const val SEARCH_ENTRY_YIELD_INTERVAL = 128
    }
}

/**
 * Remembers a controller whose lifetime follows the current composition.
 */
@Composable
fun rememberFileBrowserController(
    backend: FileBrowserBackend,
    initialDirectory: FileEntry,
): FileBrowserController {
    val scope = rememberCoroutineScope()
    return remember(backend, initialDirectory.id) {
        FileBrowserController(
            backend = backend,
            initialDirectory = initialDirectory,
            scope = scope,
        )
    }
}
