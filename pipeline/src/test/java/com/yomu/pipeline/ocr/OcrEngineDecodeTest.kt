package com.yomu.pipeline.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrEngineDecodeTest {

    private val vocab = mapOf(
        0 to "[PAD]",
        1 to "[CLS]",
        2 to "[SEP]",
        3 to "[UNK]",
        10 to "私",
        11 to "が",
        12 to "全",
        13 to "部",
        20 to "play",
        21 to "##ing"
    )

    @Test
    fun decode_skipsSpecialTokens() {
        val ids = listOf(1, 10, 11, 12, 13, 2, 0, 0)
        assertEquals("私が全部", OcrEngine.decodeTokens(ids, vocab))
    }

    @Test
    fun decode_joinsJapaneseWithoutSpaces() {
        assertEquals("私が", OcrEngine.decodeTokens(listOf(10, 11), vocab))
    }

    @Test
    fun decode_mergesWordpieceContinuations() {
        assertEquals("playing", OcrEngine.decodeTokens(listOf(20, 21), vocab))
    }

    @Test
    fun decode_skipsUnknownIds() {
        assertEquals("私", OcrEngine.decodeTokens(listOf(10, 999), vocab))
    }

    @Test
    fun decode_emptyWhenOnlySpecialTokens() {
        assertEquals("", OcrEngine.decodeTokens(listOf(1, 2, 0), vocab))
    }
}
