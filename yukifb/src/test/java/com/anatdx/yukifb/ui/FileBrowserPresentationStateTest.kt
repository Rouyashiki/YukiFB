package com.anatdx.yukifb.ui

import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.FileEntryType
import com.anatdx.yukifb.state.FileBrowserOperation
import com.anatdx.yukifb.state.FileBrowserUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileBrowserPresentationStateTest {
    private val root = FileEntry(EntryId("root"), null, "root", FileEntryType.DIRECTORY)
    private val child = FileEntry(EntryId("child"), root.id, "child", FileEntryType.DIRECTORY)
    private val rootPage = FileBrowserUiState(root, entries = listOf(child))
    private val childPage = FileBrowserUiState(child, breadcrumbs = listOf(root, child))

    @Test
    fun loadingBetweenDirectoriesPreservesTheLastCompleteAncestor() {
        val presentation = FileBrowserPresentationState()
        presentation.observe(rootPage)
        presentation.observe(rootPage.copy(isLoading = true))
        presentation.observe(childPage)
        val preview = requireNotNull(presentation.parentPreview(childPage))
        assertFalse(preview.isLoading)
        assertEquals(listOf(child), preview.entries)
        assertEquals(-1, presentation.direction(rootPage))
    }

    @Test
    fun mutationsAndExternalInvalidationRemoveStaleAncestors() {
        val presentation = FileBrowserPresentationState()
        presentation.observe(rootPage)
        presentation.observe(childPage)
        presentation.observe(childPage.copy(activeOperation = FileBrowserOperation.DELETE))
        presentation.observe(childPage)
        assertTrue(requireNotNull(presentation.parentPreview(childPage)).isLoading)
        presentation.observe(rootPage)
        presentation.observe(childPage)
        presentation.invalidatePreviews()
        assertTrue(requireNotNull(presentation.parentPreview(childPage)).isLoading)
    }

    @Test
    fun largeAncestorsAndDifferentSessionsNeverLeakCachedListings() {
        val presentation = FileBrowserPresentationState()
        presentation.observe(rootPage.copy(entries = List(2049) { child.copy(id = EntryId("item-$it")) }))
        presentation.observe(childPage)
        assertTrue(requireNotNull(presentation.parentPreview(childPage)).isLoading)
        presentation.observe(rootPage)
        presentation.observe(childPage)
        assertFalse(requireNotNull(presentation.parentPreview(childPage)).isLoading)
        assertTrue(requireNotNull(FileBrowserPresentationState().parentPreview(childPage)).isLoading)
    }
}
