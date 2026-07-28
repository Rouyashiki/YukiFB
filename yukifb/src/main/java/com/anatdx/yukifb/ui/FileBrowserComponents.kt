package com.anatdx.yukifb.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anatdx.yukifb.R
import com.anatdx.yukifb.backend.FileBrowserCapabilities
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.FileEntryType
import com.anatdx.yukifb.state.ClipboardMode
import com.anatdx.yukifb.state.FileBrowserAction
import com.anatdx.yukifb.state.FileBrowserClipboard
import com.anatdx.yukifb.state.FileBrowserSearchResult
import com.anatdx.yukifb.state.FileBrowserUiState
import com.anatdx.yukifb.state.FileSortDirection
import com.anatdx.yukifb.state.FileSortField
import com.anatdx.yukifb.state.visibleEntries
import com.anatdx.yukifb.state.visibleSearchResults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowserTopBar(
    state: FileBrowserUiState,
    onAction: (FileBrowserAction) -> Unit,
    onClose: (() -> Unit)?,
    topBarActions: @Composable RowScope.() -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    if (state.isSelectionMode) {
        TopAppBar(
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.yukifb_selected_count,
                        state.selectedIds.size,
                        state.selectedIds.size,
                    ),
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = { onAction(FileBrowserAction.ClearSelection) },
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(
                            R.string.yukifb_close_selection,
                        ),
                    )
                }
            },
            actions = {
                IconButton(onClick = { onAction(FileBrowserAction.SelectAll) }) {
                    Icon(
                        Icons.Default.SelectAll,
                        contentDescription = stringResource(R.string.yukifb_select_all),
                    )
                }
            },
        )
        return
    }

    if (state.search != null) {
        SearchTopBar(
            query = state.search.query,
            enabled = !state.isBusy,
            onQueryChange = {
                onAction(FileBrowserAction.UpdateSearchQuery(it))
            },
            onClose = { onAction(FileBrowserAction.StopSearch) },
        )
        return
    }

    TopAppBar(
        title = {
            Text(
                text = state.currentDirectory.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            when {
                state.breadcrumbs.size > 1 -> {
                    IconButton(
                        enabled = !state.isBusy,
                        onClick = { onAction(FileBrowserAction.NavigateUp) },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.yukifb_up),
                        )
                    }
                }

                onClose != null -> {
                    IconButton(
                        enabled = !state.isBusy,
                        onClick = onClose,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.yukifb_close),
                        )
                    }
                }
            }
        },
        actions = {
            topBarActions()
            IconButton(
                enabled = !state.isBusy && !state.isLoading,
                onClick = { onAction(FileBrowserAction.StartSearch) },
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.yukifb_search),
                )
            }
            IconButton(
                enabled = !state.isBusy,
                onClick = { onAction(FileBrowserAction.Refresh) },
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.yukifb_refresh),
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.yukifb_more),
                    )
                }
                BrowserOptionsMenu(
                    expanded = menuExpanded,
                    state = state,
                    onDismiss = { menuExpanded = false },
                    onAction = {
                        onAction(it)
                        menuExpanded = false
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                enabled = enabled,
                singleLine = true,
                placeholder = {
                    Text(stringResource(R.string.yukifb_search_hint))
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = stringResource(
                                    R.string.yukifb_clear_search,
                                ),
                            )
                        }
                    }
                } else {
                    null
                },
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.yukifb_close_search),
                )
            }
        },
    )
}

@Composable
private fun BrowserOptionsMenu(
    expanded: Boolean,
    state: FileBrowserUiState,
    onDismiss: () -> Unit,
    onAction: (FileBrowserAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        SortMenuItem(
            label = stringResource(R.string.yukifb_sort_name),
            selected = state.sort.field == FileSortField.NAME,
            onClick = {
                onAction(FileBrowserAction.SetSort(state.sort.copy(field = FileSortField.NAME)))
            },
        )
        SortMenuItem(
            label = stringResource(R.string.yukifb_sort_size),
            selected = state.sort.field == FileSortField.SIZE,
            onClick = {
                onAction(FileBrowserAction.SetSort(state.sort.copy(field = FileSortField.SIZE)))
            },
        )
        SortMenuItem(
            label = stringResource(R.string.yukifb_sort_modified),
            selected = state.sort.field == FileSortField.MODIFIED,
            onClick = {
                onAction(
                    FileBrowserAction.SetSort(
                        state.sort.copy(field = FileSortField.MODIFIED),
                    ),
                )
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(
                    if (state.sort.direction == FileSortDirection.ASCENDING) {
                        stringResource(R.string.yukifb_sort_descending)
                    } else {
                        stringResource(R.string.yukifb_sort_ascending)
                    },
                )
            },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
            },
            onClick = {
                val direction = if (
                    state.sort.direction == FileSortDirection.ASCENDING
                ) {
                    FileSortDirection.DESCENDING
                } else {
                    FileSortDirection.ASCENDING
                }
                onAction(FileBrowserAction.SetSort(state.sort.copy(direction = direction)))
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (state.showHiddenFiles) {
                        stringResource(R.string.yukifb_hide_hidden)
                    } else {
                        stringResource(R.string.yukifb_show_hidden)
                    },
                )
            },
            onClick = { onAction(FileBrowserAction.ToggleHiddenFiles) },
        )
    }
}

