package com.anatdx.yukifb.hex.state

import com.anatdx.yukifb.hex.backend.BinaryBytes
import com.anatdx.yukifb.hex.ui.GRID_ROWS
import com.anatdx.yukifb.hex.ui.gridBaseRow
import kotlin.test.*

class HexToolsTest {
    @Test fun parsingRejectsHalfBytesAndOverflowWithoutChangingRadixImplicitly() {
        assertContentEquals(byteArrayOf(0, 0xff.toByte(), 0x10), parseHexBytes("0x00 ff 10").toByteArray())
        assertFailsWith<IllegalArgumentException> { parseHexBytes("ABC") }
        assertFailsWith<IllegalArgumentException> { parseHexBytes("00 11", 1) }
        assertEquals(16L, parseHexEditorOffset("10", true, 32))
        assertEquals(10L, parseHexEditorOffset("10", false, 32))
        assertFailsWith<IllegalArgumentException> { parseHexEditorOffset("FFFFFFFFFFFFFFFF", true, Long.MAX_VALUE) }
    }

    @Test fun inspectorHonorsEndiannessAndIncompleteValues() {
        val bytes = BinaryBytes.copyOf(byteArrayOf(1, 2, 3, 4))
        val little = inspectBinaryValues(bytes, true).associate { it.type to it.value }
        val big = inspectBinaryValues(bytes, false).associate { it.type to it.value }
        assertEquals("67305985", little["u32"])
        assertEquals("16909060", big["u32"])
        assertNull(little["i64"])
        assertNull(big["f64"])
        assertEquals("18446744073709551615", inspectBinaryValues(BinaryBytes.copyOf(ByteArray(8) { -1 }), true).first { it.type == "u64" }.value)
    }

    @Test fun virtualRowsRemainBoundedBeyondIntRowCount() {
        val total = 128L * 1024 * 1024 * 1024 / 8
        for (target in listOf(0L, Int.MAX_VALUE.toLong() + 100, total - 1)) {
            val base = gridBaseRow(target, total)
            assertTrue(target - base in 0 until GRID_ROWS.toLong())
        }
    }
}
