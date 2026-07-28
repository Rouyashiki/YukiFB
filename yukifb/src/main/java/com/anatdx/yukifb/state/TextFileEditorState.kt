package com.anatdx.yukifb.state

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.anatdx.yukifb.model.TextFileEncoding
import com.anatdx.yukifb.model.TextFileSaveRequest
import com.anatdx.yukifb.model.TextLineEnding
import com.anatdx.yukifb.model.TextLineEndingDetection
import com.anatdx.yukifb.model.convertTextLineEndings
import com.anatdx.yukifb.model.detectTextLineEndings

/**
 * Hoistable state for the lightweight YukiFB text editor.
 *
 * Text is normalized to LF internally so editing and line numbers remain consistent.
 * The host should call [markSaved] only after persisting [createSaveRequest] successfully.
 *
 * @param initialText text already decoded by the host.
 * @param initialEncoding encoding the host used to decode [initialText].
 * @param initialLineEnding explicit initial save format, or `null` to detect it from
 * [initialText] and fall back to LF when no line ending exists.
 */
@Stable
class TextFileEditorState private constructor(
    initialText: String,
    initialEncoding: TextFileEncoding,
    initialLineEnding: TextLineEnding?,
    @Suppress("UNUSED_PARAMETER") constructorMarker: Unit,
) {
    /** Creates empty UTF-8 editor state. */
    constructor() : this(initialText = "")

    /**
     * Creates editor state for UTF-8 text, retaining the 0.1.0 constructor API.
     */
    constructor(initialText: String = "") : this(
        initialText = initialText,
        initialEncoding = TextFileEncoding.UTF_8,
        initialLineEnding = null,
        constructorMarker = Unit,
    )

    /**
     * Creates editor state for text decoded with an explicit format.
     */
    constructor(
        initialText: String,
        initialEncoding: TextFileEncoding,
        initialLineEnding: TextLineEnding? = null,
    ) : this(
        initialText = initialText,
        initialEncoding = initialEncoding,
        initialLineEnding = initialLineEnding,
        constructorMarker = Unit,
    )

    private val initialLineEndingDetection = detectTextLineEndings(initialText)
    private val normalizedInitialText = convertTextLineEndings(initialText, TextLineEnding.LF)

    /** Compose text input state, including text, selection, and undo history. */
    val textFieldState: TextFieldState = TextFieldState(normalizedInitialText)

    private var savedText by mutableStateOf(normalizedInitialText)
    private var savedEncoding by mutableStateOf(initialEncoding)
    private var savedLineEnding by mutableStateOf(
        initialLineEnding ?: initialLineEndingDetection.dominant ?: TextLineEnding.LF,
    )
    private var lineEndingRewriteRequested by mutableStateOf(false)

    /** Current editor contents. */
    val text: String
        get() = textFieldState.text.toString()

    /** Encoding the host should use for the next save. */
    var encoding: TextFileEncoding by mutableStateOf(initialEncoding)
        private set

    /** Line ending the host should use for the next save. */
    var lineEnding: TextLineEnding by mutableStateOf(savedLineEnding)
        private set

    /** Line endings detected when the current source document was loaded or saved. */
    var sourceLineEndings: TextLineEndingDetection by mutableStateOf(
        initialLineEndingDetection,
    )
        private set

    /** Whether text or its output format differs from the last successful save. */
    val hasUnsavedChanges: Boolean
        get() = text != savedText ||
            encoding != savedEncoding ||
            lineEnding != savedLineEnding ||
            lineEndingRewriteRequested

    /** Changes the encoding requested for future saves. */
    fun selectEncoding(encoding: TextFileEncoding) {
        this.encoding = encoding
    }

    /**
     * Changes the line ending requested for future saves.
     *
     * Selecting the current value for a mixed-line-ending source explicitly requests
     * a global rewrite to that value.
     */
    fun selectLineEnding(lineEnding: TextLineEnding) {
        this.lineEnding = lineEnding
        if (sourceLineEndings.isMixed) {
            lineEndingRewriteRequested = true
        }
    }

    /**
     * Replaces the editor with text newly decoded by the host.
     *
     * This establishes a new saved baseline. It is suitable for reopening the same
     * bytes with a different encoding selected by the user.
     */
    fun replaceDocument(
        text: String,
        encoding: TextFileEncoding,
        lineEnding: TextLineEnding? = null,
    ) {
        val detection = detectTextLineEndings(text)
        val normalizedText = convertTextLineEndings(text, TextLineEnding.LF)
        val detectedOrDefault = lineEnding ?: detection.dominant ?: TextLineEnding.LF

        textFieldState.edit {
            replace(0, length, normalizedText)
            placeCursorAtEnd()
        }
        this.encoding = encoding
        this.lineEnding = detectedOrDefault
        sourceLineEndings = detection
        savedText = normalizedText
        savedEncoding = encoding
        savedLineEnding = detectedOrDefault
        lineEndingRewriteRequested = false
    }

    /**
     * Creates a persistence request with the selected line ending applied globally.
     */
    fun createSaveRequest(): TextFileSaveRequest = TextFileSaveRequest(
        text = convertTextLineEndings(text, lineEnding),
        encoding = encoding,
        lineEnding = lineEnding,
    )

    /** Marks the current contents as successfully persisted by the host. */
    fun markSaved() {
        savedText = text
        savedEncoding = encoding
        savedLineEnding = lineEnding
        sourceLineEndings = detectTextLineEndings(createSaveRequest().text)
        lineEndingRewriteRequested = false
    }
}

/**
 * Remembers a [TextFileEditorState].
 *
 * Initial arguments are used only when the state is first created.
 */
@Composable
fun rememberTextFileEditorState(initialText: String = ""): TextFileEditorState =
    remember { TextFileEditorState(initialText) }

/**
 * Remembers a [TextFileEditorState] for text decoded with an explicit format.
 *
 * Initial arguments are used only when the state is first created.
 */
@Composable
fun rememberTextFileEditorState(
    initialText: String = "",
    initialEncoding: TextFileEncoding,
    initialLineEnding: TextLineEnding? = null,
): TextFileEditorState = remember {
    TextFileEditorState(
        initialText = initialText,
        initialEncoding = initialEncoding,
        initialLineEnding = initialLineEnding,
    )
}

internal fun countTextLines(text: CharSequence): Int =
    detectTextLineEndings(text).totalCount + 1

internal fun lineNumberText(lineCount: Int): String =
    (1..lineCount.coerceAtLeast(1)).joinToString(separator = "\n")
