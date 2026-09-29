package com.yomu.app.service

import com.google.gson.GsonBuilder
import com.yomu.core.TranslationOutcome
import com.yomu.pipeline.PipelineResult
import com.yomu.pipeline.TranslationPipeline

/** The build and the phone a reported page ran on. */
data class ReportOrigin(val appVersion: String, val device: String)

private class ReportedBubble(
    val id: Int,
    val bounds: FloatArray,
    val ocrText: String?,
    val translation: String?,
    val answered: Boolean
)

private class ReportedTimings(
    val totalMs: Long,
    val timeToFirstBubbleMs: Long?,
    /** The slot's own clock for the model call, without the typesetting the translation stage also does. */
    val translationCallMs: Long,
    val stagesMs: Map<TranslationPipeline.Stage, Long>
)

private class ReportedPage(
    val recalled: Boolean,
    val outcome: TranslationOutcome,
    val errorCode: String?,
    val batchOverflowFallback: Boolean,
    val pageWidth: Int,
    val pageHeight: Int,
    val settings: PageSettings,
    val timings: ReportedTimings,
    val appVersion: String,
    val device: String,
    val bubbles: List<ReportedBubble>
)

private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create()

/**
 * The files of a page report by name, in zip order: the capture, the page as JSON and the model's raw
 * reply. Every detected bubble is listed, so one OCR never read shows up as a detection with no text.
 * A recalled page is described by the translation it recalled.
 */
fun pageReportFiles(
    capturePng: ByteArray,
    page: PipelineResult,
    settings: PageSettings,
    recalled: Boolean,
    origin: ReportOrigin
): Map<String, ByteArray> {
    val translation = page.translationResult
    val translated = translation.translations.associateBy { it.bubbleId }
    val reported = ReportedPage(
        recalled = recalled,
        outcome = translation.outcome,
        errorCode = translation.errorCode,
        batchOverflowFallback = translation.batchOverflowFallback,
        pageWidth = page.pageWidth,
        pageHeight = page.pageHeight,
        settings = settings,
        timings = ReportedTimings(page.totalTimeMs, page.timeToFirstBubbleMs, translation.translationTimeMs, page.stageTimesMs),
        appVersion = origin.appVersion,
        device = origin.device,
        bubbles = page.bubbleBounds.map { (id, bounds) ->
            val bubble = translated[id]
            ReportedBubble(
                id = id,
                bounds = bounds,
                ocrText = bubble?.originalText,
                translation = bubble?.takeIf { it.answered }?.translatedText,
                answered = bubble?.answered == true
            )
        }
    )
    return linkedMapOf(
        "capture.png" to capturePng,
        "page.json" to gson.toJson(reported).toByteArray(),
        "reply.txt" to translation.rawResponse.toByteArray()
    )
}
