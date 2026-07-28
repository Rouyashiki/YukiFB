package com.anatdx.yukifb.state

import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.UnixMetadataPatch

/** User intent understood by [FileBrowserController]. */
sealed interface FileBrowserAction {
    data class NavigateTo(val directory: FileEntry) : FileBrowserAction

    data object NavigateUp : FileBrowserAction

    data object Refresh : FileBrowserAction

    /** Opens the recursive filename-search surface. */
    data object StartSearch : FileBrowserAction

    /** Replaces the active filename query. */
    data class UpdateSearchQuery(val query: String) : FileBrowserAction

    /** Cancels and closes the active search. */
    data object StopSearch : FileBrowserAction

    data class ToggleSelection(val entryId: EntryId) : FileBrowserAction

    data object SelectAll : FileBrowserAction

    data object ClearSelection : FileBrowserAction

    data class SetSort(val sort: FileBrowserSort) : FileBrowserAction

    data object ToggleHiddenFiles : FileBrowserAction

    data object CopySelected : FileBrowserAction

    data object MoveSelected : FileBrowserAction

    data object HardLinkSelected : FileBrowserAction

    data object Paste : FileBrowserAction

    data object ClearClipboard : FileBrowserAction

    data class CreateFile(val name: String) : FileBrowserAction

    data class CreateDirectory(val name: String) : FileBrowserAction

    data class CreateSymbolicLink(
        val name: String,
        val target: String,
    ) : FileBrowserAction

    data class Rename(
        val entry: FileEntry,
        val newName: String,
    ) : FileBrowserAction

    data object DeleteSelected : FileBrowserAction

    data class UpdateMetadata(
        val entryIds: List<EntryId>,
        val patch: UnixMetadataPatch,
    ) : FileBrowserAction

    data object ClearError : FileBrowserAction
}
