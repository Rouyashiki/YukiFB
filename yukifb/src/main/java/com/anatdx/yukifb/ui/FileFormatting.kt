package com.anatdx.yukifb.ui

import com.anatdx.yukifb.model.FileEntryType
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal fun formatFileSize(size: Long): String {
    if (size < 1024) return "$size B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = size.toDouble()
    var unitIndex = -1
    do {
        value /= 1024.0
        unitIndex++
    } while (value >= 1024.0 && unitIndex < units.lastIndex)
    return if (value >= 10 || value % 1.0 == 0.0) {
        String.format(Locale.ROOT, "%.0f %s", value, units[unitIndex])
    } else {
        String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
    }
}

internal fun formatModifiedTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(epochMillis))

internal fun formatPermissions(permissions: Int): String =
    buildString(9) {
        val bits = intArrayOf(
            0b100_000_000,
            0b010_000_000,
            0b001_000_000,
            0b000_100_000,
            0b000_010_000,
            0b000_001_000,
            0b000_000_100,
            0b000_000_010,
            0b000_000_001,
        )
        val chars = charArrayOf('r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x')
        bits.forEachIndexed { index, bit ->
            append(if (permissions and bit != 0) chars[index] else '-')
        }
    }

internal fun formatOctalPermissions(permissions: Int): String =
    permissions.toString(radix = 8).padStart(4, '0')

internal fun FileEntryType.resourceId(): Int = when (this) {
    FileEntryType.REGULAR_FILE -> com.anatdx.yukifb.R.string.yukifb_type_regular_file
    FileEntryType.DIRECTORY -> com.anatdx.yukifb.R.string.yukifb_type_directory
    FileEntryType.SYMBOLIC_LINK -> com.anatdx.yukifb.R.string.yukifb_type_symbolic_link
    FileEntryType.BLOCK_DEVICE -> com.anatdx.yukifb.R.string.yukifb_type_block_device
    FileEntryType.CHARACTER_DEVICE -> {
        com.anatdx.yukifb.R.string.yukifb_type_character_device
    }

    FileEntryType.FIFO -> com.anatdx.yukifb.R.string.yukifb_type_fifo
    FileEntryType.SOCKET -> com.anatdx.yukifb.R.string.yukifb_type_socket
    FileEntryType.UNKNOWN -> com.anatdx.yukifb.R.string.yukifb_type_unknown
}
