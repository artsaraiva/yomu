package com.yomu.app.service

import android.content.SharedPreferences
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelType
import com.yomu.core.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The curated model selected for each slot. An unset, unreadable or unknown stored id falls back to
 * the slot's default, so a registry change never leaves a slot empty.
 */
@Singleton
class ReadingModelSelection @Inject constructor(private val sharedPreferences: SharedPreferences) {

    fun selected(type: ModelType): ModelEntity {
        val stored = runCatching { sharedPreferences.getString(prefKey(type), null) }.getOrNull()
        return ModelManager.REGISTRY.firstOrNull { it.type == type && it.id == stored }
            ?: ModelManager.REGISTRY.single { it.id == ModelManager.SLOT_DEFAULTS.getValue(type) }
    }

    fun select(model: ModelEntity) {
        sharedPreferences.edit().putString(prefKey(model.type), model.id).apply()
    }

    private fun prefKey(type: ModelType): String = when (type) {
        ModelType.DETECTION -> Constants.PREF_DETECTION_MODEL
        ModelType.OCR -> Constants.PREF_OCR_MODEL
        ModelType.LLM -> Constants.PREF_LLM_MODEL
    }
}
