package com.yomu.app.ui.models

import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelType
import com.yomu.core.Constants

enum class ModelCapability {
    BUBBLE_DETECTION,
    OCR,
    TRANSLATION_ENGINE
}

fun ModelEntity.capability(): ModelCapability {
    return when {
        id == Constants.BUBBLE_DETECTION_MODEL_ID -> ModelCapability.BUBBLE_DETECTION
        type == ModelType.VISION -> ModelCapability.OCR
        type == ModelType.LLM || type == ModelType.TRANSLATION -> ModelCapability.TRANSLATION_ENGINE
        else -> ModelCapability.TRANSLATION_ENGINE
    }
}
