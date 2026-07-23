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
) {
    val isSelectionMode: Boolean
        get() = selectedIds.isNotEmpty()

    val isBusy: Boolean
        get() = activeOperation != null
}
