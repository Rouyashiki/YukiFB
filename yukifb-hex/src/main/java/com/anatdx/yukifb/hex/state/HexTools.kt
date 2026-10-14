package com.anatdx.yukifb.hex.state

import com.anatdx.yukifb.hex.backend.BinaryBytes
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Parses hexadecimal bytes with optional whitespace and a leading 0x prefix. */
fun parseHexBytes(text: String, maxBytes: Int = 65536): BinaryBytes {
    val compact = text.filterNot(Char::isWhitespace).removePrefix("0x").removePrefix("0X")
    require(compact.length % 2 == 0 && compact.length / 2 <= maxBytes) { "Enter complete byte pairs within the input limit" }
    return BinaryBytes.copyOf(ByteArray(compact.length / 2) { index ->
        val high = compact[index * 2].digitToIntOrNull(16)
        val low = compact[index * 2 + 1].digitToIntOrNull(16)
        require(high != null && low != null) { "Invalid hexadecimal digit" }
        ((high shl 4) or low).toByte()
    })
}

/** Formats a bounded immutable chunk as space-separated hexadecimal pairs. */
fun formatHexBytes(bytes: BinaryBytes): String =
    (0 until bytes.size).joinToString(" ") { (bytes[it].toInt() and 255).toString(16).uppercase().padStart(2, '0') }

/** Parses a nonnegative absolute offset in the selected radix, accepting a 0x override. */
fun parseHexEditorOffset(text: String, hexadecimal: Boolean, size: Long): Long {
    val value = text.trim()
    val prefixed = value.startsWith("0x", ignoreCase = true)
    val digits = if (prefixed) value.drop(2) else value
    val offset = digits.toLongOrNull(if (prefixed || hexadecimal) 16 else 10)
    require(offset != null && offset in 0..size) { "Offset is outside the document" }
    return offset
}

/** Read-only numerical interpretation. Null values mean insufficient bytes. */
data class BinaryNumericValue(
    /** Conventional scalar type name, such as u32 or f64. */
    val type: String,
    /** Decimal representation, or null if the bytes are incomplete. */
    val value: String?,
)

/** Interprets up to eight bytes at the caret without alignment requirements. */
fun inspectBinaryValues(bytes: BinaryBytes, littleEndian: Boolean): List<BinaryNumericValue> {
    val buffer = ByteBuffer.wrap(bytes.toByteArray()).order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
    fun value(type: String, width: Int, read: () -> String) = BinaryNumericValue(type, if (bytes.size >= width) read() else null)
    return listOf(
        value("i8", 1) { buffer.get(0).toString() }, value("u8", 1) { buffer.get(0).toUByte().toString() },
        value("i16", 2) { buffer.getShort(0).toString() }, value("u16", 2) { buffer.getShort(0).toUShort().toString() },
        value("i32", 4) { buffer.getInt(0).toString() }, value("u32", 4) { buffer.getInt(0).toUInt().toString() },
        value("i64", 8) { buffer.getLong(0).toString() }, value("u64", 8) { buffer.getLong(0).toULong().toString() },
        value("f32", 4) { buffer.getFloat(0).toString() }, value("f64", 8) { buffer.getDouble(0).toString() },
    )
}
