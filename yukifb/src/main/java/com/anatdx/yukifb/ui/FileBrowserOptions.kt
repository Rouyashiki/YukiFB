package com.anatdx.yukifb.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anatdx.yukifb.model.FileEntry

/**
 * Presentation options for [FileBrowser].
 *
 * [fileIconResolver] defaults to a conservative classifier that never opens file
 * content. Hosts may replace it when their backend has stronger type information.
 */
data class FileBrowserOptions(
    val showBreadcrumbs: Boolean = true,
    val showFileSize: Boolean = true,
    val showModifiedTime: Boolean = false,
    val showUnixMetadata: Boolean = true,
    val rowMinHeight: Dp = 52.dp,
    val fileIconResolver: (FileEntry) -> FileIconType = ::inferFileIconType,
)

/** Defaults used by YukiFB Compose components. */
object FileBrowserDefaults {
    val options: FileBrowserOptions = FileBrowserOptions()
}
