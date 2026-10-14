package com.anatdx.yukifb.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.anatdx.yukifb.R
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.state.FileBrowserAction
import com.anatdx.yukifb.state.FileBrowserController
import com.anatdx.yukifb.state.FileBrowserOperation
import com.anatdx.yukifb.state.FileBrowserUiState
import com.anatdx.yukifb.state.browserBackAction
import com.anatdx.yukifb.state.visibleEntries

/**
 * Renders a file browser bound to [controller].
 *
 * Opening, importing, and exporting files remain host concerns and are reported
 * through [onOpenFile], [onImport], and [onExport]. When [onClose] is supplied,
 * the root directory shows a host-level back action while child directories
 * continue to navigate up.
 */
@Composable
fun FileBrowser(
    controller: FileBrowserController,
    onOpenFile: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
    onImport: ((FileEntry) -> Unit)? = null,
    onExport: ((FileEntry) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    options: FileBrowserOptions = FileBrowserDefaults.options,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    FileBrowser(
        controller = controller,
        presentationState = rememberFileBrowserPresentationState(controller),
        onOpenFile = onOpenFile, modifier = modifier, onImport = onImport,
        onExport = onExport, onClose = onClose, options = options,
        topBarActions = topBarActions,
    )
}

/** Binds a controller while allowing its presentation to outlive the visible browser. */
@Composable
fun FileBrowser(
    controller: FileBrowserController,
    presentationState: FileBrowserPresentationState,
    onOpenFile: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
    onImport: ((FileEntry) -> Unit)? = null,
    onExport: ((FileEntry) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    options: FileBrowserOptions = FileBrowserDefaults.options,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    val state by controller.state.collectAsState()
    FileBrowser(
        state = state, presentationState = presentationState,
        onAction = controller::dispatch, onOpenFile = onOpenFile,
        modifier = modifier, onImport = onImport, onExport = onExport,
        onClose = onClose, options = options, topBarActions = topBarActions,
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
    onExport: ((FileEntry) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    options: FileBrowserOptions = FileBrowserDefaults.options,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    FileBrowser(
        state = state, presentationState = rememberFileBrowserPresentationState(),
        onAction = onAction, onOpenFile = onOpenFile, modifier = modifier,
        onImport = onImport, onExport = onExport, onClose = onClose,
        options = options, topBarActions = topBarActions,
    )
}

/** Stateless browser with host-retained scroll, transition and menu presentation. */
@Composable
fun FileBrowser(
    state: FileBrowserUiState,
    presentationState: FileBrowserPresentationState,
    onAction: (FileBrowserAction) -> Unit,
    onOpenFile: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
    onImport: ((FileEntry) -> Unit)? = null,
    onExport: ((FileEntry) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    options: FileBrowserOptions = FileBrowserDefaults.options,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    val snackbarHostState = remember(presentationState) { SnackbarHostState() }
    val page = presentationState.displayPage(state)
    SideEffect { presentationState.observe(state) }
    val selectedEntries = remember(
        state.entries,
        state.search?.results,
        state.selectedIds,
        state.sort,
        state.showHiddenFiles,
    ) {
        state.visibleEntries().filter { it.id in state.selectedIds }
    }
    val unknownError = stringResource(R.string.yukifb_unknown_error)
    val errorMessage = state.error?.cause?.localizedMessage ?: unknownError
    val operationError = stringResource(R.string.yukifb_operation_failed, errorMessage)

    LaunchedEffect(presentationState, state.error) {
        if (state.error != null) {
            snackbarHostState.showSnackbar(operationError)
            if (state.error.operation != FileBrowserOperation.LOAD) onAction(FileBrowserAction.ClearError)
        }
    }

    val parent = presentationState.parentPreview(state)
    val directoryBack = !presentationState.creationExpanded && browserBackAction(state) == FileBrowserAction.NavigateUp
    val directoryGesture = remember(presentationState, presentationState.back.generation) { directoryBack }
    key(presentationState) {
        YukiFBScene(
            sceneKey = page.currentDirectory.id,
            direction = presentationState.direction(page),
            backState = if (directoryGesture) presentationState.back else null,
            modifier = modifier,
            backPreview = parent?.let { preview ->
                {
                    FileBrowserPreview(
                        preview, presentationState, options = options,
                        showCloseButton = onClose != null, importEnabled = onImport != null,
                        topBarActions = topBarActions,
                    )
                }
            },
        ) {
            if (presentationState.isShowingParentPreview) {
                // The controller still owns the source directory until loading succeeds.
                FileBrowserPreview(
                    page, presentationState, options = options,
                    showCloseButton = onClose != null, importEnabled = onImport != null,
                    topBarActions = topBarActions,
                )
                return@YukiFBScene
            }
            Scaffold(
                containerColor = yukiFBColors.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Box(Modifier.graphicsLayer {
                        if (!directoryBack) alpha = 1f - presentationState.back.progress * 0.35f
                    }) {
                        FileBrowserTopBar(
                            state = page,
                            onAction = onAction,
                            onClose = onClose,
                            topBarActions = topBarActions,
                        )
                    }
                },
                bottomBar = {
                    val effects = yukiFBEffectsSpec<Float>()
                    val spatial = yukiFBSpatialSpec<IntOffset>()
                    val size = yukiFBSpatialSpec<IntSize>()
                    val moving = expressiveYukiFB && LocalYukiFBMotion.current == YukiFBMotion.Full
                    AnimatedContent(
                        targetState = page,
                        contentKey = { if (it.isSelectionMode) 2 else if (it.clipboard != null) 1 else 0 },
                        transitionSpec = {
                            val enter = if (moving) fadeIn(effects) + slideInVertically(spatial) { it / 3 } else fadeIn(effects)
                            val exit = if (moving) fadeOut(effects) + slideOutVertically(spatial) { it / 3 } else fadeOut(effects)
                            enter.togetherWith(exit).using(SizeTransform { _, _ -> size })
                        },
                        label = "browser actions",
                    ) { bar ->
                        Box(
                            (if (bar.barMode() == page.barMode()) Modifier else Modifier.blockPreviewInput())
                                .graphicsLayer { if (!directoryBack) alpha = 1f - presentationState.back.progress * 0.35f },
                        ) {
                            when {
                                bar.isSelectionMode -> SelectionBottomBar(
                                    state = bar,
                                    selectedEntries = selectedEntries,
                                    onCopy = { onAction(FileBrowserAction.CopySelected) },
                                    onMove = { onAction(FileBrowserAction.MoveSelected) },
                                    onDelete = { presentationState.dialog = BrowserDialog.DeleteSelection },
                                    onExport = onExport,
                                    onRename = { entry -> presentationState.dialog = BrowserDialog.Rename(entry) },
                                    onProperties = {
                                        presentationState.dialog = BrowserDialog.Properties(selectedEntries)
                                    },
                                    onMetadata = {
                                        presentationState.dialog = BrowserDialog.Metadata(selectedEntries)
                                    },
                                    onHardLink = {
                                        onAction(FileBrowserAction.HardLinkSelected)
                                    },
                                )

                                bar.clipboard != null -> ClipboardBottomBar(
                                    clipboard = bar.clipboard,
                                    enabled = !page.isBusy,
                                    onPaste = { onAction(FileBrowserAction.Paste) },
                                    onClear = { onAction(FileBrowserAction.ClearClipboard) },
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    val effects = yukiFBEffectsSpec<Float>()
                    AnimatedVisibility(
                        modifier = Modifier.graphicsLayer {
                            if (presentationState.creationExpanded) alpha = 1f - presentationState.back.progress * 0.35f
                        },
                        visible = page.search == null && !page.isBusy && !page.isSelectionMode,
                        enter = fadeIn(effects), exit = fadeOut(effects),
                    ) {
                        CreationButton(
                            capabilities = state.capabilities,
                            importEnabled = onImport != null,
                            enabled = !page.isBusy && !page.isLoading && !page.isSelectionMode,
                            expanded = presentationState.creationExpanded,
                            onExpandedChange = { presentationState.creationExpanded = it },
                            onCreateFile = { presentationState.dialog = BrowserDialog.CreateFile },
                            onCreateDirectory = { presentationState.dialog = BrowserDialog.CreateDirectory },
                            onCreateSymbolicLink = {
                                presentationState.dialog = BrowserDialog.CreateSymbolicLink
                            },
                            onImport = {
                                onImport?.invoke(state.currentDirectory)
                            },
                        )
                    }
                },
            ) { contentPadding ->
                key(page.currentDirectory.id) {
                    FileBrowserContent(
                        state = page,
                        options = options,
                        listState = presentationState.listState(page),
                        onRetry = { onAction(FileBrowserAction.Refresh) },
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
                            presentationState.dialog = BrowserDialog.Properties(listOf(entry))
                        },
                        onBreadcrumbClick = { entry ->
                            onAction(FileBrowserAction.NavigateTo(entry))
                        },
                    )
                }
            }

        }

    }

    when (val currentDialog = presentationState.dialog) {
        BrowserDialog.CreateFile -> NameInputDialog(
            title = stringResource(R.string.yukifb_new_file),
            confirmLabel = stringResource(R.string.yukifb_create),
            onDismiss = { presentationState.dialog = null },
            onConfirm = { name ->
                onAction(FileBrowserAction.CreateFile(name))
                presentationState.dialog = null
            },
        )

        BrowserDialog.CreateDirectory -> NameInputDialog(
            title = stringResource(R.string.yukifb_new_directory),
            confirmLabel = stringResource(R.string.yukifb_create),
            onDismiss = { presentationState.dialog = null },
            onConfirm = { name ->
                onAction(FileBrowserAction.CreateDirectory(name))
                presentationState.dialog = null
            },
        )

        BrowserDialog.CreateSymbolicLink -> SymbolicLinkDialog(
            onDismiss = { presentationState.dialog = null },
            onConfirm = { name, target ->
                onAction(FileBrowserAction.CreateSymbolicLink(name, target))
                presentationState.dialog = null
            },
        )

        BrowserDialog.DeleteSelection -> DeleteConfirmationDialog(
            count = selectedEntries.size,
            onDismiss = { presentationState.dialog = null },
            onConfirm = {
                onAction(FileBrowserAction.DeleteSelected)
                presentationState.dialog = null
            },
        )

        is BrowserDialog.Rename -> NameInputDialog(
            title = stringResource(R.string.yukifb_rename),
            initialValue = currentDialog.entry.name,
            confirmLabel = stringResource(R.string.yukifb_rename),
            onDismiss = { presentationState.dialog = null },
            onConfirm = { name ->
                onAction(FileBrowserAction.Rename(currentDialog.entry, name))
                presentationState.dialog = null
            },
        )

        is BrowserDialog.Properties -> PropertiesDialog(
            entries = currentDialog.entries,
            onDismiss = { presentationState.dialog = null },
        )

        is BrowserDialog.Metadata -> UnixMetadataDialog(
            entries = currentDialog.entries,
            onDismiss = { presentationState.dialog = null },
            onConfirm = { patch ->
                onAction(
                    FileBrowserAction.UpdateMetadata(
                        entryIds = currentDialog.entries.map { it.id },
                        patch = patch,
                    ),
                )
                presentationState.dialog = null
            },
        )

        null -> Unit
    }
}

internal sealed interface BrowserDialog {
    data object CreateFile : BrowserDialog

    data object CreateDirectory : BrowserDialog

    data object CreateSymbolicLink : BrowserDialog

    data object DeleteSelection : BrowserDialog

    data class Rename(val entry: FileEntry) : BrowserDialog

    data class Properties(val entries: List<FileEntry>) : BrowserDialog

    data class Metadata(val entries: List<FileEntry>) : BrowserDialog
}

/**
 * Draws a non-interactive browser preview with the retained directory scroll position.
 * No controller is observed and no operation or focus request is issued. Hosts can use
 * this as [YukiFBScene]'s parent preview while displaying a text or binary viewer.
 */
@Composable
fun FileBrowserPreview(
    state: FileBrowserUiState,
    presentationState: FileBrowserPresentationState,
    modifier: Modifier = Modifier,
    options: FileBrowserOptions = FileBrowserDefaults.options,
    showCloseButton: Boolean = false,
    importEnabled: Boolean = false,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    CompositionLocalProvider(LocalYukiFBMotion provides YukiFBMotion.None) {
        Scaffold(
            modifier = modifier.blockPreviewInput(),
            containerColor = yukiFBColors.background,
            topBar = {
                FileBrowserTopBar(state, {}, if (showCloseButton) ({}) else null, topBarActions, preview = true)
            },
            bottomBar = {
                state.clipboard?.takeUnless { state.isSelectionMode }?.let {
                    ClipboardBottomBar(it, enabled = true, onPaste = {}, onClear = {})
                }
            },
            floatingActionButton = {
                if (state.search == null && !state.isBusy && !state.isSelectionMode) {
                    CreationButton(
                        capabilities = state.capabilities, importEnabled = importEnabled,
                        enabled = true, expanded = false, onExpandedChange = {},
                        onCreateFile = {}, onCreateDirectory = {}, onCreateSymbolicLink = {}, onImport = {},
                    )
                }
            },
        ) { padding ->
            FileBrowserContent(
                state = state, options = options, contentPadding = padding,
                listState = presentationState.listState(state), onRetry = {},
                onEntryClick = {}, onEntryLongClick = {}, onEntryProperties = {},
                onBreadcrumbClick = {},
            )
        }
    }
}

private fun FileBrowserUiState.barMode(): Int = if (isSelectionMode) 2 else if (clipboard != null) 1 else 0
