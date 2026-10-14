package com.anatdx.yukifb.ui

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.anatdx.yukifb.backend.CopyEntriesCapability
import com.anatdx.yukifb.backend.FileBrowserBackend
import com.anatdx.yukifb.backend.FileBrowserCapabilities
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.FileEntryType
import com.anatdx.yukifb.state.FileBrowserAction
import com.anatdx.yukifb.state.FileBrowserController
import com.anatdx.yukifb.state.FileBrowserSearchState
import com.anatdx.yukifb.state.FileBrowserUiState
import com.anatdx.yukifb.state.FileBrowserClipboard
import com.anatdx.yukifb.state.ClipboardMode
import com.anatdx.yukifb.state.TextFileEditorState
import com.anatdx.yukifb.state.rememberFileBrowserController
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs

class FileBrowserInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val backend = BrowserFixture()
    private val input = GestureInput()
    private val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
    }
    private lateinit var controller: FileBrowserController
    private lateinit var presentation: FileBrowserPresentationState
    private var hostBacks = 0

    private fun browser(motion: YukiFBMotion = YukiFBMotion.Full) {
        compose.runOnUiThread {
            owner.navigationEventDispatcher.addInput(input)
            owner.navigationEventDispatcher.addHandler(object : NavigationEventHandler<NavigationEventInfo>(
                NavigationEventInfo.None, isBackEnabled = true,
            ) {
                override fun onBackCompleted() { hostBacks++ }
            })
        }
        compose.setContent {
            MaterialTheme {
                YukiFBProvider(
                    motion = motion,
                    colors = YukiFBThemeDefaults.colors().copy(background = Color.Transparent),
                    sceneBackground = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) },
                ) {
                    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                        controller = rememberFileBrowserController(backend, backend.root)
                        val state by controller.state.collectAsState()
                        presentation = rememberFileBrowserPresentationState(controller)
                        FileBrowserBackHandler(state, presentation, controller::dispatch)
                        FileBrowser(controller, presentation, onOpenFile = {})
                    }
                }
            }
        }
    }

    @Test
    fun directoryGestureCancellationDoesNotReadOrNavigateAndCommitReadsOnce() {
        browser()
        compose.onNodeWithText("etc").performClick()
        compose.waitForIdle()
        val reads = backend.reads
        compose.runOnIdle { input.start(); input.progress(0.55f) }
        screenshot("directory-back-left")
        compose.runOnIdle {
            assertEquals(backend.child.id, controller.state.value.currentDirectory.id)
            assertEquals(reads, backend.reads)
            assertFalse(requireNotNull(presentation.parentPreview(controller.state.value)).isLoading)
            input.cancel()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(backend.child.id, controller.state.value.currentDirectory.id)
            input.start(NavigationEvent.EDGE_RIGHT)
            input.progress(0.7f, NavigationEvent.EDGE_RIGHT)
        }
        screenshot("directory-back-right")
        compose.runOnIdle { input.complete() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(backend.root.id, controller.state.value.currentDirectory.id)
            assertEquals(reads + 1, backend.reads)
            assertEquals(0, hostBacks)
            input.complete()
            assertEquals(1, hostBacks)
        }
    }

    @Test
    fun committedDirectoryStaysLiveUntilSettledAndSubmitsOnlyOnce() {
        browser()
        compose.onNodeWithText("etc").performClick()
        compose.waitForIdle()
        val reads = backend.reads
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { input.start(); input.progress(0.6f) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { input.complete(); input.complete() }
        repeat(3) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertEquals("Page changed before settlement, frame $frame", backend.child.id, controller.state.value.currentDirectory.id)
                assertEquals(reads, backend.reads)
            }
            screenshot("commit-live-$frame")
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {
            assertEquals(backend.root.id, controller.state.value.currentDirectory.id)
            assertEquals(reads + 1, backend.reads)
            assertEquals(0, hostBacks)
        }
        screenshot("commit-parent")
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun newGestureDuringCancellationDoesNotInheritTheOldSettlement() {
        browser()
        compose.onNodeWithText("etc").performClick()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { input.start(); input.progress(0.7f); input.cancel() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { input.start(NavigationEvent.EDGE_RIGHT); input.progress(0.4f, NavigationEvent.EDGE_RIGHT) }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertTrue(presentation.back.isActive)
            assertEquals(0.4f, presentation.back.progress)
            input.cancel()
        }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun pendingParentCannotPasteUntilItsDirectoryIsCommitted() {
        val (loading, pastePosition) = returnToPendingParent()
        compose.onRoot().performTouchInput { click(pastePosition) }
        compose.runOnIdle {
            assertEquals(0, backend.copies)
            assertEquals(backend.child.id, controller.state.value.currentDirectory.id)
            assertTrue(controller.state.value.isLoading)
            loading.complete(Unit)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(backend.root.id, controller.state.value.currentDirectory.id)
            assertFalse(presentation.isShowingParentPreview)
        }
        compose.onNodeWithText("Paste").performClick()
        compose.runOnIdle { assertEquals(listOf(backend.root.id), backend.copyDestinations) }
    }

    @Test
    fun failedParentLoadRestoresInteractionWithTheSourceDirectory() {
        val (loading, pastePosition) = returnToPendingParent()
        compose.onRoot().performTouchInput { click(pastePosition) }
        compose.runOnIdle {
            assertEquals(0, backend.copies)
            backend.failRoot = true
            loading.complete(Unit)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(backend.child.id, controller.state.value.currentDirectory.id)
            assertFalse(presentation.isShowingParentPreview)
        }
        compose.onNodeWithText("Could not open the folder").assertIsDisplayed()
        compose.onNodeWithText("Paste").performClick()
        compose.runOnIdle { assertEquals(listOf(backend.child.id), backend.copyDestinations) }
    }

    private fun returnToPendingParent(): Pair<CompletableDeferred<Unit>, Offset> {
        browser()
        compose.onNodeWithText("init.rc").performTouchInput { longClick() }
        compose.onNodeWithText("Copy").performClick()
        compose.onNodeWithText("etc").performClick()
        val pastePosition = compose.onNodeWithText("Paste").fetchSemanticsNode().boundsInRoot.center
        val loading = CompletableDeferred<Unit>()
        compose.runOnIdle {
            backend.rootLoading = loading
            input.start()
            input.progress(0.6f)
            input.complete()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(presentation.isShowingParentPreview)
            assertTrue(controller.state.value.isLoading)
        }
        return loading to pastePosition
    }

    @Test
    fun selectionConsumesBackBeforeSearchAndParent() {
        browser()
        compose.onNodeWithText("etc").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.runOnIdle { input.start(); input.progress(0.5f); input.cancel() }
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.runOnIdle { input.complete() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(controller.state.value.selectedIds.isEmpty())
            assertEquals(0, hostBacks)
        }
    }

    @Test
    fun retainedPresentationHandlesFirstOrdinaryBackAfterHandlerRemount() {
        val showBrowser = mutableStateOf(true)
        val actions = mutableListOf<FileBrowserAction>()
        compose.runOnUiThread { owner.navigationEventDispatcher.addInput(input) }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                    val retained = rememberFileBrowserPresentationState()
                    if (showBrowser.value) {
                        FileBrowserBackHandler(
                            state = FileBrowserUiState(backend.child, breadcrumbs = listOf(backend.root, backend.child)),
                            presentationState = retained,
                            onAction = { actions += it },
                        )
                    }
                }
            }
        }
        repeat(2) { returnCount ->
            compose.runOnIdle { showBrowser.value = false }
            compose.waitForIdle()
            compose.runOnIdle { showBrowser.value = true }
            compose.waitForIdle()
            // Button/keyboard back has no preceding gesture-start event.
            compose.runOnIdle {
                input.complete()
                assertEquals(List(returnCount + 1) { FileBrowserAction.NavigateUp }, actions)
            }
        }
    }

    @Test
    fun searchAndClipboardSurviveTheirModeTransitions() {
        browser(YukiFBMotion.Reduced)
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Search this folder and subfolders").performTextInput("init")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("init.rc").performTouchInput { longClick() }
        compose.onNodeWithText("Copy").performClick()
        compose.onNodeWithText("1 item ready to copy").assertIsDisplayed()
        screenshot("search-clipboard")
        compose.runOnIdle { input.start(); input.progress(0.6f); input.cancel() }
        compose.runOnIdle { assertEquals("init", controller.state.value.search?.query) }
        compose.runOnIdle { input.complete() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(null, controller.state.value.search)
            assertEquals(listOf(backend.text), controller.state.value.clipboard?.entries)
            assertEquals(0, hostBacks)
        }
        compose.onNodeWithText("Paste").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, backend.copies) }
    }

    @Test
    fun expressiveCreationMenuConsumesBackWithoutLeavingTheDirectory() {
        var actions = 0
        compose.runOnUiThread { owner.navigationEventDispatcher.addInput(input) }
        compose.setContent {
            MaterialTheme {
                YukiFBProvider {
                    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                        val page = FileBrowserUiState(backend.root, capabilities = FileBrowserCapabilities(canCreateEntries = true))
                        val state = rememberFileBrowserPresentationState()
                        FileBrowserBackHandler(page, state, { actions++ })
                        FileBrowser(page, state, { actions++ }, {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Create").performClick()
        compose.onNodeWithText("New directory").assertIsDisplayed()
        compose.runOnIdle { input.start(); input.progress(0.4f); input.cancel() }
        compose.onNodeWithText("New directory").assertIsDisplayed()
        compose.runOnIdle { input.complete() }
        compose.onNodeWithText("New directory").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, actions) }
    }

    @Test
    fun chineseLargeFontAndTransparentContainersRemainUsableAt320dp() {
        compose.setContent {
            val context = LocalContext.current
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }
            val localized = context.createConfigurationContext(configuration)
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, 1.5f),
            ) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).fillMaxHeight().background(Color(0xffbecfe5))) {
                        val colors = YukiFBThemeDefaults.colors()
                        YukiFBProvider(colors = colors.copy(background = Color.Transparent, listContainer = colors.listContainer.copy(alpha = 0.65f))) {
                            FileBrowser(FileBrowserUiState(backend.root, entries = listOf(backend.child, backend.text),
                                clipboard = FileBrowserClipboard(listOf(backend.text), ClipboardMode.COPY)), {}, {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("粘贴").assertIsDisplayed()
        compose.onNodeWithText("init.rc").assertIsDisplayed()
        screenshot("chinese-narrow-largefont-transparent")
    }

    @Test
    fun targetChangeDuringGestureCannotApplyTheOldAction() {
        val page = mutableStateOf(FileBrowserUiState(backend.child, breadcrumbs = listOf(backend.root, backend.child)))
        var actions = 0
        compose.runOnUiThread { owner.navigationEventDispatcher.addInput(input) }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                    FileBrowserBackHandler(page.value, rememberFileBrowserPresentationState(), { actions++ })
                }
            }
        }
        compose.runOnIdle { input.start(); input.progress(0.3f) }
        compose.runOnIdle { page.value = page.value.copy(search = FileBrowserSearchState(query = "new")) }
        compose.waitForIdle()
        compose.runOnIdle { input.progress(0.8f) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { input.complete(); assertEquals(0, actions) }
    }

    @Test
    fun loadingErrorKeepsSourceAndRetryRemainsAfterSnackbar() {
        backend.failChild = true
        browser()
        compose.onNodeWithText("etc").performClick()
        compose.mainClock.advanceTimeBy(12_000)
        compose.onNodeWithText("Could not open the folder").assertIsDisplayed()
        compose.onNodeWithText("etc").assertIsDisplayed()
        screenshot("load-error")
        compose.runOnIdle { backend.failChild = false }
        compose.onNodeWithText("Retry").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(backend.child.id, controller.state.value.currentDirectory.id) }
    }

    @Test
    fun aThousandEntriesStayLazyAndScrollSurvivesDirectoryReturn() {
        backend.large = true
        browser(YukiFBMotion.None)
        compose.onNodeWithText("file-0999.txt").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(980)
        compose.onNodeWithText("file-0980.txt").assertIsDisplayed()
        compose.runOnIdle { controller.dispatch(FileBrowserAction.NavigateTo(backend.child)) }
        compose.waitForIdle()
        compose.runOnIdle { input.complete() }
        compose.waitForIdle()
        compose.onNodeWithText("file-0980.txt").assertIsDisplayed()
    }

    @Test
    fun styleMatrixRendersAndReadOnlyCapabilitiesStayHidden() {
        val style = mutableStateOf(YukiFBStyle.Classic)
        val dark = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                YukiFBProvider(style = style.value) {
                    FileBrowser(FileBrowserUiState(backend.root, entries = listOf(backend.child, backend.text)), {}, {})
                }
            }
        }
        for (value in YukiFBStyle.entries) {
            for (night in listOf(false, true)) {
                compose.runOnIdle { style.value = value; dark.value = night }
                compose.onNodeWithText("init.rc").assertIsDisplayed()
                compose.onNodeWithContentDescription("Create").assertDoesNotExist()
                screenshot("${value.name.lowercase()}-${if (night) "dark" else "light"}")
            }
        }
    }

    @Test
    fun rowContentAndIconsStayVerticallyCenteredInBothStylesAtLargeFont() {
        val style = mutableStateOf(YukiFBStyle.Classic)
        val scale = mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                MaterialTheme {
                    YukiFBProvider(style = style.value) {
                        FileBrowser(FileBrowserUiState(backend.root, entries = listOf(backend.child, backend.text)), {}, {})
                    }
                }
            }
        }
        for (value in YukiFBStyle.entries) {
            for (fontScale in listOf(1f, 1.5f, 2f)) {
                compose.runOnIdle { style.value = value; scale.value = fontScale }
                val row = compose.onNodeWithText("init.rc").fetchSemanticsNode().boundsInRoot
                val title = compose.onNodeWithText("init.rc", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val detail = compose.onNodeWithText("8 KiB", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val icon = compose.onNodeWithContentDescription("Regular file", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("Text is not centered: $value / $fontScale", abs((title.top + detail.bottom) / 2f - row.center.y) <= 1.5f)
                assertTrue("Icon is not centered: $value / $fontScale", abs(icon.center.y - row.center.y) <= 1.5f)
                val simpleRow = compose.onNodeWithText("etc").fetchSemanticsNode().boundsInRoot
                val simpleText = compose.onNodeWithText("etc", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("Single-line content is not centered", abs(simpleRow.center.y - simpleText.center.y) <= 1.5f)
            }
        }
    }

    @Test
    fun hostEditorClosePolicyDoesNotShowASecondConfirmation() {
        val editor = TextFileEditorState("initial")
        var closeRequests = 0
        compose.setContent {
            MaterialTheme {
                YukiFBProvider {
                    TextFileEditor("init.rc", editor, onSave = {}, onClose = { closeRequests++ }, confirmClose = false)
                }
            }
        }
        compose.runOnIdle { editor.textFieldState.edit { append("changed") } }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle { assertEquals(1, closeRequests) }
        compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()
        screenshot("text-editor")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(compose.activity.getExternalFilesDir(null), "yukifb-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class GestureInput : NavigationEventInput() {
        fun start(edge: Int = NavigationEvent.EDGE_LEFT) = dispatchOnBackStarted(NavigationEvent(swipeEdge = edge))
        fun progress(value: Float, edge: Int = NavigationEvent.EDGE_LEFT) =
            dispatchOnBackProgressed(NavigationEvent(swipeEdge = edge, progress = value))
        fun complete() = dispatchOnBackCompleted()
        fun cancel() = dispatchOnBackCancelled()
    }

    private class BrowserFixture : FileBrowserBackend, CopyEntriesCapability {
        val root = FileEntry(EntryId("root"), null, "ramdisk", FileEntryType.DIRECTORY)
        val child = FileEntry(EntryId("dir"), root.id, "etc", FileEntryType.DIRECTORY)
        val text = FileEntry(EntryId("text"), root.id, "init.rc", FileEntryType.REGULAR_FILE, size = 8192)
        var reads = 0
        var failChild = false
        var failRoot = false
        var rootLoading: CompletableDeferred<Unit>? = null
        var large = false
        var copies = 0
        val copyDestinations = mutableListOf<EntryId>()

        override suspend fun stat(entryId: EntryId) = if (entryId == root.id) root else child
        override suspend fun list(directoryId: EntryId): List<FileEntry> {
            reads++
            if (directoryId == child.id) {
                check(!failChild) { "Unavailable" }
                return listOf(text)
            }
            rootLoading?.await()
            check(!failRoot) { "Unavailable" }
            return if (!large) listOf(child, text) else List(1000) { index ->
                FileEntry(EntryId("entry-$index"), root.id, "file-%04d.txt".format(index), FileEntryType.REGULAR_FILE)
            }
        }
        override suspend fun copy(entryIds: List<EntryId>, destinationId: EntryId) {
            copies++
            copyDestinations += destinationId
        }
    }
}
