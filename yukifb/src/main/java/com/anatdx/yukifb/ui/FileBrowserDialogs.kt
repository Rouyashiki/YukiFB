package com.anatdx.yukifb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anatdx.yukifb.R
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.UnixMetadataPatch

@Composable
internal fun NameInputDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialValue: String = "",
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val isValid = value.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                singleLine = true,
                label = { Text(stringResource(R.string.yukifb_name)) },
                isError = value.isNotEmpty() && !isValid,
                supportingText = if (value.isNotEmpty() && !isValid) {
                    {
                        Text(stringResource(R.string.yukifb_invalid_name))
                    }
                } else {
                    null
                },
                onValueChange = { value = it },
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(value.trim()) },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.yukifb_cancel))
            }
        },
    )
}

@Composable
internal fun SymbolicLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, target: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    val isValid = name.trim().isNotEmpty() && target.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.yukifb_new_symbolic_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    singleLine = true,
                    label = { Text(stringResource(R.string.yukifb_name)) },
                    onValueChange = { name = it },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = target,
                    singleLine = true,
                    label = { Text(stringResource(R.string.yukifb_link_target)) },
                    onValueChange = { target = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(name.trim(), target) },
            ) {
                Text(stringResource(R.string.yukifb_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.yukifb_cancel))
            }
        },
    )
}

@Composable
internal fun DeleteConfirmationDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.yukifb_confirm_delete_title)) },
        text = {
            Text(
                pluralStringResource(
                    R.plurals.yukifb_confirm_delete_message,
                    count,
                    count,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.yukifb_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.yukifb_cancel))
            }
        },
    )
}

@Composable
internal fun UnixMetadataDialog(
    entries: List<FileEntry>,
    onDismiss: () -> Unit,
    onConfirm: (UnixMetadataPatch) -> Unit,
) {
    val commonMetadata = entries
        .mapNotNull { it.unixMetadata }
        .takeIf { it.size == entries.size }
        ?.let { metadata ->
            metadata.firstOrNull()?.takeIf { first ->
                metadata.all {
                    it.permissions == first.permissions &&
                        it.uid == first.uid &&
                        it.gid == first.gid
                }
            }
        }
    var permissions by remember {
        mutableStateOf(
            commonMetadata?.permissions?.let(::formatOctalPermissions).orEmpty(),
        )
    }
    var uid by remember { mutableStateOf(commonMetadata?.uid?.toString().orEmpty()) }
    var gid by remember { mutableStateOf(commonMetadata?.gid?.toString().orEmpty()) }

    val parsedPermissions = permissions
        .takeIf { it.isNotBlank() }
        ?.toIntOrNull(radix = 8)
    val parsedUid = uid.takeIf { it.isNotBlank() }?.toLongOrNull()
    val parsedGid = gid.takeIf { it.isNotBlank() }?.toLongOrNull()
    val permissionsValid = permissions.isBlank() ||
        parsedPermissions?.let { it in 0..0xFFF } == true
    val uidValid = uid.isBlank() || parsedUid?.let { it >= 0 } == true
    val gidValid = gid.isBlank() || parsedGid?.let { it >= 0 } == true
    val hasChange = permissions.isNotBlank() || uid.isNotBlank() || gid.isNotBlank()
    val isValid = hasChange && permissionsValid && uidValid && gidValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.yukifb_edit_metadata)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = permissions,
                    singleLine = true,
                    label = { Text(stringResource(R.string.yukifb_permissions_octal)) },
                    isError = !permissionsValid,
                    supportingText = if (!permissionsValid) {
                        {
                            Text(stringResource(R.string.yukifb_invalid_permissions))
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onValueChange = { permissions = it },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = uid,
                        singleLine = true,
                        label = { Text(stringResource(R.string.yukifb_uid)) },
                        isError = !uidValid,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        onValueChange = { uid = it },
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = gid,
                        singleLine = true,
                        label = { Text(stringResource(R.string.yukifb_gid)) },
                        isError = !gidValid,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        onValueChange = { gid = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onConfirm(
                        UnixMetadataPatch(
                            permissions = parsedPermissions,
                            uid = parsedUid,
                            gid = parsedGid,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.yukifb_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.yukifb_cancel))
            }
        },
    )
}

@Composable
internal fun PropertiesDialog(
    entries: List<FileEntry>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.yukifb_properties)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (entries.size == 1) {
                    EntryProperties(entries.single())
                } else {
                    PropertyRow(
                        stringResource(R.string.yukifb_type),
                        stringResource(R.string.yukifb_multiple_entries),
                    )
                    PropertyRow(
                        pluralStringResource(
                            R.plurals.yukifb_selected_count,
                            entries.size,
                            entries.size,
                        ),
                        entries.sumOf { it.size ?: 0L }.let(::formatFileSize),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.yukifb_close))
            }
        },
    )
}

@Composable
private fun EntryProperties(entry: FileEntry) {
    PropertyRow(stringResource(R.string.yukifb_name), entry.name)
    PropertyRow(
        stringResource(R.string.yukifb_type),
        stringResource(entry.type.resourceId()),
    )
    entry.size?.let {
        PropertyRow(stringResource(R.string.yukifb_size), formatFileSize(it))
    }
    entry.modifiedAtMillis?.let {
        PropertyRow(stringResource(R.string.yukifb_modified), formatModifiedTime(it))
    }
    entry.linkTarget?.let {
        PropertyRow(stringResource(R.string.yukifb_link_target), it)
    }
    entry.unixMetadata?.let { metadata ->
        PropertyRow(
            stringResource(R.string.yukifb_permissions),
            "${formatOctalPermissions(metadata.permissions)} " +
                "(${formatPermissions(metadata.permissions)})",
        )
        PropertyRow(
            stringResource(R.string.yukifb_owner),
            "${metadata.uid}:${metadata.gid}",
        )
        metadata.inode?.let {
            PropertyRow(stringResource(R.string.yukifb_inode), it.toString())
        }
        metadata.hardLinkCount?.let {
            PropertyRow(stringResource(R.string.yukifb_hard_link_count), it.toString())
        }
        if (metadata.deviceMajor != null && metadata.deviceMinor != null) {
            PropertyRow(
                stringResource(R.string.yukifb_device),
                "${metadata.deviceMajor}:${metadata.deviceMinor}",
            )
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(label)
        Text(value)
    }
    HorizontalDivider()
}
