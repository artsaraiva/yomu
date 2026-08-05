package com.yomu.ml

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaTranslationBridgeTest {

    @Test
    fun translate_successResultMapsToTranslationOutput() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val bridge = LlamaTranslationBridge(
            llamaBridge = FakeLlamaBridge(GenerationResult.Success(" Hello ", 100L)),
            modelPath = model.absolutePath
        )
        bridge.ensureReady()

        val output = bridge.translate("こんにちは")

        assertEquals("Hello", output?.translatedText)
        assertEquals(0.7f, output?.confidence)
        assertEquals(100L, output?.durationMs)
        model.delete()
    }

    @Test
    fun translate_blankResultReturnsNull() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val bridge = LlamaTranslationBridge(
            llamaBridge = FakeLlamaBridge(GenerationResult.Blank(50L)),
            modelPath = model.absolutePath
        )
        bridge.ensureReady()

        assertNull(bridge.translate("こんにちは"))
        model.delete()
    }

    @Test
    fun translate_errorResultReturnsNull() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val bridge = LlamaTranslationBridge(
            llamaBridge = FakeLlamaBridge(GenerationResult.Error("fail", 30L)),
            modelPath = model.absolutePath
        )
        bridge.ensureReady()

        assertNull(bridge.translate("こんにちは"))
        model.delete()
    }

    @Test
    fun translate_notLoadedResultReturnsNull() = runTest {
        val model = File.createTempFile("model", ".gguf")
        val bridge = LlamaTranslationBridge(
            llamaBridge = FakeLlamaBridge(GenerationResult.NotLoaded(0L)),
            modelPath = model.absolutePath
        )
        bridge.ensureReady()

        assertNull(bridge.translate("こんにちは"))
        model.delete()
    }

    @Test
    fun supportsBatch_returnsTrue() {
        val bridge = LlamaTranslationBridge(
            llamaBridge = FakeLlamaBridge(GenerationResult.Success("x", 1L)),
            modelPath = ""
        )

        assertTrue(bridge.supportsBatch())
    }

    private class FakeLlamaBridge(private val result: GenerationResult) : LlamaBridge(null) {
        override val isNativeAvailable: Boolean get() = true
        override val isModelLoaded: Boolean get() = true
        override fun loadModel(modelPath: String, nCtx: Int, nGpuLayers: Int): Boolean = true
        override fun loadModel(modelPath: String, nCtx: Int, nGpuLayers: Int, nThreads: Int): Boolean = true
        override fun generate(prompt: String, maxTokens: Int, temperature: Float): GenerationResult = result
        override fun generate(
            prompt: String,
            maxTokens: Int,
            temperature: Float,
            timeoutMs: Int
        ): GenerationResult = result
        override fun release() {}
        override fun clearMemory() {}
    }
}
