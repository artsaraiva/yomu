package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.Constants
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

    suspend fun selectLlmModel(option: LlmModelOption) {
        sharedPreferences.edit().putString(Constants.PREF_LLM_MODEL, option.id).apply()
        llamaSlot.selectModel(
            LlmModelCatalog.profileFor(
                option,
                llmModelsDir,
                sharedPreferences.getBoolean(Constants.PREF_CAPTURE_CONTEXT, false)
            )
        )
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
