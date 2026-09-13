package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.Constants
import com.yomu.core.GenerationBound
import com.yomu.core.TranslationSlot
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.ml.opusmt.OpusMtTranslationBridge
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngineSelection @Inject constructor(
    private val mlKitSlot: MlKitTranslationBridge,
    private val opusMtSlot: OpusMtTranslationBridge,
    private val llamaSlot: LlamaTranslationBridge,
    private val sharedPreferences: SharedPreferences,
    private val llmModelsDir: File = File("")
) {
    private val generationStore = GenerationProfileStore(sharedPreferences)
    private var selectedEngine: TranslationEngineType = loadEngine()

    fun current(): TranslationSlot = when (selectedEngine) {
        TranslationEngineType.ML_KIT -> mlKitSlot
        TranslationEngineType.OPUS_MT -> opusMtSlot
        TranslationEngineType.LLM -> llamaSlot
    }

    fun currentEngine(): TranslationEngineType = selectedEngine

    fun currentLlmModel(): LlmModelOption = LlmModelCatalog.selectedOrDefault(
        sharedPreferences.getString(Constants.PREF_LLM_MODEL, null)
    )

    /** True when a model id is stored but no longer in the catalog, so [currentLlmModel] fell back. */
    fun storedLlmModelRecovered(): Boolean =
        sharedPreferences.getString(Constants.PREF_LLM_MODEL, null)?.let { LlmModelCatalog.fromId(it) == null } ?: false

    suspend fun selectLlmModel(option: LlmModelOption) {
        sharedPreferences.edit().putString(Constants.PREF_LLM_MODEL, option.id).apply()
        applyLlmProfile(option)
    }

    fun generationProfile(): GenerationProfileStore.Loaded = generationStore.load()

    /**
     * Persist one reader-facing sampler value and hand the new profile to the slot, so the next
     * capture uses it without restarting the overlay (#192). Refused values change nothing.
     */
    suspend fun saveGeneration(bound: GenerationBound, value: Float): Boolean {
        if (!generationStore.save(bound, value)) return false
        applyLlmProfile(currentLlmModel())
        return true
    }

    suspend fun resetGeneration() {
        generationStore.reset()
        applyLlmProfile(currentLlmModel())
    }

    private suspend fun applyLlmProfile(option: LlmModelOption) {
        llamaSlot.selectModel(LlmModelCatalog.profileFor(option, llmModelsDir, generationStore.load().params))
    }

    fun selectEngine(type: TranslationEngineType) {
        if (type == selectedEngine) return
        current().endSession()
        selectedEngine = type
        sharedPreferences.edit().putString(Constants.PREF_TRANSLATION_ENGINE, type.id).apply()
    }

    fun close() {
        mlKitSlot.close()
        opusMtSlot.close()
        llamaSlot.close()
    }

    private fun loadEngine(): TranslationEngineType {
        val savedId = sharedPreferences.getString(Constants.PREF_TRANSLATION_ENGINE, null)
        return savedId?.let { TranslationEngineType.fromId(it) } ?: TranslationEngineType.ML_KIT
    }
}
