package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.Constants
import com.yomu.core.GenerationBound
import com.yomu.core.GenerationParams
import com.yomu.core.TranslationSlot
import com.yomu.ml.LlamaTranslationBridge
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationModelSelection @Inject constructor(
    private val llamaSlot: LlamaTranslationBridge,
    private val sharedPreferences: SharedPreferences,
    private val llmModelsDir: File = File(""),
    private val totalMemBytes: Long = 0L,
    translators: Set<Translator> = setOf(Translator.onDevice(llamaSlot))
) {
    private val translators = translators.associateBy { it.id }
    private val onDevice = this.translators.getValue(Translator.ON_DEVICE_ID)
    private val generationStore = GenerationProfileStore(sharedPreferences)
    private val limitsStore = ResourceLimitsStore(sharedPreferences)

    /** The slot of the translator the reader chose, or the on-device slot when that one can't run. */
    fun current(): TranslationSlot = activeTranslator().slot()

    /** The translator that takes the next page: the one the reader chose, or the on-device one when that can't run. */
    fun activeTranslator(): Translator = translators[storedTranslatorId()]?.takeIf { it.canRun() } ?: onDevice

    /** Every registered translator, in the order they were registered. */
    fun translators(): Collection<Translator> = translators.values

    /** The registered translator with [id], or null when none is. */
    fun translator(id: String): Translator? = translators[id]

    fun selectTranslator(translator: Translator) {
        sharedPreferences.edit().putString(Constants.PREF_TRANSLATOR, translator.id).apply()
    }

    private fun storedTranslatorId(): String? =
        runCatching { sharedPreferences.getString(Constants.PREF_TRANSLATOR, null) }.getOrNull()

    fun currentLlmModel(): LlmModelOption = LlmModelCatalog.selectedOrDefault(sharedPreferences.storedLlmModelId())

    /** True when a model id is stored but unreadable or no longer in the catalog, so [currentLlmModel] fell back. */
    fun storedLlmModelRecovered(): Boolean =
        sharedPreferences.contains(Constants.PREF_LLM_MODEL) &&
            !llmModelCleared() &&
            LlmModelCatalog.fromId(sharedPreferences.storedLlmModelId()) == null

    /** An empty stored id marks a slot whose model was deleted; picking a model stores its id over it. */
    fun llmModelCleared(): Boolean = sharedPreferences.storedLlmModelId() == ""

    fun clearLlmModel() {
        sharedPreferences.edit().putString(Constants.PREF_LLM_MODEL, "").apply()
    }

    /** Drop the stored values that loaded as defaults, so their warning is shown once, not every visit. */
    fun clearRecovered() {
        if (storedLlmModelRecovered()) sharedPreferences.edit().remove(Constants.PREF_LLM_MODEL).apply()
        generationStore.forget(generationProfile().recovered)
    }

    suspend fun selectLlmModel(option: LlmModelOption) {
        sharedPreferences.edit().putString(Constants.PREF_LLM_MODEL, option.id).apply()
        applyLlmProfile(option)
    }

    /** The effective profile: the selected deliverable's maker sampling under the reader's saved fields. */
    fun generationProfile(): GenerationProfileStore.Loaded = generationStore.load(currentLlmModel().generationDefaults)

    /** What "Reset to defaults" returns to — the selected deliverable's maker sampling (ADR-0017). */
    fun generationDefaults(): GenerationParams = currentLlmModel().generationDefaults

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

    fun resourceLimit(limit: ResourceLimit): Int = limitsStore.load(limit)

    /** Persist one resource cap; threads and context reach the slot, which reloads the model on its next page (#79). */
    suspend fun saveResourceLimit(limit: ResourceLimit, value: Int): Boolean {
        if (!limitsStore.save(limit, value)) return false
        applyLlmProfile(currentLlmModel())
        return true
    }

    suspend fun resetResourceLimits() {
        limitsStore.reset()
        applyLlmProfile(currentLlmModel())
    }

    fun lastPageDurationMs(): Long? = llamaSlot.lastPageDurationMs

    private suspend fun applyLlmProfile(option: LlmModelOption) {
        llamaSlot.selectModel(
            LlmModelCatalog.profileFor(
                option, llmModelsDir, generationStore.load(option.generationDefaults).params, limitsStore.runtime(), limitsStore.fitBudget(totalMemBytes)
            )
        )
    }

    fun close() {
        translators.values.forEach(Translator::close)
    }
}
