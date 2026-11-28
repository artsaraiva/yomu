package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelStatus

enum class Readiness {
    Ready, NeedsModels, NeedsPermission, NeedsBoth;

    /** Every model the next page needs is downloaded. */
    val modelsReady: Boolean get() = this == Ready || this == NeedsPermission

    /** Yomu may draw over other apps. */
    val hasOverlayPermission: Boolean get() = this == Ready || this == NeedsModels
}

enum class ReadingStatus { Off, On, NotReady }

fun resolveReadingStatus(isReady: Boolean, isServiceRunning: Boolean): ReadingStatus = when {
    !isReady -> ReadingStatus.NotReady
    isServiceRunning -> ReadingStatus.On
    else -> ReadingStatus.Off
}

/**
 * Detection and OCR always run on the phone. The phone's translation model is needed only while [phoneTranslates],
 * since a reader whose pages go to another translator, such as their own server, never uses it (#536).
 */
fun resolveReadiness(
    models: Map<String, ModelStatus>,
    detectionModelId: String?,
    ocrModelId: String?,
    translationModelId: String?,
    hasOverlayPermission: Boolean,
    phoneTranslates: Boolean
): Readiness {
    val required = listOf(detectionModelId, ocrModelId) + listOf(translationModelId).filter { phoneTranslates }
    val modelsReady = required.all { models[it] == ModelStatus.READY }
    return when {
        modelsReady && hasOverlayPermission -> Readiness.Ready
        modelsReady -> Readiness.NeedsPermission
        hasOverlayPermission -> Readiness.NeedsModels
        else -> Readiness.NeedsBoth
    }
}
