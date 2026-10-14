package com.anatdx.yukifb.ui

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.anatdx.yukifb.backend.FileBrowserCapabilities
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.FileEntryType
import com.anatdx.yukifb.model.UnixMetadata
import com.anatdx.yukifb.state.ClipboardMode
import com.anatdx.yukifb.state.FileBrowserClipboard
import com.anatdx.yukifb.state.FileBrowserError
import com.anatdx.yukifb.state.FileBrowserOperation
import com.anatdx.yukifb.state.FileBrowserUiState

private val previewRoot = FileEntry(EntryId("root"), null, "ramdisk", FileEntryType.DIRECTORY)
private val previewEntries = listOf(
    FileEntry(EntryId("dir"), previewRoot.id, "overlay.d", FileEntryType.DIRECTORY),
    FileEntry(EntryId("text"), previewRoot.id, "init.rc", FileEntryType.REGULAR_FILE, size = 8192,
        unixMetadata = UnixMetadata(permissions = 420, uid = 0, gid = 0)),
    FileEntry(EntryId("long"), previewRoot.id, "a-very-long-file-name-with-important-details.conf", FileEntryType.REGULAR_FILE),
    FileEntry(EntryId("link"), previewRoot.id, "bin", FileEntryType.SYMBOLIC_LINK, linkTarget = "/system/bin"),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StyledBrowser(
    style: YukiFBStyle,
    selected: Boolean = false,
    clipboard: Boolean = false,
    empty: Boolean = false,
    failed: Boolean = false,
    transparent: Boolean = false,
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        motionScheme = if (style == YukiFBStyle.Expressive) MotionScheme.expressive() else MotionScheme.standard(),
    ) {
        val defaults = YukiFBThemeDefaults.colors()
        YukiFBProvider(style, colors = if (transparent) defaults.copy(background = Color.Transparent,
            listContainer = defaults.listContainer.copy(alpha = 0.7f)) else defaults) {
            FileBrowser(
                state = FileBrowserUiState(
                    currentDirectory = previewRoot,
                    entries = if (empty) emptyList() else previewEntries,
                    selectedIds = if (selected) setOf(previewEntries[1].id) else emptySet(),
                    clipboard = if (clipboard) FileBrowserClipboard(listOf(previewEntries[1]), ClipboardMode.COPY) else null,
                    error = if (failed) FileBrowserError(FileBrowserOperation.LOAD, IllegalStateException("Unavailable")) else null,
                    capabilities = FileBrowserCapabilities(canCopy = selected, canDelete = selected),
                ),
                onAction = {}, onOpenFile = {},
            )
        }
    }
}

@Preview(name = "Classic compact", widthDp = 360, heightDp = 760)
@Composable private fun ClassicPreview() = StyledBrowser(YukiFBStyle.Classic)

@Preview(name = "Expressive dark selection", widthDp = 390, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ExpressiveSelectionPreview() = StyledBrowser(YukiFBStyle.Expressive, selected = true)

@Preview(name = "Chinese narrow clipboard", widthDp = 320, heightDp = 720, locale = "zh-rCN", fontScale = 1.5f)
@Composable private fun ChineseClipboardPreview() = StyledBrowser(YukiFBStyle.Expressive, clipboard = true)

@Preview(name = "Empty read-only", widthDp = 360, heightDp = 760)
@Composable private fun EmptyReadOnlyPreview() = StyledBrowser(YukiFBStyle.Classic, empty = true)

@Preview(name = "Persistent load failure", widthDp = 360, heightDp = 760)
@Composable private fun LoadFailurePreview() = StyledBrowser(YukiFBStyle.Expressive, empty = true, failed = true)

@Preview(name = "Transparent host surface", widthDp = 360, heightDp = 760, backgroundColor = 0xffbecfe5, showBackground = true)
@Composable private fun TransparentSurfacePreview() = StyledBrowser(YukiFBStyle.Expressive, transparent = true)
