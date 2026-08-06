package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.Constants
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.ml.TranslationBridge
import com.yomu.ml.TranslationOutput
import com.yomu.ml.TranslationStatus
import com.yomu.ml.opusmt.OpusMtTranslationBridge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationEngineSelector @Inject constructor(
    private val mlKitBridge: MlKitTranslationBridge,
    private val opusMtBridge: OpusMtTranslationBridge,
    private val llamaBridge: LlamaTranslationBridge,
    private val sharedPreferences: SharedPreferences
) : TranslationBridge {

    private var current: TranslationEngineType = loadEngine()

    val engineId: String get() = current.id

    fun currentEngine(): TranslationEngineType = current

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
