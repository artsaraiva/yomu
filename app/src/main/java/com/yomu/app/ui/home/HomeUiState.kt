package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelType
import com.yomu.app.db.entities.TranslationEntity
import com.yomu.app.service.LanguageSwitch
import com.yomu.app.service.ModelManager
import com.yomu.app.service.ModelSlotSelection
import com.yomu.app.service.ServerPick
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.translation.FitBudget
import com.yomu.app.translation.LlmModelCatalog
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
    /** The server models the picker lists, or null while the server isn't set up (ADR-0023). */
    val serverModels: List<String>? = null,
    /** The server model that takes the next page, or null while the phone does. */
    val activeServerModel: String? = null,
    /** The first server pick, waiting on the reader to accept that pages go to their server, or null. */
    val serverNotice: ServerPick.Notice? = null,
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

    /** Why [language] can't be chosen, or null. English only is a phone-model rule, so never while a server model is picked. */
    fun languageUnavailable(language: TargetLanguage): String? = if (activeServerModel != null) null
        else ModelSlotSelection.languageUnavailable(language, deliverables[ModelType.LLM].orEmpty(), ::fits)

    /** Whether [deliverable]'s row is checked: the selected phone model, while no server model takes the next page. */
    fun checked(deliverable: SlotDeliverable): Boolean = activeServerModel == null && deliverable.id == translationSelectedId

    /** Whether the server model [name]'s row is checked: it takes the next page. */
    fun checked(name: String): Boolean = name == activeServerModel

    fun languageChipLabel(): String = "Japanese → ${targetLanguage.promptName}"

    fun translationChipLabel(): String {
        activeServerModel?.let { return it }
        val options = deliverables[ModelType.LLM].orEmpty()
        val selected = options.firstOrNull { it.id == translationSelectedId }?.name ?: "No model selected"
        return options.firstOrNull { it.id == translationPendingId }?.let { "$selected · ${it.name} when downloaded" } ?: selected
    }
}
