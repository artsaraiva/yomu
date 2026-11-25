package com.yomu.pipeline.ocr

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Hayai's byte-level BPE vocabulary, for turning generated ids back into text. Each token string spells bytes with
 * GPT-2's printable stand-ins, so a character can span tokens; [special] ids are structure, not text.
 */
internal class ByteLevelVocab(private val tokens: Map<Int, String>, private val special: Set<Int>) {

    /** The UTF-8 text [ids] spell, special and unknown ids left out; a malformed byte run becomes U+FFFD. */
    fun decode(ids: List<Int>): String {
        val bytes = ByteArrayOutputStream()
        for (id in ids) {
            if (id in special) continue
            val token = tokens[id] ?: continue
            for (char in token) bytes.write(BYTE_OF[char] ?: continue)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    companion object {
        /** Reads `model.vocab` and the special `added_tokens` of a Hugging Face tokenizer.json. */
        fun load(file: File): ByteLevelVocab {
            val json = JSONObject(file.readText())
            val vocab = json.getJSONObject("model").getJSONObject("vocab")
            val tokens = vocab.keys().asSequence().associateBy { vocab.getInt(it) }
            val added = json.getJSONArray("added_tokens")
            val special = (0 until added.length()).map { added.getJSONObject(it) }
                .filter { it.optBoolean("special") }
                .map { it.getInt("id") }
                .toSet()
            return ByteLevelVocab(tokens, special)
        }

        /** GPT-2's bytes_to_unicode, reversed: printable Latin-1 bytes stand for themselves, the rest from U+0100 on. */
        private val BYTE_OF: Map<Char, Int> = run {
            val printable = ('!'.code..'~'.code) + ('¡'.code..'¬'.code) + ('®'.code..'ÿ'.code)
            var next = 0
            (0..255).associateBy { byte ->
                if (byte in printable) byte.toChar() else (256 + next++).toChar()
            }
        }
    }
}
