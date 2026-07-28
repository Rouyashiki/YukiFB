package com.anatdx.yukifb.state

import com.anatdx.yukifb.backend.FileBrowserCapabilities
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry

/** Field used to order entries in the browser. */
enum class FileSortField {
    NAME,
    SIZE,
    MODIFIED,
}

/** Sort direction for a [FileSortField]. */
enum class FileSortDirection {
    ASCENDING,
    DESCENDING,
}

/** Current ordering configuration. */
data class FileBrowserSort(
    val field: FileSortField = FileSortField.NAME,
    val direction: FileSortDirection = FileSortDirection.ASCENDING,
    val directoriesFirst: Boolean = true,
)

/** Pending clipboard operation. */
enum class ClipboardMode {
    COPY,
    MOVE,
    HARD_LINK,
}

/** Stable entry references waiting to be pasted. */
data class FileBrowserClipboard(
    val entries: List<FileEntry>,
    val mode: ClipboardMode,
)

/**
 * One filename match found below the active search directory.
 *
 * [parentBreadcrumbs] is display and navigation data only. [FileEntry.id] remains
 * the canonical identity of the result.
 *
 * @property entry Matched backend entry.
 * @property parentBreadcrumbs Entries from the backend root through the parent.
 */
data class FileBrowserSearchResult(
    val entry: FileEntry,
    val parentBreadcrumbs: List<FileEntry>,
)

/**
 * State of an active recursive filename search.
 *
 * @property query User-entered filename fragment.
 * @property results Matches found so far.
 * @property isSearching Whether recursive traversal is still running.
 * @property scannedDirectoryCount Number of directories successfully listed.
 * @property skippedDirectoryCount Number of directories that could not be traversed.
 * @property resultLimitReached Whether traversal stopped at the safety result limit.
 */
data class FileBrowserSearchState(
    val query: String = "",
    val results: List<FileBrowserSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val scannedDirectoryCount: Int = 0,
    val skippedDirectoryCount: Int = 0,
    val resultLimitReached: Boolean = false,
)

/** Long-running operation currently performed by the controller. */
enum class FileBrowserOperation {
    LOAD,
    CREATE,
    RENAME,
    DELETE,
    COPY,
    MOVE,
    CREATE_LINK,
    UPDATE_METADATA,
    SEARCH,
}

/** Error produced by a backend operation. */
data class FileBrowserError(
    val operation: FileBrowserOperation,
    val cause: Throwable,
)

/** Immutable state consumed by the Compose file browser. */
data class FileBrowserUiState(
    val currentDirectory: FileEntry,
    val breadcrumbs: List<FileEntry> = listOf(currentDirectory),
    val entries: List<FileEntry> = emptyList(),
    val selectedIds: Set<EntryId> = emptySet(),
    val clipboard: FileBrowserClipboard? = null,
    val sort: FileBrowserSort = FileBrowserSort(),
    val showHiddenFiles: Boolean = true,
    val isLoading: Boolean = false,
    val activeOperation: FileBrowserOperation? = null,
    val error: FileBrowserError? = null,
    val capabilities: FileBrowserCapabilities = FileBrowserCapabilities(),
    val search: FileBrowserSearchState? = null,
) {
    /**
     * Retains the 0.1.0 constructor ABI for already compiled consumers.
     */
    @Deprecated(
        message = "Binary compatibility bridge",
        level = DeprecationLevel.HIDDEN,
    )
    constructor(
        currentDirectory: FileEntry,
        breadcrumbs: List<FileEntry> = listOf(currentDirectory),
        entries: List<FileEntry> = emptyList(),
        selectedIds: Set<EntryId> = emptySet(),
        clipboard: FileBrowserClipboard? = null,
        sort: FileBrowserSort = FileBrowserSort(),
        showHiddenFiles: Boolean = true,
        isLoading: Boolean = false,
        activeOperation: FileBrowserOperation? = null,
        error: FileBrowserError? = null,
        capabilities: FileBrowserCapabilities = FileBrowserCapabilities(),
    ) : this(
        currentDirectory = currentDirectory,
        breadcrumbs = breadcrumbs,
        entries = entries,
        selectedIds = selectedIds,
        clipboard = clipboard,
        sort = sort,
        showHiddenFiles = showHiddenFiles,
        isLoading = isLoading,
        activeOperation = activeOperation,
        error = error,
        capabilities = capabilities,
        search = null,
    )

    val isSelectionMode: Boolean
        get() = selectedIds.isNotEmpty()

    val isBusy: Boolean
        get() = activeOperation != null

    /**
     * Retains the 0.1.0 `copy` ABI while preserving the current search state.
     */
    @Deprecated(
        message = "Binary compatibility bridge",
        level = DeprecationLevel.HIDDEN,
    )
    fun copy(
        currentDirectory: FileEntry = this.currentDirectory,
        breadcrumbs: List<FileEntry> = this.breadcrumbs,
        entries: List<FileEntry> = this.entries,
        selectedIds: Set<EntryId> = this.selectedIds,
        clipboard: FileBrowserClipboard? = this.clipboard,
        sort: FileBrowserSort = this.sort,
        showHiddenFiles: Boolean = this.showHiddenFiles,
        isLoading: Boolean = this.isLoading,
        activeOperation: FileBrowserOperation? = this.activeOperation,
        error: FileBrowserError? = this.error,
        capabilities: FileBrowserCapabilities = this.capabilities,
    ): FileBrowserUiState = FileBrowserUiState(
        currentDirectory = currentDirectory,
        breadcrumbs = breadcrumbs,
        entries = entries,
        selectedIds = selectedIds,
        clipboard = clipboard,
        sort = sort,
        showHiddenFiles = showHiddenFiles,
        isLoading = isLoading,
        activeOperation = activeOperation,
        error = error,
        capabilities = capabilities,
        search = search,
    )
}
