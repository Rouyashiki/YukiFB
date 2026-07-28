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
    val isSelectionMode: Boolean
        get() = selectedIds.isNotEmpty()

    val isBusy: Boolean
        get() = activeOperation != null
}
