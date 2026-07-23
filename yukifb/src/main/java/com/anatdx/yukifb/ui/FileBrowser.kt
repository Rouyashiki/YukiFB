package com.anatdx.yukifb.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.anatdx.yukifb.R
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.state.FileBrowserAction
import com.anatdx.yukifb.state.FileBrowserController
import com.anatdx.yukifb.state.FileBrowserUiState

/**
 * Renders a file browser bound to [controller].
 *
 * Opening and importing files remain host concerns and are reported through
 * [onOpenFile] and [onImport].
 */
@Composable
fun FileBrowser(
    controller: FileBrowserController,
    onOpenFile: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
    onImport: ((FileEntry) -> Unit)? = null,
    options: FileBrowserOptions = FileBrowserDefaults.options,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    val state by controller.state.collectAsState()
    FileBrowser(
        state = state,
        onAction = controller::dispatch,
        onOpenFile = onOpenFile,
        modifier = modifier,
        onImport = onImport,
        options = options,
        topBarActions = topBarActions,
    )
}

/**
 * Stateless YukiFB surface.
 *
 * This overload is suitable for hosts that keep browser state in their own
 * ViewModel. [onAction] receives all navigation and mutation intents.
 */
@Composable
fun FileBrowser(
    state: FileBrowserUiState,
    onAction: (FileBrowserAction) -> Unit,
    onOpenFile: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
    onImport: ((FileEntry) -> Unit)? = null,
    options: FileBrowserOptions = FileBrowserDefaults.options,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }
    val selectedEntries = remember(state.entries, state.selectedIds) {
        state.entries.filter { it.id in state.selectedIds }
    }
    val unknownError = stringResource(R.string.yukifb_unknown_error)
    val errorMessage = state.error?.cause?.localizedMessage ?: unknownError
    val operationError = stringResource(R.string.yukifb_operation_failed, errorMessage)

    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbarHostState.showSnackbar(operationError)
            onAction(FileBrowserAction.ClearError)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FileBrowserTopBar(
                state = state,
                onAction = onAction,
                topBarActions = topBarActions,
            )
        },
        bottomBar = {
            when {
                state.isSelectionMode -> SelectionBottomBar(
                    state = state,
                    selectedEntries = selectedEntries,
                    onCopy = { onAction(FileBrowserAction.CopySelected) },
                    onMove = { onAction(FileBrowserAction.MoveSelected) },
                    onDelete = { dialog = BrowserDialog.DeleteSelection },
                    onRename = { entry -> dialog = BrowserDialog.Rename(entry) },
                    onProperties = {
                        dialog = BrowserDialog.Properties(selectedEntries)
                    },
                    onMetadata = {
                        dialog = BrowserDialog.Metadata(selectedEntries)
                    },
                    onHardLink = {
                        onAction(FileBrowserAction.HardLinkSelected)
                    },
                )

                state.clipboard != null -> ClipboardBottomBar(
                    clipboard = state.clipboard,
                    enabled = !state.isBusy,
                    onPaste = { onAction(FileBrowserAction.Paste) },
                    onClear = { onAction(FileBrowserAction.ClearClipboard) },
                )
            }
        },
        floatingActionButton = {
            CreationButton(
                capabilities = state.capabilities,
                importEnabled = onImport != null,
                enabled = !state.isBusy && !state.isLoading && !state.isSelectionMode,
                onCreateFile = { dialog = BrowserDialog.CreateFile },
                onCreateDirectory = { dialog = BrowserDialog.CreateDirectory },
                onCreateSymbolicLink = {
                    dialog = BrowserDialog.CreateSymbolicLink
                },
                onImport = {
                    onImport?.invoke(state.currentDirectory)
                },
            )
        },
    ) { contentPadding ->
        FileBrowserContent(
            state = state,
            options = options,
            contentPadding = contentPadding,
            onEntryClick = { entry ->
                if (state.isSelectionMode) {
                    onAction(FileBrowserAction.ToggleSelection(entry.id))
                } else if (entry.isDirectory) {
                    onAction(FileBrowserAction.NavigateTo(entry))
                } else {
                    onOpenFile(entry)
                }
            },
            onEntryLongClick = { entry ->
                onAction(FileBrowserAction.ToggleSelection(entry.id))
            },
            onEntryProperties = { entry ->
                dialog = BrowserDialog.Properties(listOf(entry))
            },
            onBreadcrumbClick = { entry ->
                onAction(FileBrowserAction.NavigateTo(entry))
            },
        )
    }

    when (val currentDialog = dialog) {
        BrowserDialog.CreateFile -> NameInputDialog(
            title = stringResource(R.string.yukifb_new_file),
            confirmLabel = stringResource(R.string.yukifb_create),
            onDismiss = { dialog = null },
            onConfirm = { name ->
                onAction(FileBrowserAction.CreateFile(name))
                dialog = null
            },
        )

        BrowserDialog.CreateDirectory -> NameInputDialog(
            title = stringResource(R.string.yukifb_new_directory),
            confirmLabel = stringResource(R.string.yukifb_create),
            onDismiss = { dialog = null },
            onConfirm = { name ->
                onAction(FileBrowserAction.CreateDirectory(name))
                dialog = null
            },
        )

        BrowserDialog.CreateSymbolicLink -> SymbolicLinkDialog(
            onDismiss = { dialog = null },
            onConfirm = { name, target ->
                onAction(FileBrowserAction.CreateSymbolicLink(name, target))
                dialog = null
            },
        )

        BrowserDialog.DeleteSelection -> DeleteConfirmationDialog(
            count = selectedEntries.size,
            onDismiss = { dialog = null },
            onConfirm = {
                onAction(FileBrowserAction.DeleteSelected)
                dialog = null
            },
        )

        is BrowserDialog.Rename -> NameInputDialog(
            title = stringResource(R.string.yukifb_rename),
            initialValue = currentDialog.entry.name,
            confirmLabel = stringResource(R.string.yukifb_rename),
            onDismiss = { dialog = null },
            onConfirm = { name ->
                onAction(FileBrowserAction.Rename(currentDialog.entry, name))
                dialog = null
            },
        )

        is BrowserDialog.Properties -> PropertiesDialog(
            entries = currentDialog.entries,
            onDismiss = { dialog = null },
        )

        is BrowserDialog.Metadata -> UnixMetadataDialog(
            entries = currentDialog.entries,
            onDismiss = { dialog = null },
            onConfirm = { patch ->
                onAction(
                    FileBrowserAction.UpdateMetadata(
                        entryIds = currentDialog.entries.map { it.id },
                        patch = patch,
                    ),
                )
                dialog = null
            },
        )

        null -> Unit
    }
}

private sealed interface BrowserDialog {
    data object CreateFile : BrowserDialog

    data object CreateDirectory : BrowserDialog

    data object CreateSymbolicLink : BrowserDialog

    data object DeleteSelection : BrowserDialog

    data class Rename(val entry: FileEntry) : BrowserDialog

    data class Properties(val entries: List<FileEntry>) : BrowserDialog

    data class Metadata(val entries: List<FileEntry>) : BrowserDialog
}
