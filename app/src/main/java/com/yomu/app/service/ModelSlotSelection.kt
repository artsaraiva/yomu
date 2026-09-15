package com.yomu.app.service

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.app.translation.TranslationModelSelection
import com.yomu.core.Constants
import javax.inject.Inject
import javax.inject.Singleton

/** One curated model a slot can hold, as the slot picker lists it. */
data class SlotDeliverable(val id: String, val name: String, val sizeBytes: Long, val licence: String)

/**
 * Which curated model fills each slot, and which one is waiting to take over. A model picked before it is READY is
 * held as pending, so the stored selection, and the model already working, stay put until its download completes.
 */
@Singleton
class ModelSlotSelection @Inject constructor(
    private val translation: TranslationModelSelection,
    private val reading: ReadingModelSelection
) {
    private val pending = mutableMapOf<ModelType, String>()

    fun deliverables(type: ModelType): List<SlotDeliverable> = when (type) {
        ModelType.LLM -> LlmModelCatalog.ALL.map { SlotDeliverable(it.id, it.displayName, it.sizeBytes, it.licence) }
        else -> ModelManager.REGISTRY.filter { it.type == type }.map {
            SlotDeliverable(
                it.id,
                it.name,
                it.fileSize + ModelManager.additionalFiles(it.id).sumOf { file -> file.size },
                READING_LICENCES.getValue(it.id)
            )
        }
    }

    fun selectedId(type: ModelType): String =
        if (type == ModelType.LLM) translation.currentLlmModel().id else reading.selected(type).id

    fun pendingId(type: ModelType): String? = pending[type]

    /** Refuses a model outside [type]'s deliverables or the fit budget; otherwise commits it if READY, else holds it pending. */
    suspend fun pick(type: ModelType, id: String, status: ModelStatus?, totalMemBytes: Long): Boolean {
        if (deliverables(type).none { it.id == id } || !fits(id, totalMemBytes)) return false
        if (status == ModelStatus.READY) {
            pending.remove(type)
            commit(type, id)
        } else {
            pending[type] = id
        }
        return true
    }

    suspend fun commitPending(statuses: Map<String, ModelStatus>) {
        for ((type, id) in pending.toMap()) {
            if (statuses[id] != ModelStatus.READY) continue
            pending.remove(type)
            commit(type, id)
        }
    }

    fun dropPending(id: String) {
        pending.values.remove(id)
    }

    private suspend fun commit(type: ModelType, id: String) {
        if (type == ModelType.LLM) {
            LlmModelCatalog.fromId(id)?.let { translation.selectLlmModel(it) }
        } else {
            ModelManager.REGISTRY.firstOrNull { it.id == id }?.let(reading::select)
        }
    }

    companion object {
        // ADR-0007: the detector's card says Apache-2.0, but it is a fine-tune of AGPL-3.0 Ultralytics YOLO26.
        private val READING_LICENCES = mapOf(
            Constants.BUBBLE_DETECTION_MODEL_ID to "AGPL-3.0 (YOLO26)",
            Constants.MANGA_OCR_MODEL_ID to "Apache-2.0 (manga-ocr)"
        )

        /** Whether [id] fits a device with [totalMemBytes] of RAM. Only translation models are budgeted; 0 means unknown. */
        fun fits(id: String, totalMemBytes: Long): Boolean {
            val option = LlmModelCatalog.fromId(id) ?: return true
            return totalMemBytes <= 0L || LlmModelCatalog.canRunOnDevice(option, totalMemBytes)
        }
    }
}
