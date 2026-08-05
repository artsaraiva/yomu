package com.yomu.ml.opusmt

import android.content.Context
import com.yomu.ml.OnnxRuntime
import org.mockito.Mockito.mock
import com.yomu.ml.TranslationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusMtTranslationBridgeTest {

    @Test
    fun greedyDecode_stopsOnEos() {
        val step = DecoderStep { input ->
            val tokenId = when (input.inputId) {
                60715L -> 100L
                100L -> 200L
                200L -> 0L
                else -> 0L
            }
            tokenId to emptyList<PresentCache>()
        }

        val result = OpusMtDecoder.greedyDecode(512, 60715L, 0L, step)

        assertArrayEquals(longArrayOf(100L, 200L, 0L), result)
    }

    @Test
    fun greedyDecode_returnsNullWhenStepFails() {
        val step = DecoderStep { null }

        assertNull(OpusMtDecoder.greedyDecode(512, 60715L, 0L, step))
    }

    @Test
    fun ensureReady_modelMissing_returnsFalseAndError() = runTest {
        val bridge = createBridge()

        val ready = bridge.ensureReady()

        assertFalse(ready)
        assertTrue(bridge.status is TranslationStatus.Error)
    }

    @Test
    fun translate_notReadyAndLoadFails_returnsNull() = runTest {
        val bridge = createBridge()

        assertNull(bridge.translate("こんにちは"))
    }

    @Test
    fun supportsBatch_returnsFalse() {
        val bridge = OpusMtTranslationBridge(
            OnnxRuntime(mock(Context::class.java)),
            encoderModelPath = "",
            decoderModelPath = "",
            tokenizerPath = ""
        )

        assertFalse(bridge.supportsBatch())
    }

    @Test
    fun close_resetsStatusToNotReady() = runTest {
        val bridge = createBridge()
        bridge.ensureReady()

        bridge.close()

        assertEquals(TranslationStatus.NotReady, bridge.status)
    }

    private fun createBridge(): OpusMtTranslationBridge {
        return OpusMtTranslationBridge(
            OnnxRuntime(mock(Context::class.java)),
            encoderModelPath = "/nonexistent/encoder.onnx",
            decoderModelPath = "/nonexistent/decoder.onnx",
            tokenizerPath = "/nonexistent/tokenizer.json"
        )
    }
}
