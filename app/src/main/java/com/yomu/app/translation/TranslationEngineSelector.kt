package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.Constants
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.ml.TranslationBridge
import com.yomu.ml.TranslationOutput
import com.yomu.ml.TranslationStatus
import com.yomu.ml.opusmt.OpusMtTranslationBridge
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationEngineSelector @Inject constructor(
    private val mlKitBridge: MlKitTranslationBridge,
    private val opusMtBridge: OpusMtTranslationBridge,
    private val llamaBridge: LlamaTranslationBridge,
    private val sharedPreferences: SharedPreferences,
    // filesDir/models/llm — where curated GGUFs live. Defaulted so unit tests that mock the bridges
    // need not supply it; DI passes the real directory.
    private val llmModelsDir: File = File("")
) : TranslationBridge {

    private var current: TranslationEngineType = loadEngine()

    // No startup reconfiguration: the DI provider constructs the llama bridge from the same
    // persisted PREF_LLM_MODEL, so bridge and selector already agree at startup (#90 part A).

    val engineId: String get() = current.id

    fun currentEngine(): TranslationEngineType = current

    /** The curated LLM currently in the translation slot (ADR-0009); default when nothing is picked. */
    fun currentLlmModel(): LlmModelOption =
        LlmModelCatalog.selectedOrDefault(sharedPreferences.getString(Constants.PREF_LLM_MODEL, null))

    /**
     * Choose which curated LLM occupies the translation slot (#90 part A). Persists the id and
     * reloads the llama bridge onto the new GGUF. Rejects a non-hosted entry — the HF-auth download
     * path is not wired yet (#90 part C).
     */
    fun selectLlmModel(option: LlmModelOption) {
        if (option.tier != LlmModelTier.HOSTED) return
        sharedPreferences.edit().putString(Constants.PREF_LLM_MODEL, option.id).apply()
        applyLlmModel(option)
    }

    private fun applyLlmModel(option: LlmModelOption) {
        val path = File(llmModelsDir, option.ggufFileName).absolutePath
        llamaBridge.selectModel(path, option.idKeyedBatch)
    }

    fun selectEngine(type: TranslationEngineType) {
        if (type == current) return
        activeBridge().clearMemory()
        current = type
        sharedPreferences.edit().putString(Constants.PREF_TRANSLATION_ENGINE, type.id).apply()
    }

    override val status: TranslationStatus
        get() = activeBridge().status

    override suspend fun ensureReady(): Boolean {
        return activeBridge().ensureReady()
    }

    override suspend fun translate(sourceText: String): TranslationOutput? {
        return activeBridge().translate(sourceText)
    }

    override fun supportsBatch(): Boolean {
        return activeBridge().supportsBatch()
    }

    override fun supportsIdKeyedBatch(): Boolean {
        return activeBridge().supportsIdKeyedBatch()
    }

    override fun clearMemory() {
        activeBridge().clearMemory()
    }

    override fun close() {
        mlKitBridge.close()
        opusMtBridge.close()
        llamaBridge.close()
    }

    private fun activeBridge(): TranslationBridge = when (current) {
        TranslationEngineType.ML_KIT -> mlKitBridge
        TranslationEngineType.OPUS_MT -> opusMtBridge
        TranslationEngineType.LLM -> llamaBridge
    }

    private fun loadEngine(): TranslationEngineType {
        val savedId = sharedPreferences.getString(Constants.PREF_TRANSLATION_ENGINE, null)
        return savedId?.let { TranslationEngineType.fromId(it) } ?: TranslationEngineType.ML_KIT
    }
}
