package com.anatdx.yukifb.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.anatdx.yukifb.backend.FileBrowserCapabilities
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.FileEntryType
import com.anatdx.yukifb.model.TextFileEncoding
import com.anatdx.yukifb.model.UnixMetadata
import com.anatdx.yukifb.state.FileBrowserUiState
import com.anatdx.yukifb.state.FileBrowserSearchResult
import com.anatdx.yukifb.state.FileBrowserSearchState
import com.anatdx.yukifb.state.rememberTextFileEditorState

@Preview(
    name = "Ramdisk root",
    showBackground = true,
    widthDp = 390,
    heightDp = 800,
)
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun FileBrowserPreview() {
    val rootId = EntryId("root")
    MaterialExpressiveTheme(
        colorScheme = expressiveLightColorScheme(),
        motionScheme = MotionScheme.expressive(),
    ) {
        FileBrowser(
            state = FileBrowserUiState(
                currentDirectory = entry(
                    id = rootId,
                    parentId = null,
                    name = "ramdisk",
                    type = FileEntryType.DIRECTORY,
                ),
                entries = listOf(
                    entry(
                        id = EntryId("overlay"),
                        parentId = rootId,
                        name = "overlay.d",
                        type = FileEntryType.DIRECTORY,
                        permissions = 0b111_101_101,
                    ),
                    entry(
                        id = EntryId("init"),
                        parentId = rootId,
                        name = "init",
                        type = FileEntryType.REGULAR_FILE,
                        size = 2_361_344,
                        permissions = 0b111_101_000,
                    ),
                    entry(
                        id = EntryId("init.rc"),
                        parentId = rootId,
                        name = "init.rc",
                        type = FileEntryType.REGULAR_FILE,
                        size = 19_286,
                        permissions = 0b110_100_100,
                    ),
                    entry(
                        id = EntryId("bin"),
                        parentId = rootId,
                        name = "bin",
                        type = FileEntryType.SYMBOLIC_LINK,
                        linkTarget = "/system/bin",
                        permissions = 0b111_111_111,
                    ),
                    entry(
                        id = EntryId("kernelsu"),
                        parentId = rootId,
                        name = "kernelsu.ko",
                        type = FileEntryType.REGULAR_FILE,
                        size = 1_048_576,
                        permissions = 0b110_100_100,
                    ),
                ),
                capabilities = FileBrowserCapabilities(
                    canCreateEntries = true,
                    canRename = true,
                    canDelete = true,
                    canCopy = true,
                    canMove = true,
                    canEditUnixMetadata = true,
                    canCreateSymbolicLinks = true,
                    canCreateHardLinks = true,
                ),
            ),
            onAction = {},
            onOpenFile = {},
            onImport = {},
        )
    }
}

@Preview(
    name = "Recursive search",
    showBackground = true,
    widthDp = 390,
    heightDp = 800,
)
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun FileBrowserSearchPreview() {
    val root = entry(
        id = EntryId("root"),
        parentId = null,
        name = "ramdisk",
        type = FileEntryType.DIRECTORY,
    )
    val overlay = entry(
        id = EntryId("overlay"),
        parentId = root.id,
        name = "overlay.d",
        type = FileEntryType.DIRECTORY,
    )
    val result = entry(
        id = EntryId("init-overlay"),
        parentId = overlay.id,
        name = "init.overlay.rc",
        type = FileEntryType.REGULAR_FILE,
        size = 4_096,
        permissions = 0b110_100_100,
    )
    MaterialExpressiveTheme(
        colorScheme = expressiveLightColorScheme(),
        motionScheme = MotionScheme.expressive(),
    ) {
        FileBrowser(
            state = FileBrowserUiState(
                currentDirectory = root,
                search = FileBrowserSearchState(
                    query = "init",
                    results = listOf(
                        FileBrowserSearchResult(
                            entry = result,
                            parentBreadcrumbs = listOf(root, overlay),
                        ),
                    ),
                    scannedDirectoryCount = 7,
                ),
            ),
            onAction = {},
            onOpenFile = {},
        )
    }
}

@Preview(
    name = "Text editor formats",
    showBackground = true,
    widthDp = 390,
    heightDp = 800,
)
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun TextFileEditorPreview() {
    MaterialExpressiveTheme(
        colorScheme = expressiveLightColorScheme(),
        motionScheme = MotionScheme.expressive(),
    ) {
        val state = rememberTextFileEditorState(
            initialText = "#!/system/bin/sh\r\necho \"YukiFB\"\nexit 0\r\n",
            initialEncoding = TextFileEncoding.UTF_8,
        )
        TextFileEditor(
            fileName = "init.custom.rc",
            state = state,
            onSave = {},
            onClose = {},
            onReopenWithEncoding = {},
        )
    }
}

private fun entry(
    id: EntryId,
    parentId: EntryId?,
    name: String,
    type: FileEntryType,
    size: Long? = null,
    permissions: Int? = null,
    linkTarget: String? = null,
): FileEntry = FileEntry(
    id = id,
    parentId = parentId,
    name = name,
    type = type,
    size = size,
    unixMetadata = permissions?.let {
        UnixMetadata(
            permissions = it,
            uid = 0,
            gid = 0,
        )
    },
    linkTarget = linkTarget,
)
