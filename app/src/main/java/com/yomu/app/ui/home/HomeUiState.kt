package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelType
import com.yomu.app.db.entities.TranslationEntity
import com.yomu.app.service.ModelSlotSelection
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.translation.FitBudget
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.core.RuntimeLimits

data class HomeUiState(
    val isServiceRunning: Boolean = false,
    val readiness: Readiness = Readiness.NeedsBoth,
    val models: List<ModelEntity> = emptyList(),
    val modelsLoading: Boolean = true,
    val setupComplete: Boolean = false,
    val setupVisible: Boolean = false,
    val setupStarted: Boolean = false,
    val setupDownloadsReady: Boolean = false,
    val downloading: String? = null,
    val downloadProgress: Int = 0,
    val setupError: Boolean = false,
    /** Name of the chosen model setup refused to download because it is outside the fit budget, or null. */
    val setupBlockedBy: String? = null,
    val deliverables: Map<ModelType, List<SlotDeliverable>> = emptyMap(),
    val chosenIds: Map<ModelType, String> = emptyMap(),
    val translationSelectedId: String? = null,
    val translationPendingId: String? = null,
    val setupDownloadBytes: Long = 0L,
    val fitBudget: FitBudget = FitBudget(0L, LlmModelCatalog.DEFAULT_FIT_BUDGET_PERCENT, RuntimeLimits.DEFAULT_CONTEXT_TOKENS),
    val onWifi: Boolean = true,
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

    fun translationChipLabel(): String {
        val options = deliverables[ModelType.LLM].orEmpty()
        val selected = options.firstOrNull { it.id == translationSelectedId }?.name ?: "No model selected"
        return options.firstOrNull { it.id == translationPendingId }?.let { "$selected · ${it.name} when downloaded" } ?: selected
    }
}
