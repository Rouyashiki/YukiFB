package com.anatdx.yukifb.hex.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.anatdx.yukifb.hex.backend.BinaryBytes
import com.anatdx.yukifb.hex.backend.BinaryDataSource
import com.anatdx.yukifb.hex.state.HexEditorController
import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.ui.YukiFBProvider

@Preview(widthDp = 393, heightDp = 820)
@Composable
private fun HexEditorPreview() {
    val scope = rememberCoroutineScope()
    val controller = remember {
        HexEditorController(object : BinaryDataSource {
            override val id = EntryId("preview-binary")
            override val revision = "preview"
            override val size = 4096L
            override suspend fun read(offset: Long, length: Int) = BinaryBytes.copyOf(ByteArray(length) { (offset + it).toByte() })
        }, scope)
    }
    MaterialTheme { YukiFBProvider { HexEditor("firmware.bin", controller, {}, confirmClose = false) } }
}
