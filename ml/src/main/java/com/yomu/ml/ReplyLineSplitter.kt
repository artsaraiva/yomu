package com.yomu.ml

import java.io.ByteArrayOutputStream

/**
 * Cuts streamed reply bytes into lines at newline bytes and decodes each line only once it is whole.
 * A newline byte never occurs inside a multi-byte UTF-8 sequence, so a character split across chunks
 * is whole again before it is decoded (#235). An unterminated tail is never emitted.
 */
internal class ReplyLineSplitter(private val onLine: (String) -> Unit) {
    private val pending = ByteArrayOutputStream()

    fun feed(bytes: ByteArray) {
        var start = 0
        bytes.forEachIndexed { index, byte ->
            if (byte == NEWLINE) {
                pending.write(bytes, start, index - start)
                onLine(decodeGenerated(pending.toByteArray()))
                pending.reset()
                start = index + 1
            }
        }
        pending.write(bytes, start, bytes.size - start)
    }

    private companion object {
        const val NEWLINE = '\n'.code.toByte()
    }
}
