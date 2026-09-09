package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelStatus
import com.yomu.core.Constants

enum class Readiness { Ready, NeedsModels, NeedsPermission, NeedsBoth }

enum class ReadingStatus { Off, On, NotReady }

fun resolveReadingStatus(isReady: Boolean, isServiceRunning: Boolean): ReadingStatus = when {
    !isReady -> ReadingStatus.NotReady
    isServiceRunning -> ReadingStatus.On
    else -> ReadingStatus.Off
}

fun resolveReadiness(models: Map<String, ModelStatus>, hasOverlayPermission: Boolean): Readiness {
    val modelsReady = models[Constants.BUBBLE_DETECTION_MODEL_ID] == ModelStatus.READY &&
        models[Constants.MANGA_OCR_MODEL_ID] == ModelStatus.READY
    return when {
        modelsReady && hasOverlayPermission -> Readiness.Ready
        modelsReady -> Readiness.NeedsPermission
        hasOverlayPermission -> Readiness.NeedsModels
        else -> Readiness.NeedsBoth
    }
}
