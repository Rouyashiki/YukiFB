package com.anatdx.yukifb.model

/**
 * A text encoding that can be selected by the YukiFB text editor.
 *
 * The host remains responsible for decoding and encoding file content, including
 * consuming or emitting a byte-order mark when [usesByteOrderMark] is `true`.
 */
enum class TextFileEncoding(
    /** Canonical charset name suitable for host-side charset lookup. */
    val charsetName: String,
    /** Short label shown by the editor. */
    val displayName: String = charsetName,
    /** Whether the host should consume or emit a byte-order mark. */
    val usesByteOrderMark: Boolean = false,
) {
    /** UTF-8 without a byte-order mark. */
    UTF_8("UTF-8"),

    /** UTF-8 with a byte-order mark. */
    UTF_8_BOM(
        charsetName = "UTF-8",
        displayName = "UTF-8 BOM",
        usesByteOrderMark = true,
    ),

    /** Big-endian UTF-16 without an implied byte-order mark. */
    UTF_16_BE("UTF-16BE"),

    /** Little-endian UTF-16 without an implied byte-order mark. */
    UTF_16_LE("UTF-16LE"),

    /** Big-endian UTF-32 without an implied byte-order mark. */
    UTF_32_BE("UTF-32BE"),

    /** Little-endian UTF-32 without an implied byte-order mark. */
    UTF_32_LE("UTF-32LE"),
}

/**
 * A line-ending sequence supported by the YukiFB text editor.
 */
enum class TextLineEnding(
    /** Characters written between lines when the document is saved. */
    val sequence: String,
) {
    /** Line feed (`\n`). */
    LF("\n"),

    /** Carriage return (`\r`). */
    CR("\r"),

    /** Carriage return followed by line feed (`\r\n`). */
    CRLF("\r\n"),
}

/**
 * Counts line-ending sequences found in a text document.
 *
 * [dominant] is the most common sequence, or the first sequence encountered when
 * counts are tied. It is `null` when the document contains no line endings.
 */
data class TextLineEndingDetection(
    /** Most common line ending, or `null` when none was found. */
    val dominant: TextLineEnding?,
    /** Number of standalone line-feed sequences. */
    val lfCount: Int,
    /** Number of standalone carriage-return sequences. */
    val crCount: Int,
    /** Number of carriage-return/line-feed sequences. */
    val crlfCount: Int,
) {
    /** Total number of line-ending sequences. */
    val totalCount: Int
        get() = lfCount + crCount + crlfCount

    /** Whether more than one kind of line ending occurs in the document. */
    val isMixed: Boolean
        get() = listOf(lfCount, crCount, crlfCount).count { it > 0 } > 1
}

/**
 * A host-facing request to persist text using [encoding] and [lineEnding].
 *
 * [text] already uses [lineEnding] throughout. The host only needs to encode it,
 * add a byte-order mark when required by [encoding], and persist the resulting bytes.
 */
data class TextFileSaveRequest(
    /** Text whose line endings have been converted for persistence. */
    val text: String,
    /** Encoding the host should use for persistence. */
    val encoding: TextFileEncoding,
    /** Line ending already applied to [text]. */
    val lineEnding: TextLineEnding,
)

/**
 * Detects standalone LF, standalone CR, and CRLF sequences in [text].
 */
fun detectTextLineEndings(text: CharSequence): TextLineEndingDetection {
    val counts = IntArray(TextLineEnding.entries.size)
    val firstIndexes = IntArray(TextLineEnding.entries.size) { Int.MAX_VALUE }
    var index = 0

    while (index < text.length) {
        val lineEnding = when (text[index]) {
            '\r' -> if (index + 1 < text.length && text[index + 1] == '\n') {
                TextLineEnding.CRLF
            } else {
                TextLineEnding.CR
            }

            '\n' -> TextLineEnding.LF
            else -> null
        }

        if (lineEnding == null) {
            index += 1
            continue
        }

        val ordinal = lineEnding.ordinal
        counts[ordinal] += 1
        firstIndexes[ordinal] = minOf(firstIndexes[ordinal], index)
        index += lineEnding.sequence.length
    }

    var dominant: TextLineEnding? = null
    var dominantCount = 0
    var dominantFirstIndex = Int.MAX_VALUE
    TextLineEnding.entries.forEach { lineEnding ->
        val count = counts[lineEnding.ordinal]
        val firstIndex = firstIndexes[lineEnding.ordinal]
        if (
            count > dominantCount ||
            count == dominantCount && count > 0 && firstIndex < dominantFirstIndex
        ) {
            dominant = lineEnding
            dominantCount = count
            dominantFirstIndex = firstIndex
        }
    }

    return TextLineEndingDetection(
        dominant = dominant,
        lfCount = counts[TextLineEnding.LF.ordinal],
        crCount = counts[TextLineEnding.CR.ordinal],
        crlfCount = counts[TextLineEnding.CRLF.ordinal],
    )
}

/**
 * Converts all LF, CR, and CRLF sequences in [text] to [lineEnding].
 */
fun convertTextLineEndings(
    text: CharSequence,
    lineEnding: TextLineEnding,
): String {
    val converted = StringBuilder(text.length)
    var segmentStart = 0
    var index = 0

    while (index < text.length) {
        val sequenceLength = when (text[index]) {
            '\r' -> if (index + 1 < text.length && text[index + 1] == '\n') 2 else 1
            '\n' -> 1
            else -> {
                index += 1
                continue
            }
        }

        converted.append(text, segmentStart, index)
        converted.append(lineEnding.sequence)
        index += sequenceLength
        segmentStart = index
    }

    converted.append(text, segmentStart, text.length)
    return converted.toString()
}
