package com.yomu.app.service

import com.yomu.core.GenerationParams
import com.yomu.core.RuntimeLimits
import com.yomu.core.TranslationOutcome
import com.yomu.pipeline.PipelineResult
import com.yomu.pipeline.translation.untranslatedNotice

/** Every setting that shapes a translated page: a change to any of them makes the next capture a fresh translation. */
data class PageSettings(
    val translationModel: String?,
    val detectionModel: String?,
    val ocrModel: String?,
    val generation: GenerationParams,
    val runtime: RuntimeLimits,
    val detectionThreshold: Float,
    val fontScale: Float
)

/**
 * Pages already translated in this overlay session, keyed on the captured frame and the settings that
 * made them, never on source lines (ADR-0002). Memory only: the overlay clears it when it stops.
 */
class PageRecall {

    private class Key(val fingerprint: PageFingerprint, val settings: PageSettings)

    private val pages = object : LinkedHashMap<Key, PipelineResult>(CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, PipelineResult>): Boolean = size > CAPACITY
    }

    @Synchronized
    fun recall(fingerprint: PageFingerprint, settings: PageSettings): PipelineResult? =
        pages.keys.firstOrNull { it.settings == settings && it.fingerprint.matches(fingerprint) }?.let(pages::get)

    /** Keeps only a page the model answered in full: capturing an incomplete one again is how the reader retries it. */
    @Synchronized
    fun remember(fingerprint: PageFingerprint, settings: PageSettings, page: PipelineResult) {
        val translation = page.translationResult
        if (translation.outcome != TranslationOutcome.SUCCESS || translation.untranslatedNotice() != null) return
        pages[Key(fingerprint, settings)] = page
    }

    @Synchronized
    fun clear() = pages.clear()

    private companion object {
        const val CAPACITY = 30
    }
}
