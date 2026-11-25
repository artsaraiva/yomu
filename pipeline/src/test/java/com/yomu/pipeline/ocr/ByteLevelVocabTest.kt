package com.yomu.pipeline.ocr

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Token strings and expected text come from Hayai's tokenizer.json through the `tokenizers` library (#510). */
@RunWith(AndroidJUnit4::class)
class ByteLevelVocabTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val vocab = ByteLevelVocab(
        tokens = mapOf(7192 to "å®ĩå®Ļ", 275 to "ãģ§", 1144 to "!?", 377 to "...", 11732 to "Sh", 4953 to "Ġn", 5018 to "ow", 50 to "ã", 51 to "ģ§"),
        special = setOf(16001, 16002)
    )

    @Test
    fun `byte-level tokens decode to the UTF-8 text they spell`() {
        assertEquals("宇宙で!?", vocab.decode(listOf(7192, 275, 1144)))
    }

    @Test
    fun `a character split across tokens is joined before decoding`() {
        assertEquals("で", vocab.decode(listOf(50, 51)))
    }

    @Test
    fun `the space marker decodes to a space`() {
        assertEquals("Sh now", vocab.decode(listOf(11732, 4953, 5018)))
    }

    @Test
    fun `special and unknown tokens are left out`() {
        assertEquals("...", vocab.decode(listOf(16001, 377, 99999, 16002)))
    }

    @Test
    fun `loads the model vocabulary and the special added tokens from tokenizer json`() {
        val file = tmp.newFile("tokenizer.json").apply {
            writeText(
                """{"added_tokens":[{"id":16001,"content":"<bos>","special":true}],
                   "model":{"type":"BPE","vocab":{"ãģ§":275,"!?":1144},"merges":[]}}"""
            )
        }

        val loaded = ByteLevelVocab.load(file)

        assertEquals("で!?", loaded.decode(listOf(16001, 275, 1144)))
    }
}
