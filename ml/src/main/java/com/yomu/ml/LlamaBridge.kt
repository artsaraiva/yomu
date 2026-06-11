package com.yomu.ml

import android.content.Context
import com.yomu.core.Constants
import java.io.File

class LlamaBridge(private val context: Context) {

    companion object {
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

    val isNativeAvailable: Boolean get() = nativeLoaded

    val isModelLoaded: Boolean get() = isLoaded

    private fun getModelFile(modelName: String): File {
        val modelsDir = File(context.filesDir, Constants.MODELS_DIR)
        val llmDir = File(modelsDir, Constants.LLM_MODELS_DIR)
        return File(llmDir, modelName)
    }

    fun loadModel(modelName: String, nCtx: Int = 2048, nGpuLayers: Int = 0): Boolean {
        if (!nativeLoaded) return false

        val modelFile = getModelFile(modelName)
        if (!modelFile.exists()) return false

        isLoaded = nativeLoadModel(
            modelFile.absolutePath,
            nCtx,
            nGpuLayers
        )
        return isLoaded
    }

    fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): String {
        if (!isLoaded) return ""
        try {
            val result = nativeGenerate(prompt, maxTokens, temperature)
            return result ?: ""
        } catch (e: Exception) {
            return ""
        }
    }

    fun release() {
        if (isLoaded) {
            nativeRelease()
            isLoaded = false
        }
    }

    private external fun nativeInit()
    private external fun nativeLoadModel(path: String, nCtx: Int, nGpuLayers: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String?
    private external fun nativeRelease()
}
