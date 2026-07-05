package com.yomu.ml

import android.content.Context
import android.util.Log
import java.io.File

class LlamaBridge(private val context: Context) : TextGenerationBridge {

    companion object {
        private const val TAG = "LlamaBridge"
        private var nativeLoaded = false

        init {
            try {
                System.loadLibrary("llama_jni")
                nativeLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                nativeLoaded = false
            }
        }
    }

    private var isLoaded = false

    override val isNativeAvailable: Boolean get() = nativeLoaded

    override val isModelLoaded: Boolean get() = isLoaded

    fun loadModel(modelPath: String): Boolean {
        return loadModel(modelPath, nCtx = 2048, nGpuLayers = 0)
    }

    override fun loadModel(modelPath: String, nCtx: Int, nGpuLayers: Int): Boolean {
        if (!nativeLoaded) {
            Log.w(TAG, "loadModel skipped native_unavailable")
            return false
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.w(TAG, "loadModel skipped model_missing")
            return false
        }

        isLoaded = nativeLoadModel(
            modelFile.absolutePath,
            nCtx,
            nGpuLayers
        )
        Log.i(TAG, "loadModel completed loaded=$isLoaded")
        return isLoaded
    }

    fun generate(prompt: String): GenerationResult {
        return generate(prompt, maxTokens = 512, temperature = 0.7f)
    }

    override fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): GenerationResult {
        if (!isLoaded) {
            Log.w(TAG, "generate skipped model_not_loaded")
            return GenerationResult.NotLoaded(durationMs = 0L)
        }
        val startMs = System.currentTimeMillis()
        try {
            val result = nativeGenerate(prompt, maxTokens, temperature)
            val durationMs = System.currentTimeMillis() - startMs
            val text = result.orEmpty()
            if (text.isBlank()) {
                Log.w(TAG, "generate completed status=blank durationMs=$durationMs responseLength=${text.length}")
                return GenerationResult.Blank(durationMs = durationMs)
            }
            Log.i(TAG, "generate completed status=success durationMs=$durationMs responseLength=${text.length}")
            return GenerationResult.Success(text = text, durationMs = durationMs)
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            val reason = e::class.simpleName ?: "Exception"
            Log.e(TAG, "generate completed status=error durationMs=$durationMs reason=$reason")
            return GenerationResult.Error(reason = reason, durationMs = durationMs)
        }
    }

    override fun release() {
        if (isLoaded) {
            nativeRelease()
            isLoaded = false
        }
        Log.i(TAG, "release completed")
    }

    private external fun nativeLoadModel(path: String, nCtx: Int, nGpuLayers: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String?
    private external fun nativeRelease()
}