@Composable
private fun SortMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null)
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        onClick = onClick,
    )
}

@Composable
internal fun FileBrowserContent(
    state: FileBrowserUiState,
    options: FileBrowserOptions,
    contentPadding: PaddingValues,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onEntryProperties: (FileEntry) -> Unit,
    onBreadcrumbClick: (FileEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        if (state.isLoading || state.isBusy || state.search?.isSearching == true) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (
            state.search == null &&
            options.showBreadcrumbs &&
            state.breadcrumbs.size > 1
        ) {
            BreadcrumbBar(
                breadcrumbs = state.breadcrumbs,
                enabled = !state.isBusy && !state.isSelectionMode,
                onClick = onBreadcrumbClick,
            )
            HorizontalDivider()
        }

        val search = state.search
        if (search != null) {
            SearchResults(
                state = state,
                options = options,
                onEntryClick = onEntryClick,
                onEntryLongClick = onEntryLongClick,
                onEntryProperties = onEntryProperties,
            )
            return@Column
        }

        val entries = state.visibleEntries()
        when {
            state.isLoading && entries.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            entries.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.yukifb_empty_directory),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { _, entry -> entry.id.value },
                    ) { index, entry ->
                        FileEntryRow(
                            entry = entry,
                            index = index,
                            itemCount = entries.size,
                            selected = entry.id in state.selectedIds,
                            selectionMode = state.isSelectionMode,
                            enabled = !state.isBusy,
                            options = options,
                            onClick = { onEntryClick(entry) },
                            onLongClick = { onEntryLongClick(entry) },
                            onProperties = { onEntryProperties(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: FileBrowserUiState,
    options: FileBrowserOptions,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onEntryProperties: (FileEntry) -> Unit,
) {
    val search = state.search ?: return
    val results = state.visibleSearchResults()
    when {
        search.query.isBlank() -> {
            EmptyContent(
                message = stringResource(R.string.yukifb_search_prompt),
                modifier = Modifier.fillMaxSize(),
            )
        }

        search.isSearching && results.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        results.isEmpty() -> {
            Column(Modifier.fillMaxSize()) {
                EmptyContent(
                    message = stringResource(R.string.yukifb_no_search_results),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                if (search.skippedDirectoryCount > 0) {
                    SearchStatus(
                        resultCount = 0,
                        resultLimitReached = false,
                        skippedDirectoryCount = search.skippedDirectoryCount,
                    )
                }
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(
                    items = results,
                    key = { _, result -> result.entry.id.value },
                ) { index, result ->
                    FileEntryRow(
                        entry = result.entry,
                        index = index,
                        itemCount = results.size,
                        selected = result.entry.id in state.selectedIds,
                        selectionMode = state.isSelectionMode,
                        enabled = !state.isBusy,
                        options = options,
                        location = result.displayLocation(),
                        onClick = { onEntryClick(result.entry) },
                        onLongClick = { onEntryLongClick(result.entry) },
                        onProperties = { onEntryProperties(result.entry) },
                    )
                }
                if (
                    search.resultLimitReached ||
                    search.skippedDirectoryCount > 0
                ) {
                    item(key = "yukifb-search-status") {
                        SearchStatus(
                            resultCount = results.size,
                            resultLimitReached = search.resultLimitReached,
                            skippedDirectoryCount = search.skippedDirectoryCount,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchStatus(
    resultCount: Int,
    resultLimitReached: Boolean,
    skippedDirectoryCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (resultLimitReached) {
            Text(
                text = pluralStringResource(
                    R.plurals.yukifb_search_result_limit,
                    resultCount,
                    resultCount,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (skippedDirectoryCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.yukifb_search_skipped_directories,
                    skippedDirectoryCount,
                    skippedDirectoryCount,
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun FileBrowserSearchResult.displayLocation(): String =
    parentBreadcrumbs.joinToString(separator = " / ") { it.name }

@Composable
private fun BreadcrumbBar(
    breadcrumbs: List<FileEntry>,
    enabled: Boolean,
    onClick: (FileEntry) -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(breadcrumbs.map { it.id }) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        breadcrumbs.forEachIndexed { index, entry ->
            if (index > 0) {
                Text(
                    text = "/",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                enabled = enabled && index != breadcrumbs.lastIndex,
                onClick = { onClick(entry) },
            ) {
                Text(
                    text = entry.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileEntryRow(
    entry: FileEntry,
    index: Int,
    itemCount: Int,
    selected: Boolean,
    selectionMode: Boolean,
    enabled: Boolean,
    options: FileBrowserOptions,
    location: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onProperties: () -> Unit,
) {
    val details = remember(entry, options, location) {
        buildList {
            if (!location.isNullOrEmpty()) {
                add(location)
            }
            if (entry.type == FileEntryType.SYMBOLIC_LINK && entry.linkTarget != null) {
                add("→ ${entry.linkTarget}")
            }
            if (options.showFileSize && !entry.isDirectory && entry.size != null) {
                add(formatFileSize(entry.size))
            }
            if (options.showUnixMetadata && entry.unixMetadata != null) {
                add(formatOctalPermissions(entry.unixMetadata.permissions))
                add("${entry.unixMetadata.uid}:${entry.unixMetadata.gid}")
            }
            if (options.showModifiedTime && entry.modifiedAtMillis != null) {
                add(formatModifiedTime(entry.modifiedAtMillis))
            }
        }.joinToString(" · ")
    }

    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = options.rowMinHeight)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        enabled = enabled,
        onLongClick = onLongClick,
        shapes = ListItemDefaults.segmentedShapes(index, itemCount),
        leadingContent = {
            FileTypeIcon(
                entryType = entry.type,
                iconType = options.fileIconResolver(entry),
            )
        },
        content = {
            Text(
                text = entry.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = if (details.isNotEmpty()) {
            {
                Text(
                    text = details,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            null
        },
        trailingContent = {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                )
            } else {
                IconButton(onClick = onProperties) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.yukifb_properties),
                    )
                }
            }
        },
    )
}

@Composable
private fun FileTypeIcon(
    entryType: FileEntryType,
    iconType: FileIconType,
) {
    val icon: ImageVector = when (iconType) {
        FileIconType.DIRECTORY -> Icons.Default.Folder
        FileIconType.SYMBOLIC_LINK -> Icons.Default.Link
        FileIconType.SPECIAL_FILE -> Icons.Default.Memory
        FileIconType.GENERIC_FILE -> Icons.AutoMirrored.Outlined.InsertDriveFile
        FileIconType.TEXT -> Icons.Default.Description
        FileIconType.IMAGE -> Icons.Default.Image
        FileIconType.AUDIO -> Icons.Default.AudioFile
        FileIconType.VIDEO -> Icons.Default.VideoFile
        FileIconType.ARCHIVE -> Icons.Default.Archive
        FileIconType.PDF -> Icons.Default.PictureAsPdf
        FileIconType.ANDROID_PACKAGE -> Icons.Default.Android
    }
    val containerColor = when (iconType) {
        FileIconType.DIRECTORY,
        FileIconType.ARCHIVE,
        -> MaterialTheme.colorScheme.primaryContainer

        FileIconType.SYMBOLIC_LINK,
        FileIconType.IMAGE,
        FileIconType.AUDIO,
        FileIconType.VIDEO,
        FileIconType.ANDROID_PACKAGE,
        -> MaterialTheme.colorScheme.tertiaryContainer

        FileIconType.TEXT,
        FileIconType.PDF,
        -> MaterialTheme.colorScheme.secondaryContainer

        FileIconType.SPECIAL_FILE,
        FileIconType.GENERIC_FILE,
        -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when (iconType) {
        FileIconType.DIRECTORY,
        FileIconType.ARCHIVE,
        -> MaterialTheme.colorScheme.onPrimaryContainer

        FileIconType.SYMBOLIC_LINK,
        FileIconType.IMAGE,
        FileIconType.AUDIO,
        FileIconType.VIDEO,
        FileIconType.ANDROID_PACKAGE,
        -> MaterialTheme.colorScheme.onTertiaryContainer

        FileIconType.TEXT,
        FileIconType.PDF,
        -> MaterialTheme.colorScheme.onSecondaryContainer

        FileIconType.SPECIAL_FILE,
        FileIconType.GENERIC_FILE,
        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = icon,
                contentDescription = stringResource(entryType.resourceId()),
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
internal fun SelectionBottomBar(
    state: FileBrowserUiState,
    selectedEntries: List<FileEntry>,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onExport: ((FileEntry) -> Unit)?,
    onRename: (FileEntry) -> Unit,
    onProperties: () -> Unit,
    onMetadata: () -> Unit,
    onHardLink: () -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    FlexibleBottomAppBar(
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (state.capabilities.canCopy) {
            BottomAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ContentCopy,
                label = stringResource(R.string.yukifb_copy),
                enabled = !state.isBusy,
                onClick = onCopy,
            )
        }
        if (state.capabilities.canMove) {
            BottomAction(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                label = stringResource(R.string.yukifb_cut),
                enabled = !state.isBusy,
                onClick = onMove,
            )
        }
        if (state.capabilities.canDelete) {
            BottomAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Delete,
                label = stringResource(R.string.yukifb_delete),
                enabled = !state.isBusy,
                onClick = onDelete,
            )
        }
        val exportEntry = selectedEntries.singleOrNull()?.takeIf {
            it.type == FileEntryType.REGULAR_FILE
        }
        if (
            onExport != null &&
            state.capabilities.canAccessContent &&
            exportEntry != null
        ) {
            BottomAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Download,
                label = stringResource(R.string.yukifb_export),
                enabled = !state.isBusy,
                onClick = { onExport(exportEntry) },
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            BottomAction(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.MoreVert,
                label = stringResource(R.string.yukifb_more),
                enabled = !state.isBusy,
                onClick = { moreExpanded = true },
            )
            DropdownMenu(
                expanded = moreExpanded,
                onDismissRequest = { moreExpanded = false },
            ) {
                        if (
                            state.capabilities.canRename &&
                            selectedEntries.size == 1
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.yukifb_rename)) },
                                onClick = {
                                    moreExpanded = false
                                    onRename(selectedEntries.single())
                                },
                            )
                        }
                        if (state.capabilities.canEditUnixMetadata) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.yukifb_edit_metadata))
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                },
                                onClick = {
                                    moreExpanded = false
                                    onMetadata()
                                },
                            )
                        }
                        if (
                            state.capabilities.canCreateHardLinks &&
                            selectedEntries.size == 1 &&
                            !selectedEntries.single().isDirectory
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.yukifb_create_hard_link))
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Link, contentDescription = null)
                                },
                                onClick = {
                                    moreExpanded = false
                                    onHardLink()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.yukifb_properties)) },
                            onClick = {
                                moreExpanded = false
                                onProperties()
                            },
                        )
            }
        }
    }
}

@Composable
private fun BottomAction(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 2.dp),
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(
                text = label,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
internal fun ClipboardBottomBar(
    clipboard: FileBrowserClipboard,
    enabled: Boolean,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    val count = clipboard.entries.size
    val label = when (clipboard.mode) {
        ClipboardMode.COPY -> pluralStringResource(
            R.plurals.yukifb_clipboard_copy,
            count,
            count,
        )

        ClipboardMode.MOVE -> pluralStringResource(
            R.plurals.yukifb_clipboard_move,
            count,
            count,
        )

        ClipboardMode.HARD_LINK -> {
            stringResource(R.string.yukifb_clipboard_hard_link)
        }
    }
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onClear) {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = stringResource(R.string.yukifb_cancel_clipboard),
                )
            }
            TextButton(
                enabled = enabled,
                onClick = onPaste,
            ) {
                Text(stringResource(R.string.yukifb_paste))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CreationButton(
    capabilities: FileBrowserCapabilities,
    importEnabled: Boolean,
    enabled: Boolean,
    onCreateFile: () -> Unit,
    onCreateDirectory: () -> Unit,
    onCreateSymbolicLink: () -> Unit,
    onImport: () -> Unit,
) {
    val hasActions = capabilities.canCreateEntries ||
        capabilities.canCreateSymbolicLinks ||
        importEnabled
    if (!hasActions || !enabled) return

    var expanded by remember { mutableStateOf(false) }
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = it },
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = stringResource(R.string.yukifb_create),
                )
            }
        },
    ) {
            if (capabilities.canCreateEntries) {
                FloatingActionButtonMenuItem(
                    text = { Text(stringResource(R.string.yukifb_new_file)) },
                    icon = {
                        Icon(Icons.Default.Description, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onCreateFile()
                    },
                )
                FloatingActionButtonMenuItem(
                    text = { Text(stringResource(R.string.yukifb_new_directory)) },
                    icon = {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onCreateDirectory()
                    },
                )
            }
            if (capabilities.canCreateSymbolicLinks) {
                FloatingActionButtonMenuItem(
                    text = { Text(stringResource(R.string.yukifb_new_symbolic_link)) },
                    icon = {
                        Icon(Icons.Default.Link, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onCreateSymbolicLink()
                    },
                )
            }
            if (importEnabled) {
                FloatingActionButtonMenuItem(
                    text = { Text(stringResource(R.string.yukifb_import)) },
                    icon = {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onImport()
                    },
                )
            }
    }
}
