package com.anatdx.yukifb.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.state.FileBrowserAction
import com.anatdx.yukifb.state.FileBrowserOperation
import com.anatdx.yukifb.state.FileBrowserUiState
import com.anatdx.yukifb.state.browserBackAction

/**
 * Browser presentation retained while a host shows an editor or viewer.
 * Create a separate instance for each backend session. Cached listings are
 * non-interactive previews, never authoritative backend data or file contents.
 */
@Stable
class FileBrowserPresentationState {
    internal val back = YukiFBBackState()
    internal var creationExpanded by mutableStateOf(false)
    internal var dialog by mutableStateOf<BrowserDialog?>(null)
    private var lastPage: FileBrowserUiState? = null
    private var stablePage: FileBrowserUiState? = null
    private var previews by mutableStateOf<Map<EntryId, FileBrowserUiState>>(emptyMap())
    private val scrollStates = linkedMapOf<Pair<EntryId, Boolean>, LazyListState>()
    private var pendingParent by mutableStateOf<FileBrowserUiState?>(null)
    private var pendingObservedLoading = false
    private var lastDirection = 1

    internal fun listState(page: FileBrowserUiState): LazyListState {
        val key = page.currentDirectory.id to (page.search != null)
        return scrollStates.getOrPut(key) {
            if (scrollStates.size >= 64) scrollStates.remove(scrollStates.keys.first())
            LazyListState()
        }
    }

    internal fun direction(page: FileBrowserUiState): Int {
        val previous = lastPage ?: return 1
        return if (previous.currentDirectory.id == page.currentDirectory.id) lastDirection
        else if (previous.breadcrumbs.any { it.id == page.currentDirectory.id }) -1 else 1
    }

    internal fun observe(page: FileBrowserUiState) {
        lastDirection = direction(page)
        val previous = lastPage
        if (page.isMutating || (previous?.isMutating == true && !page.isMutating) ||
            (previous?.currentDirectory?.id == page.currentDirectory.id && previous.entries !== page.entries)) {
            previews = emptyMap()
            stablePage = null
        }
        if (previous?.currentDirectory?.id != page.currentDirectory.id && previous != null) {
            stablePage?.takeIf { it.currentDirectory.id == previous.currentDirectory.id }?.let(::cache)
            creationExpanded = false
            dialog = null
        }
        if (page.isBusy || page.isLoading || page.isSelectionMode || page.search != null) {
            creationExpanded = false
        }
        if (pendingParent != null) {
            pendingObservedLoading = pendingObservedLoading || page.isLoading
            if (page.currentDirectory.id == pendingParent?.currentDirectory?.id ||
                (!page.isLoading && (pendingObservedLoading || page.error != null))) {
                pendingParent = null
                pendingObservedLoading = false
            }
        }
        lastPage = page
        if (!page.isLoading && !page.isBusy && page.error == null) stablePage = page
    }

    private fun cache(page: FileBrowserUiState) {
        if (page.isBusy || page.isLoading || page.error != null || page.entries.size > 2048) return
        previews = LinkedHashMap(previews).apply {
            remove(page.currentDirectory.id)
            put(page.currentDirectory.id, page.copy(selectedIds = emptySet(), search = null))
            while (size > 8) remove(keys.first())
        }
    }

    internal fun parentPreview(page: FileBrowserUiState): FileBrowserUiState? {
        val parent = page.breadcrumbs.dropLast(1).lastOrNull() ?: return null
        return previews[parent.id]?.copy(sort = page.sort, showHiddenFiles = page.showHiddenFiles)
            ?: page.copy(
                currentDirectory = parent,
                breadcrumbs = page.breadcrumbs.dropLast(1),
                entries = emptyList(), selectedIds = emptySet(), search = null,
                error = null, isLoading = true, activeOperation = null,
            )
    }

    internal fun commitParentPreview(page: FileBrowserUiState) {
        if (back.isActive) {
            pendingParent = parentPreview(page)
            pendingObservedLoading = false
        }
    }

    internal fun displayPage(page: FileBrowserUiState): FileBrowserUiState =
        pendingParent?.copy(isLoading = true, activeOperation = null) ?: page

    internal val isShowingParentPreview: Boolean
        get() = pendingParent != null

    /** Drops stale ancestor previews after a mutation performed directly by the host. */
    fun invalidatePreviews() {
        previews = emptyMap()
        stablePage = null
    }
}

private val FileBrowserUiState.isMutating: Boolean
    get() = activeOperation != null && activeOperation != FileBrowserOperation.LOAD &&
        activeOperation != FileBrowserOperation.SEARCH

/** Remembers presentation for [owner], normally the controller or backend instance. */
@Composable
fun rememberFileBrowserPresentationState(owner: Any? = null): FileBrowserPresentationState =
    remember(owner) { FileBrowserPresentationState() }

/**
 * Explicitly handles menus, selection, search and parent navigation. Root back is
 * left to the host. Disable this handler while a host editor or modal is visible.
 */
@Composable
fun FileBrowserBackHandler(
    state: FileBrowserUiState,
    presentationState: FileBrowserPresentationState,
    onAction: (FileBrowserAction) -> Unit,
    enabled: Boolean = true,
    predictiveBackEnabled: Boolean = true,
) {
    val action = browserBackAction(state)
    val menu = presentationState.creationExpanded
    YukiFBBackHandler(
        state = presentationState.back,
        targetKey = listOf(state.currentDirectory.id, action, state.selectedIds, state.search?.query, menu),
        enabled = enabled && presentationState.dialog == null && !state.isBusy && !state.isLoading && (menu || action != null),
        predictiveBackEnabled = predictiveBackEnabled,
    ) {
        if (menu) presentationState.creationExpanded = false
        else if (action != null) {
            if (action == FileBrowserAction.NavigateUp) presentationState.commitParentPreview(state)
            onAction(action)
        }
    }
}
