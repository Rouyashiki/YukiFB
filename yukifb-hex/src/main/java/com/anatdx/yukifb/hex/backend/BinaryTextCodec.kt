package com.anatdx.yukifb.hex.backend

/** A decoded Unicode scalar or placeholder and its exact source-byte range. */
data class BinaryCharacter(
    /** Absolute offset of its first byte. */
    val offset: Long,
    /** Number of source bytes represented by this character. */
    val byteCount: Int,
    /** Display text; invalid and non-printing sequences should use a visible placeholder. */
    val text: String,
)

/** Host-supplied encoding. Decoding is a display operation and must never rewrite source bytes. */
interface BinaryTextCodec {
    /** Stable encoding identity. */
    val id: String
    /** Encoding's conventional display name. */
    val label: String
    /** Bounded context needed on either side of a visible range, at most 4096 bytes. */
    val contextBytes: Int get() = 8
    /** Decodes a window starting at [offset], preserving byte positions, including invalid sequences. */
    fun decode(bytes: BinaryBytes, offset: Long): List<BinaryCharacter>
    /** Encodes explicit user input without automatically adding a BOM or normalizing newlines. */
    fun encode(text: String): BinaryBytes
    /** Decodes an explicit bounded selection for clipboard text, preserving control characters where possible. */
    fun decodeText(bytes: BinaryBytes): String = decode(bytes, 0).joinToString("") { it.text }
}

/** Default single-byte printable ASCII view. */
object AsciiBinaryTextCodec : BinaryTextCodec {
    override val id: String = "ASCII"
    override val label: String = "ASCII"
    override val contextBytes: Int = 0
    override fun decode(bytes: BinaryBytes, offset: Long): List<BinaryCharacter> =
        List(bytes.size) { index ->
            val value = bytes[index].toInt() and 255
            BinaryCharacter(offset + index, 1, if (value in 32..126) value.toChar().toString() else "·")
        }
    override fun encode(text: String): BinaryBytes {
        require(text.all { it.code < 128 }) { "Text cannot be represented as ASCII" }
        return BinaryBytes.owned(ByteArray(text.length) { text[it].code.toByte() })
    }
    override fun decodeText(bytes: BinaryBytes): String = bytes.toByteArray().toString(Charsets.US_ASCII)
}
