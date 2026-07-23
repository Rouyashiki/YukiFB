package com.anatdx.yukifb.state

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Hoistable state for the lightweight YukiFB text editor.
 *
 * The host should call [markSaved] only after persisting [text] successfully.
 */
@Stable
class TextFileEditorState(initialText: String = "") {
    /** Compose text input state, including text, selection, and undo history. */
    val textFieldState: TextFieldState = TextFieldState(initialText)

    private var savedText by mutableStateOf(initialText)

    /** Current editor contents. */
    val text: String
        get() = textFieldState.text.toString()

    /** Whether current contents differ from the last successfully saved contents. */
    val hasUnsavedChanges: Boolean
        get() = text != savedText

    /** Marks the current contents as successfully persisted by the host. */
    fun markSaved() {
        savedText = text
    }
}

/**
 * Remembers a [TextFileEditorState].
 *
 * [initialText] is used only when the state is first created.
 */
@Composable
fun rememberTextFileEditorState(initialText: String = ""): TextFileEditorState =
    remember { TextFileEditorState(initialText) }

internal fun countTextLines(text: CharSequence): Int =
    text.count { it == '\n' } + 1

internal fun lineNumberText(lineCount: Int): String =
    (1..lineCount.coerceAtLeast(1)).joinToString(separator = "\n")
