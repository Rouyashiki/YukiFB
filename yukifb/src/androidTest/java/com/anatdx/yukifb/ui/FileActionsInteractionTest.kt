package com.anatdx.yukifb.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.anatdx.yukifb.R
import com.anatdx.yukifb.backend.FileChecksumCapability
import com.anatdx.yukifb.model.*
import com.anatdx.yukifb.state.*
import java.io.File
import java.util.Locale
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FileActionsInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val entry = FileEntry(EntryId("file"), null, "init.real", FileEntryType.REGULAR_FILE, size = 2795360)

    private fun menu(scale: Float = 1f, chinese: Boolean = false) {
        compose.setContent {
            val context = LocalContext.current
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(if (chinese) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH) }
            val localized = context.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration,
                LocalResources provides localized.resources, LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaterialTheme(colorScheme = if (chinese) darkColorScheme() else lightColorScheme()) {
                    YukiFBProvider(style = if (chinese) YukiFBStyle.Classic else YukiFBStyle.Expressive, motion = YukiFBMotion.Reduced) {
                        FileActionsDialog(entry, listOf(
                            FileActionItem("text", if (chinese) "文本编辑器" else "Text editor", Icons.Default.Edit),
                            FileActionItem("elf", if (chinese) "ELF 信息" else "ELF information", Icons.Default.Memory),
                            FileActionItem("hex", if (chinese) "Hex 编辑器" else "Hex editor", Icons.Default.Code),
                            FileActionItem("share", localized.getString(R.string.yukifb_action_share), Icons.Default.Share),
                            FileActionItem("open", localized.getString(R.string.yukifb_action_open_externally), Icons.AutoMirrored.Filled.OpenInNew),
                            FileActionItem("checksum", localized.getString(R.string.yukifb_action_checksums), Icons.Default.Fingerprint),
                        ), {}, {})
                    }
                }
            }
        }
    }

    @Test fun phoneMenuUsesTwoColumnsAndFullButtonLabels() {
        menu()
        val first = compose.onNodeWithTag("yukifb_file_action_text").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("yukifb_file_action_elf").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue(second.left >= first.right)
        compose.onNodeWithTag("yukifb_file_action_checksum").assertIsDisplayed()
        saveScreenshot("actions-phone.png")
    }

    @Test fun largeChineseTextUsesOneScrollableColumnInDarkClassicStyle() {
        menu(scale = 1.8f, chinese = true)
        val first = compose.onNodeWithTag("yukifb_file_action_text").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("yukifb_file_action_elf").fetchSemanticsNode().boundsInRoot
        assertTrue(second.top >= first.bottom)
        assertEquals(first.left, second.left, 1f)
        compose.onNodeWithTag("yukifb_file_action_checksum").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed()
        saveScreenshot("actions-large-zh-dark.png")
    }

    @Test fun menuDispatchesStableIdAndDoesNotInventUnavailableActions() {
        var chosen: String? = null
        compose.setContent { MaterialTheme {
            FileActionsDialog(entry, listOf(FileActionItem("opaque-share", "Share", Icons.Default.Share)), { chosen = it }, {})
        } }
        compose.onNodeWithText("Hex editor").assertDoesNotExist()
        compose.onNodeWithText("Share").performClick()
        compose.runOnIdle { assertEquals("opaque-share", chosen) }
    }

    @Test fun checksumsRequireCalculateThenAllowCopyAndExplicitPasteComparison() {
        var calls = 0
        lateinit var controller: FileChecksumController
        compose.setContent {
            val scope = rememberCoroutineScope()
            controller = remember { FileChecksumController(entry, checksumHost { calls++; listOf(FileChecksumValue("CRC32", "CBF43926")) }, scope, setOf("CRC32")) }
            MaterialTheme { FileChecksumsDialog(controller, {}) }
        }
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Calculate").performClick()
        compose.onNodeWithContentDescription("Copy CRC32").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Paste checksum").performScrollTo().performClick()
        compose.onNodeWithText("Matches: CRC32").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, calls); assertEquals("CBF43926", controller.state.value.comparisonInput) }
        saveScreenshot("checksum-match.png")
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Calculate").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(controller.state.value.results.isEmpty()) }
    }

    @Test fun closingChecksumDialogCancelsActiveHostTask() {
        lateinit var controller: FileChecksumController
        var dismissed = false
        var cleaned = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            controller = remember { FileChecksumController(entry, checksumHost {
                try { awaitCancellation() } finally { cleaned = true }
            }, scope, setOf("CRC32")) }
            MaterialTheme { FileChecksumsDialog(controller, { dismissed = true }) }
        }
        compose.onNodeWithText("Calculate").performClick()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.waitUntil { cleaned }
        compose.runOnIdle { assertTrue(dismissed); assertFalse(controller.state.value.running) }
    }

    private fun checksumHost(calculate: suspend () -> List<FileChecksumValue>) = object : FileChecksumCapability {
        override fun algorithms(entry: FileEntry) = listOf(FileChecksumAlgorithm("CRC32", "CRC32", 8))
        override suspend fun calculate(entryId: EntryId, algorithmIds: Set<String>, onProgress: (FileByteProgress) -> Unit) = calculate()
    }

    private fun saveScreenshot(name: String) {
        val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
