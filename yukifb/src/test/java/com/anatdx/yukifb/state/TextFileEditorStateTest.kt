package com.anatdx.yukifb.state

import com.anatdx.yukifb.model.TextFileEncoding
import com.anatdx.yukifb.model.TextLineEnding
import com.anatdx.yukifb.model.convertTextLineEndings
import com.anatdx.yukifb.model.detectTextLineEndings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextFileEditorStateTest {
    @Test
    fun detectsEveryLineEndingWithoutDoubleCountingCrLf() {
        val detection = detectTextLineEndings("a\r\nb\nc\rd\r\n")

        assertEquals(1, detection.lfCount)
        assertEquals(1, detection.crCount)
        assertEquals(2, detection.crlfCount)
        assertEquals(4, detection.totalCount)
        assertEquals(TextLineEnding.CRLF, detection.dominant)
        assertTrue(detection.isMixed)
    }

    @Test
    fun tiedDetectionUsesFirstSequenceEncountered() {
        val detection = detectTextLineEndings("a\rb\n")

        assertEquals(TextLineEnding.CR, detection.dominant)
    }

    @Test
    fun documentWithoutNewlinesHasNoDetectedEnding() {
        val detection = detectTextLineEndings("one line")

        assertNull(detection.dominant)
        assertEquals(0, detection.totalCount)
        assertFalse(detection.isMixed)
    }

    @Test
    fun utf8BomUsesUtf8CharsetWithExplicitSignature() {
        assertEquals("UTF-8", TextFileEncoding.UTF_8_BOM.charsetName)
        assertEquals("UTF-8 BOM", TextFileEncoding.UTF_8_BOM.displayName)
        assertTrue(TextFileEncoding.UTF_8_BOM.usesByteOrderMark)
        assertFalse(TextFileEncoding.UTF_8.usesByteOrderMark)
    }

    @Test
    fun convertsMixedLineEndingsGlobally() {
        assertEquals(
            "a\r\nb\r\nc\r\nd",
            convertTextLineEndings("a\nb\rc\r\nd", TextLineEnding.CRLF),
        )
    }

    @Test
    fun editorNormalizesInputAndKeepsDetectedSaveFormat() {
        val state = TextFileEditorState(
            initialText = "one\r\ntwo\r\nthree",
            initialEncoding = TextFileEncoding.UTF_16_LE,
        )

        assertEquals("one\ntwo\nthree", state.text)
        assertEquals(TextFileEncoding.UTF_16_LE, state.encoding)
        assertEquals(TextLineEnding.CRLF, state.lineEnding)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun formatChangesParticipateInDirtyStateAndSaveRequest() {
        val state = TextFileEditorState("one\ntwo")

        state.selectEncoding(TextFileEncoding.UTF_8_BOM)
        state.selectLineEnding(TextLineEnding.CR)

        val request = state.createSaveRequest()
        assertEquals("one\rtwo", request.text)
        assertEquals(TextFileEncoding.UTF_8_BOM, request.encoding)
        assertEquals(TextLineEnding.CR, request.lineEnding)
        assertTrue(state.hasUnsavedChanges)

        state.markSaved()
        assertFalse(state.hasUnsavedChanges)
        assertEquals(TextLineEnding.CR, state.sourceLineEndings.dominant)
    }

    @Test
    fun selectingDominantEndingForMixedSourceRequestsNormalization() {
        val state = TextFileEditorState("one\r\ntwo\nthree\r\n")

        assertEquals(TextLineEnding.CRLF, state.lineEnding)
        assertFalse(state.hasUnsavedChanges)

        state.selectLineEnding(TextLineEnding.CRLF)

        assertTrue(state.hasUnsavedChanges)
        assertEquals("one\r\ntwo\r\nthree\r\n", state.createSaveRequest().text)
    }

    @Test
    fun replacingDocumentEstablishesNewDecodedBaseline() {
        val state = TextFileEditorState("wrong")
        state.selectEncoding(TextFileEncoding.UTF_32_BE)

        state.replaceDocument(
            text = "正确\r文本",
            encoding = TextFileEncoding.UTF_16_BE,
        )

        assertEquals("正确\n文本", state.text)
        assertEquals(TextFileEncoding.UTF_16_BE, state.encoding)
        assertEquals(TextLineEnding.CR, state.lineEnding)
        assertFalse(state.hasUnsavedChanges)
    }
}
