package com.anatdx.yukifb.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anatdx.yukifb.R
import com.anatdx.yukifb.model.FileByteProgress
import com.anatdx.yukifb.model.FileEntry

/** One available host action. [id] is the stable Compose identity, independent of its title. */
data class FileActionItem(val id: String, val title: String, val icon: ImageVector)

/** Stateless centered file-action grid. Hosts supply only actions supported for [entry]. */
@Composable
fun FileActionsDialog(entry: FileEntry, actions: List<FileActionItem>, onAction: (String) -> Unit, onDismiss: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    val maximumHeight = fileDialogContentHeight()
    BrowserAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Column {
                entry.size?.let { Text(formatFileSize(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp)) }
                BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = maximumHeight)) {
                    val columns = if (maxWidth / fontScale >= 240.dp) 2 else 1
                    Column(Modifier.verticalScroll(rememberScrollState()).testTag("yukifb_file_actions"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { action -> key(action.id) {
                                    FilledTonalButton(
                                        onClick = { onAction(action.id) },
                                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 80.dp).testTag("yukifb_file_action_${action.id}"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                                        shape = if (expressiveYukiFB) MaterialTheme.shapes.large else MaterialTheme.shapes.medium,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            BrowserIcon(action.icon, null, Modifier.size(24.dp))
                                            Text(action.title, textAlign = TextAlign.Center)
                                        }
                                    }
                                } }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.yukifb_action_close)) } },
    )
}

/** Cancellable progress for a host-owned export; a null [progress] is indeterminate. */
@Composable
fun FileActionProgressDialog(title: String, progress: FileByteProgress?, cancelling: Boolean, onCancel: () -> Unit) {
    BrowserAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FileActionProgress(progress)
            Text(stringResource(if (cancelling) R.string.yukifb_action_cancelling else R.string.yukifb_action_preparing))
        } },
        confirmButton = { TextButton(onClick = onCancel, enabled = !cancelling) { Text(stringResource(R.string.yukifb_action_cancel)) } },
    )
}

@Composable
internal fun FileActionProgress(progress: FileByteProgress?) {
    val total = progress?.totalBytes
    if (progress == null || total == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    else LinearProgressIndicator(progress = { if (total == 0L) 1f else (progress.completedBytes.toDouble() / total).toFloat() }, modifier = Modifier.fillMaxWidth())
    if (progress != null) Text(
        if (total == null) formatFileSize(progress.completedBytes) else "${formatFileSize(progress.completedBytes)} / ${formatFileSize(total)}",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
internal fun fileDialogContentHeight(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.6f }
