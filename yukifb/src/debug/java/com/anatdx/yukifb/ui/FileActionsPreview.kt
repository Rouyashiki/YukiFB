package com.anatdx.yukifb.ui

import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.anatdx.yukifb.R
import com.anatdx.yukifb.model.*
import com.anatdx.yukifb.state.FileChecksumUiState

private val actionPreviewEntry = FileEntry(EntryId("preview-file"), null, "init.rc", FileEntryType.REGULAR_FILE, size = 65536)

@Preview(name = "File actions", widthDp = 412, heightDp = 800)
@Preview(name = "File actions Chinese large font", widthDp = 320, heightDp = 640, locale = "zh-rCN", fontScale = 1.5f)
@Composable
private fun FileActionsPreview() {
    MaterialTheme { YukiFBProvider {
        FileActionsDialog(actionPreviewEntry, listOf(
            FileActionItem("share", stringResource(R.string.yukifb_action_share), Icons.Default.Share),
            FileActionItem("checksum", stringResource(R.string.yukifb_action_checksums), Icons.Default.Fingerprint),
        ), {}, {})
    } }
}

@Preview(name = "Checksums dark Classic", widthDp = 412, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FileChecksumsPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) { YukiFBProvider(style = YukiFBStyle.Classic) {
        FileChecksumsDialog(FileChecksumUiState(
            entry = actionPreviewEntry,
            algorithms = listOf(FileChecksumAlgorithm("CRC32", "CRC32", 8)), selectedIds = setOf("CRC32"),
            results = listOf(FileChecksumValue("CRC32", "CBF43926")), comparisonInput = "cb f4 39 26",
        ), {}, {})
    } }
}
