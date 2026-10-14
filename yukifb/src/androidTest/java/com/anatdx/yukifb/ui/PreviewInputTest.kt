package com.anatdx.yukifb.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.FileEntryType
import com.anatdx.yukifb.state.FileBrowserUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PreviewInputTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var inputModeManager: InputModeManager

    @Test
    fun browserPreviewCannotFocusOrActivateHostActionsWithAKeyboard() {
        val liveFocus = FocusRequester()
        val previewFocus = FocusRequester()
        var previewWasFocused = false
        var hostActions = 0
        var liveActions = 0
        val root = FileEntry(EntryId("root"), null, "root", FileEntryType.DIRECTORY)
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            MaterialTheme {
                YukiFBProvider {
                    Box(Modifier.fillMaxSize()) {
                        FileBrowserPreview(
                            FileBrowserUiState(root), rememberFileBrowserPresentationState(),
                            topBarActions = {
                                // A host can supply its own focus groups inside the toolbar.
                                Box(Modifier.focusGroup()) {
                                    Button(
                                        onClick = { hostActions++ },
                                        modifier = Modifier.focusRequester(previewFocus)
                                            .onFocusChanged { if (it.isFocused) previewWasFocused = true },
                                    ) { Text("Host action") }
                                }
                            },
                        )
                        Button(
                            onClick = { liveActions++ },
                            modifier = Modifier.align(Alignment.BottomCenter).focusRequester(liveFocus),
                        ) { Text("Live action") }
                    }
                }
            }
        }
        compose.runOnIdle { assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard)) }
        compose.runOnIdle {
            assertTrue(liveFocus.requestFocus())
            assertFalse(previewFocus.requestFocus())
        }
        repeat(12) {
            compose.onRoot().performKeyInput { pressKey(Key.Tab); pressKey(Key.Enter) }
        }
        compose.runOnIdle {
            assertFalse(previewWasFocused)
            assertEquals(0, hostActions)
            liveFocus.requestFocus()
        }
        val before = liveActions
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(before + 1, liveActions) }
    }

    @Test
    fun becomingAPreviewBlocksKeysFromAnAlreadyFocusedDescendant() {
        val preview = mutableStateOf(false)
        val focus = FocusRequester()
        var actions = 0
        var keys = 0
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            MaterialTheme {
                Box(if (preview.value) Modifier.blockPreviewInput() else Modifier) {
                    Box(Modifier.focusGroup()) {
                        Button(
                            onClick = { actions++ },
                            modifier = Modifier.focusRequester(focus).onPreviewKeyEvent { keys++; false },
                        ) { Text("Action") }
                    }
                }
            }
        }
        compose.runOnIdle { assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard)) }
        compose.runOnIdle { assertTrue(focus.requestFocus()) }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle {
            assertEquals(1, actions)
            keys = 0
            preview.value = true
        }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle {
            assertEquals(1, actions)
            assertEquals(0, keys)
            preview.value = false
        }
        compose.runOnIdle { assertTrue(focus.requestFocus()) }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(2, actions) }
    }
}
