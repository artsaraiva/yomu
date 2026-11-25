package com.yomu.pipeline.ocr

import android.graphics.Bitmap
import android.graphics.Color
import com.yomu.ml.HayaiDecode
import com.yomu.ml.OnnxRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class OcrEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val steps = mutableListOf<Long>()
    private var prefillRows = 0

    /** A decode whose prefill picks [first] and whose steps pick each next id of [then]. */
    private fun decode(first: Int, then: List<Int>): HayaiDecode {
        val next = ArrayDeque(then)
        return Mockito.mock(HayaiDecode::class.java) { invocation ->
            when (invocation.method.name) {
                "getFirstLogits" -> logitsPeakingAt(first)
                "step" -> {
                    steps += invocation.getArgument<Long>(0)
                    logitsPeakingAt(next.removeFirst())
                }
                else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
            }
        }
    }

    private fun runtime(decode: HayaiDecode?): OnnxRuntime = Mockito.mock(OnnxRuntime::class.java) { invocation ->
        when (invocation.method.name) {
            "loadModel", "isModelLoaded" -> true
            "startHayaiDecode" -> decode.also { prefillRows = invocation.getArgument(5) }
            else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
    }

    private fun logitsPeakingAt(id: Int) = FloatArray(16004).also { it[id] = 50f }

    private fun engine(runtime: OnnxRuntime, tokenizer: File = tokenizer()): OcrEngine =
        OcrEngine(runtime).apply { loadModel("prefill.onnx", "decode.onnx", tokenizer.path, positions().path) }

    private fun tokenizer() = tmp.newFile().apply {
        writeText("""{"added_tokens":[{"id":16002,"content":"<eos>","special":true}],"model":{"vocab":{"ãģ§":275,"!?":1144}}}""")
    }

    private fun positions() = tmp.newFile().apply {
        val header = "{'descr': '<f4', 'fortran_order': False, 'shape': (4, 2), }".padEnd(117) + "\n"
        val buffer = ByteBuffer.allocate(10 + header.length + 8 * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x93.toByte()).put("NUMPY".toByteArray()).put(1).put(0).putShort(header.length.toShort()).put(header.toByteArray())
        writeBytes(buffer.array())
    }

    private fun crop(width: Int = 111, height: Int = 166) =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    @Test
    fun `reads a crop by feeding each chosen token back until the stop token`() {
        val read = engine(runtime(decode(first = 275, then = listOf(1144, 16002)))).extractText(crop())

        assertEquals("で!?", read?.text)
        assertEquals(1f, checkNotNull(read).confidence, 1e-4f)
        assertEquals(listOf(275L, 1144L), steps)
    }

    @Test
    fun `the pad token also ends a read`() {
        val read = engine(runtime(decode(first = 275, then = listOf(16000)))).extractText(crop())

        assertEquals("で", read?.text)
    }

    @Test
    fun `the crop reaches the prefill on its shape-keeping patch grid`() {
        engine(runtime(decode(first = 16002, then = emptyList()))).extractText(crop(width = 111, height = 166))

        assertEquals(17, prefillRows)
    }

    @Test
    fun `a failed prefill gives no read`() {
        assertNull(engine(runtime(decode = null)).extractText(crop()))
    }

    @Test
    fun `without its tokenizer the engine is not loaded and reads nothing`() {
        val engine = OcrEngine(runtime(decode(first = 275, then = listOf(16002))))

        assertFalse(engine.loadModel("prefill.onnx", "decode.onnx", File(tmp.root, "missing.json").path, positions().path))
        assertFalse(engine.isModelLoaded())
        assertNull(engine.extractText(crop()))
    }

    @Test
    fun `loads when both graphs, the tokenizer and the positions load`() {
        val engine = OcrEngine(runtime(decode(first = 275, then = listOf(16002))))

        assertTrue(engine.loadModel("prefill.onnx", "decode.onnx", tokenizer().path, positions().path))
    }
}
