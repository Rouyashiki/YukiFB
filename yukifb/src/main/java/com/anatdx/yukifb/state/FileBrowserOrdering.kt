package com.anatdx.yukifb.state

import com.anatdx.yukifb.model.FileEntry

/** Applies hidden-file filtering and stable browser ordering to [entries]. */
fun orderFileEntries(
    entries: List<FileEntry>,
    sort: FileBrowserSort,
    showHiddenFiles: Boolean,
): List<FileEntry> {
    val visible = if (showHiddenFiles) {
        entries
    } else {
        entries.filterNot { it.name.startsWith('.') }
    }

    val fieldComparator = when (sort.field) {
        FileSortField.NAME -> compareBy<FileEntry> { it.name.lowercase() }
            .thenBy { it.name }

        FileSortField.SIZE -> compareBy<FileEntry> { it.size ?: Long.MIN_VALUE }
            .thenBy { it.name.lowercase() }

        FileSortField.MODIFIED -> compareBy<FileEntry> {
            it.modifiedAtMillis ?: Long.MIN_VALUE
        }.thenBy { it.name.lowercase() }
    }

    val directed = if (sort.direction == FileSortDirection.ASCENDING) {
        fieldComparator
    } else {
        Comparator { left, right -> fieldComparator.compare(right, left) }
    }

    val comparator = if (sort.directoriesFirst) {
        compareByDescending<FileEntry> { it.isDirectory }.then(directed)
    } else {
        directed
    }
    return visible.sortedWith(comparator)
}

/** Returns the entries currently visible to the user. */
fun FileBrowserUiState.visibleEntries(): List<FileEntry> =
    orderFileEntries(
        entries = search?.results?.map { it.entry } ?: entries,
        sort = sort,
        showHiddenFiles = showHiddenFiles,
    )

/** Returns active search matches in the same order as [visibleEntries]. */
fun FileBrowserUiState.visibleSearchResults(): List<FileBrowserSearchResult> {
    val searchResults = search?.results.orEmpty()
    if (searchResults.isEmpty()) return emptyList()
    val resultById = searchResults.associateBy { it.entry.id }
    return visibleEntries().mapNotNull { resultById[it.id] }
}
