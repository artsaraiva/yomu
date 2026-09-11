package com.yomu.ml

import android.content.Context
import android.util.Log
import com.yomu.core.GenerationParams
import java.io.File

open class LlamaBridge(private val context: Context?) : TextGenerationBridge {

    companion object {
        private const val TAG = "LlamaBridge"
        private val DEFAULT_N_THREADS = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
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
        return loadModel(modelPath, nCtx, nGpuLayers, DEFAULT_N_THREADS)
    }

    open fun loadModel(modelPath: String, nCtx: Int, nGpuLayers: Int, nThreads: Int): Boolean {
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
            nGpuLayers,
            nThreads
        )
        Log.i(TAG, "loadModel completed loaded=$isLoaded")
        return isLoaded
    }

    open fun clearMemory() {
        if (isLoaded) {
            nativeClearMemory()
        }
    }

    override fun generate(
        prompt: String,
        params: GenerationParams,
        maxTokens: Int,
        timeoutMs: Int
    ): GenerationResult {
        if (!isLoaded) {
            Log.w(TAG, "generate skipped model_not_loaded")
            return GenerationResult.NotLoaded(durationMs = 0L)
        }
        val startMs = System.currentTimeMillis()
        try {
            val result = nativeGenerate(prompt, maxTokens, timeoutMs, params.samplerArray(), params.seed)
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

    private external fun nativeLoadModel(path: String, nCtx: Int, nGpuLayers: Int, nThreads: Int): Boolean
    // samplerParams carries the sampler knobs in GenerationParams.SAMPLER_INDEX order; seed is a
    // separate Int because 0xFFFFFFFF (LLAMA_DEFAULT_SEED) has no exact Float representation.
    private external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        timeoutMs: Int,
        samplerParams: FloatArray,
        seed: Int
    ): String?
    private external fun nativeClearMemory()
    private external fun nativeRelease()
}
