package com.anatdx.yukifb.ui

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.anatdx.yukifb.R
import com.anatdx.yukifb.model.FileContentChangedException
import com.anatdx.yukifb.state.FileChecksumAction
import com.anatdx.yukifb.state.FileChecksumComparison
import com.anatdx.yukifb.state.FileChecksumController
import com.anatdx.yukifb.state.FileChecksumUiState
import kotlinx.coroutines.launch

/** Binds a host-owned controller; closing cancels the current batch before dismissing. */
@Composable
fun FileChecksumsDialog(controller: FileChecksumController, onDismiss: () -> Unit) {
    val state by controller.state.collectAsState()
    FileChecksumsDialog(state, controller::dispatch, { controller.cancel(); onDismiss() })
}

/** Stateless algorithm selection, progress, copy and explicit paste-to-compare surface. */
@Composable
fun FileChecksumsDialog(state: FileChecksumUiState, onAction: (FileChecksumAction) -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val contentHeight = fileDialogContentHeight()
    BrowserAlertDialog(
        onDismissRequest = onDismiss,
        title = { Column {
            Text(stringResource(R.string.yukifb_action_checksums))
            Text(state.entry.name, style = MaterialTheme.typography.bodyMedium)
        } },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = contentHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.yukifb_checksum_choose))
                state.algorithms.forEach { algorithm -> key(algorithm.id) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            value = algorithm.id in state.selectedIds, role = Role.Checkbox,
                            onValueChange = { onAction(FileChecksumAction.Toggle(algorithm.id)) },
                        ), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = algorithm.id in state.selectedIds, onCheckedChange = null)
                        Text(algorithm.label, Modifier.padding(start = 8.dp))
                    }
                } }
                if (state.running) {
                    FileActionProgress(state.progress)
                    Text(stringResource(if (state.cancelling) R.string.yukifb_action_cancelling else R.string.yukifb_checksum_calculating))
                }
                state.error?.let { error ->
                    Text(stringResource(if (error is FileContentChangedException) R.string.yukifb_checksum_changed else R.string.yukifb_checksum_failed), color = MaterialTheme.colorScheme.error)
                }
                state.results.forEach { result -> key(result.algorithmId) {
                    HorizontalDivider()
                    val name = state.algorithms.first { it.id == result.algorithmId }.label
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(name, result.hex))) } }) {
                            BrowserIcon(Icons.Default.ContentCopy, stringResource(R.string.yukifb_checksum_copy, name))
                        }
                    }
                    SelectionContainer { Text(result.hex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                } }
                HorizontalDivider()
                OutlinedTextField(
                    value = state.comparisonInput, onValueChange = { onAction(FileChecksumAction.Compare(it)) },
                    modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.yukifb_checksum_compare)) },
                    isError = state.comparison == FileChecksumComparison.INVALID,
                )
                TextButton(onClick = { scope.launch {
                    val clip = clipboard.getClipEntry()?.clipData
                    if (clip != null && clip.itemCount > 0) onAction(FileChecksumAction.Compare(clip.getItemAt(0).text?.toString().orEmpty()))
                } }) { Text(stringResource(R.string.yukifb_checksum_paste)) }
                when (state.comparison) {
                    FileChecksumComparison.EMPTY -> Unit
                    FileChecksumComparison.INVALID -> Text(stringResource(R.string.yukifb_checksum_invalid), color = MaterialTheme.colorScheme.error)
                    FileChecksumComparison.NOT_COMPUTED -> Text(stringResource(R.string.yukifb_checksum_not_computed))
                    FileChecksumComparison.MATCH -> Text(stringResource(R.string.yukifb_checksum_match, state.algorithms.filter { it.id in state.matchingIds }.joinToString { it.label }), color = MaterialTheme.colorScheme.primary)
                    FileChecksumComparison.DIFFERENT -> Text(stringResource(R.string.yukifb_checksum_different), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (state.running) TextButton(onClick = { onAction(FileChecksumAction.Cancel) }, enabled = !state.cancelling) { Text(stringResource(R.string.yukifb_action_cancel)) }
            else TextButton(onClick = { onAction(FileChecksumAction.Calculate) }, enabled = state.selectedIds.isNotEmpty()) {
                Text(stringResource(if (state.error == null) R.string.yukifb_checksum_calculate else R.string.yukifb_checksum_retry))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.yukifb_action_close)) } },
    )
}
