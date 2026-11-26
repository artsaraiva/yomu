package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.db.entities.TranslationEntity
import com.yomu.app.service.LanguageSwitch
import com.yomu.app.service.ModelManager
import com.yomu.app.service.ModelSlotSelection
import com.yomu.app.service.PickerGroup
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.translation.FitBudget
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.app.translation.TranslatorPick
import com.yomu.app.translation.TranslatorPickerEntry
import com.yomu.core.RuntimeLimits
import com.yomu.core.TargetLanguage

data class HomeUiState(
    val isServiceRunning: Boolean = false,
    val readiness: Readiness = Readiness.NeedsBoth,
    val models: List<ModelEntity> = emptyList(),
    val modelsLoading: Boolean = true,
    val setupComplete: Boolean = false,
    val setupVisible: Boolean = false,
    val setupStarted: Boolean = false,
    val setupDownloadsReady: Boolean = false,
    val setupError: Boolean = false,
    /** The model setup is downloading now and how far it is, or null between downloads. */
    val setupDownload: ModelManager.DownloadProgress? = null,
    /** The chosen model whose setup download failed, or null. */
    val setupFailedId: String? = null,
    val deliverables: Map<ModelType, List<SlotDeliverable>> = emptyMap(),
    val chosenIds: Map<ModelType, String> = emptyMap(),
    val translationSelectedId: String? = null,
    val translationPendingId: String? = null,
    val targetLanguage: TargetLanguage = TargetLanguage.ENGLISH,
    /** The model switch a language choice is waiting on the reader to accept or decline (#338), or null. */
    val languageOffer: LanguageSwitch.Offer? = null,
    /** The registered translators' groups, listed in the picker after the phone's (#534). */
    val translatorGroups: List<PickerGroup> = emptyList(),
    /** A translator's pick waiting on the reader to agree, such as the first server pick's notice, or null. */
    val translatorConfirm: TranslatorConfirm? = null,
    val setupDownloadBytes: Long = 0L,
    val fitBudget: FitBudget = FitBudget(0L, LlmModelCatalog.DEFAULT_FIT_BUDGET_PERCENT, RuntimeLimits.DEFAULT_CONTEXT_TOKENS),
    val onMeteredNetwork: Boolean = false,
    val pagesTranslatedToday: Int = 0,
    val translations: List<TranslationEntity> = emptyList(),
    val hasRead: Boolean = false,
    val historyLoading: Boolean = true,
    val historyError: Boolean = false,
    val clearError: Boolean = false,
    val serviceError: Boolean = false,
    val confirmation: String? = null
) {
    val readingStatus: ReadingStatus
        get() = resolveReadingStatus(readiness == Readiness.Ready && setupComplete, isServiceRunning)

    fun fits(deliverable: SlotDeliverable): Boolean = ModelSlotSelection.fits(deliverable.id, fitBudget)

    fun needsSlowWarning(deliverable: SlotDeliverable): Boolean =
        ModelSlotSelection.needsSlowWarning(deliverable, fitBudget)

    fun languageBlock(deliverable: SlotDeliverable): String? = ModelSlotSelection.languageBlock(deliverable, targetLanguage)

    fun languageUnavailable(language: TargetLanguage): String? =
        ModelSlotSelection.languageUnavailable(language, deliverables[ModelType.LLM].orEmpty(), checkedTranslatorEntry() != null, ::fits)

    /**
     * The phone models the picker lists, in catalog order: the downloaded ones, any downloading, and the selected and
     * waiting picks. The rest of the catalog is downloaded from Settings, so the server's group stays in reach.
     */
    fun phoneRows(): List<SlotDeliverable> {
        val onPhone = models.filter { it.status == ModelStatus.READY || it.status == ModelStatus.DOWNLOADING }.mapTo(mutableSetOf()) { it.id }
        val picked = setOfNotNull(translationSelectedId, translationPendingId)
        return deliverables[ModelType.LLM].orEmpty().filter { it.id in onPhone || it.id in picked }
    }

    /** Whether [deliverable]'s row is checked: the selected phone model, while no translator's entry takes the next page. */
    fun checked(deliverable: SlotDeliverable): Boolean = checkedTranslatorEntry() == null && deliverable.id == translationSelectedId

    /** The translator entry that takes the next page, or null while the phone does. */
    private fun checkedTranslatorEntry(): TranslatorPickerEntry? = translatorGroups.flatMap { it.entries }.firstOrNull { it.checked }

    fun languageChipLabel(): String = "Japanese → ${targetLanguage.promptName}"

    fun translationChipLabel(): String {
        checkedTranslatorEntry()?.let { return it.name }
        val options = deliverables[ModelType.LLM].orEmpty()
        val selected = options.firstOrNull { it.id == translationSelectedId }?.name ?: "No model selected"
        return options.firstOrNull { it.id == translationPendingId }?.let { "$selected · ${it.name} when downloaded" } ?: selected
    }
}

/** A pick [translatorId]'s entry made, waiting on the reader to agree to [confirm]. */
data class TranslatorConfirm(val translatorId: String, val confirm: TranslatorPick.Confirm)
