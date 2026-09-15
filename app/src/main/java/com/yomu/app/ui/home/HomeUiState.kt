package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelType
import com.yomu.app.db.entities.TranslationEntity
import com.yomu.app.service.ModelSlotSelection
import com.yomu.app.service.SlotDeliverable

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
    val deliverables: Map<ModelType, List<SlotDeliverable>> = emptyMap(),
    val selectedIds: Map<ModelType, String> = emptyMap(),
    val pendingIds: Map<ModelType, String> = emptyMap(),
    /** Bytes still to download for the model each slot will hold. */
    val setupDownloadBytes: Long = 0L,
    val deviceTotalMemBytes: Long = 0L,
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

    val everySlotChosen: Boolean
        get() = ModelType.entries.all { it in selectedIds || it in pendingIds }

    fun fits(deliverable: SlotDeliverable): Boolean = ModelSlotSelection.fits(deliverable.id, deviceTotalMemBytes)
}
