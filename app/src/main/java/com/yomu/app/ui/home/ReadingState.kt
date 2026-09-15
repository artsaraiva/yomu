package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelStatus

enum class Readiness { Ready, NeedsModels, NeedsPermission, NeedsBoth }

enum class ReadingStatus { Off, On, NotReady }

fun resolveReadingStatus(isReady: Boolean, isServiceRunning: Boolean): ReadingStatus = when {
    !isReady -> ReadingStatus.NotReady
    isServiceRunning -> ReadingStatus.On
    else -> ReadingStatus.Off
}

fun resolveReadiness(
    models: Map<String, ModelStatus>,
    detectionModelId: String,
    ocrModelId: String,
    translationModelId: String,
    hasOverlayPermission: Boolean
): Readiness {
    val modelsReady = listOf(detectionModelId, ocrModelId, translationModelId).all { models[it] == ModelStatus.READY }
    return when {
        modelsReady && hasOverlayPermission -> Readiness.Ready
        modelsReady -> Readiness.NeedsPermission
        hasOverlayPermission -> Readiness.NeedsModels
        else -> Readiness.NeedsBoth
    }
}
