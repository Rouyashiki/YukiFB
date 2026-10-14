package com.anatdx.yukifb.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.anatdx.yukifb.backend.FileBrowserCapabilities
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.FileEntryType
import com.anatdx.yukifb.state.FileBrowserUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class YukiFBRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val root = FileEntry(EntryId("root"), null, "root", FileEntryType.DIRECTORY)
    private val entries = List(3) { FileEntry(EntryId("file-$it"), root.id, "file-$it", FileEntryType.REGULAR_FILE) }

    @Test
    fun transparentSceneKeepsItsHostBackgroundDuringBothBackEdges() {
        val back = YukiFBBackState()
        compose.setContent {
            MaterialTheme {
                YukiFBProvider(
                    colors = YukiFBThemeDefaults.colors().copy(background = Color.Transparent),
                    sceneBackground = { Box(Modifier.fillMaxSize().background(Color.White)) },
                ) {
                    YukiFBScene("child", Modifier.fillMaxSize(), backState = back,
                        backPreview = { Box(Modifier.fillMaxSize().background(Color.Red)) }) {}
                }
            }
        }
        for (edge in listOf(1, -1)) {
            compose.runOnIdle { back.direction = edge; back.isActive = true; back.progress = 0.6f }
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            assertEquals("Parent leaked through the moving page", Color.White.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
            compose.runOnIdle { back.reset() }
        }
    }

    @Test
    fun transparentSceneWithoutHostRendererHasOpaqueGestureBacking() {
        val back = YukiFBBackState()
        var expected = Color.Unspecified
        compose.setContent {
            MaterialTheme {
                expected = MaterialTheme.colorScheme.surfaceContainerLowest
                YukiFBProvider(colors = YukiFBThemeDefaults.colors().copy(background = Color.Transparent)) {
                    YukiFBScene("child", Modifier.fillMaxSize(), backState = back,
                        backPreview = { Box(Modifier.fillMaxSize().background(Color.Red)) }) {}
                }
            }
        }
        compose.runOnIdle { back.isActive = true; back.progress = 0.6f }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(expected.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
    }

    @Test
    fun ordinarySceneTransitionNeverBlendsTwoPagesAtOnce() {
        val page = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                YukiFBProvider(sceneBackground = { Box(Modifier.fillMaxSize().background(Color.Black)) }) {
                    YukiFBScene(page.value, Modifier.size(240.dp)) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.align(if (page.value) Alignment.CenterEnd else Alignment.CenterStart)
                                .size(60.dp).background(if (page.value) Color.Green else Color.Red))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { page.value = true }
        repeat(24) {
            compose.mainClock.advanceTimeByFrame()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            var red = false
            var green = false
            for (x in 0 until pixels.width step 3) {
                val color = pixels[x, pixels.height / 2]
                red = red || (color.red > 0.08f && color.green < 0.02f)
                green = green || (color.green > 0.08f && color.red < 0.02f)
            }
            assertTrue("Outgoing and incoming content overlap at frame $it", !(red && green))
        }
        compose.mainClock.autoAdvance = true
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test
    fun directoryReplacementDoesNotRunRemovedRowAnimationsInTheNewPage() {
        val page = mutableStateOf(FileBrowserUiState(root, entries = entries))
        val slowRemoval = object : MotionScheme by MotionScheme.standard() {
            override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
            override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(2_000)
        }
        compose.setContent {
            MaterialTheme(motionScheme = slowRemoval) {
                YukiFBProvider(style = YukiFBStyle.Classic,
                    colors = YukiFBThemeDefaults.colors().copy(background = Color.White, listContainer = Color.Black)) {
                    FileBrowser(page.value, {}, {})
                }
            }
        }
        val removed = compose.onNodeWithText("file-2").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            val child = FileEntry(EntryId("child"), root.id, "child", FileEntryType.DIRECTORY)
            page.value = FileBrowserUiState(child,
                entries = listOf(FileEntry(EntryId("new"), child.id, "new", FileEntryType.REGULAR_FILE)))
        }
        compose.mainClock.advanceTimeBy(64)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals("Previous directory rows are still drawn", Color.White.toArgb(), pixels[2, removed.center.y.toInt()].toArgb())
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun ordinaryNavigationNeverShowsABlankOrFadedIncomingPage() {
        val page = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                YukiFBProvider(sceneBackground = { Box(Modifier.fillMaxSize().background(Color.White)) }) {
                    YukiFBScene(page.value, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(if (page.value) Color.Blue else Color.Red))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { page.value = true }
        repeat(18) { frame ->
            compose.mainClock.advanceTimeByFrame()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            assertEquals("Incoming page flashed at frame $frame", Color.Blue.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
        }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun loadingNeverMovesRowsOrHidesTheCreationButton() {
        val page = mutableStateOf(FileBrowserUiState(root, entries = entries,
            capabilities = FileBrowserCapabilities(canCreateEntries = true)))
        compose.setContent {
            MaterialTheme {
                YukiFBProvider {
                    FileBrowser(page.value, {}, {})
                }
            }
        }
        val before = compose.onNodeWithText("file-1").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { page.value = page.value.copy(isLoading = true) }
        for (time in listOf(32L, 160L, 250L)) {
            compose.mainClock.advanceTimeBy(time)
            assertEquals(before, compose.onNodeWithText("file-1").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithContentDescription("Create").assertIsDisplayed()
        }
        compose.runOnIdle { page.value = page.value.copy(isLoading = false) }
        compose.mainClock.advanceTimeBy(250)
        assertEquals(before, compose.onNodeWithText("file-1").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun classicDividerInsetUsesTheSameSurfaceAsItsRows() {
        compose.setContent {
            MaterialTheme {
                YukiFBProvider(style = YukiFBStyle.Classic, motion = YukiFBMotion.None,
                    colors = YukiFBThemeDefaults.colors().copy(background = Color.White, listContainer = Color.Gray)) {
                    FileBrowser(FileBrowserUiState(root, entries = entries), {}, {})
                }
            }
        }
        val first = compose.onNodeWithText("file-0").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("file-1").fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val seam = ((first.bottom + second.top) / 2).toInt()
        assertEquals("Divider inset exposes page background", pixels[2, first.center.y.toInt()].toArgb(), pixels[2, seam].toArgb())
    }

    @Test
    fun expressiveGroupEndsMatchSuperuserRowCorners() {
        compose.setContent {
            MaterialTheme(shapes = Shapes(extraSmall = RoundedCornerShape(7.dp), large = RoundedCornerShape(28.dp))) {
                YukiFBProvider(motion = YukiFBMotion.None,
                    colors = YukiFBThemeDefaults.colors().copy(background = Color.White, listContainer = Color.Black)) {
                    FileBrowser(FileBrowserUiState(root, entries = entries), {}, {})
                }
            }
        }
        val first = compose.onNodeWithText("file-0").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithText("file-2").fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        val x = (first.left + 8 * density).roundToInt()
        assertEquals(Color.Black.toArgb(), pixels[x, (first.top + 2 * density).roundToInt()].toArgb())
        assertEquals(Color.Black.toArgb(), pixels[x, (last.bottom - 2 * density).roundToInt()].toArgb())
    }
}
