package com.yomu.app.service

import com.yomu.core.GenerationParams
import com.yomu.core.RuntimeLimits
import com.yomu.core.TranslationOutcome
import com.yomu.pipeline.PipelineResult

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
        /** Evicts the least recently used page when insertion exceeds the thirty-page capacity. */
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, PipelineResult>): Boolean = size > CAPACITY
    }

    /** Returns a matching page and marks it most recently used, or null if no fingerprint and settings match. */
    @Synchronized
    fun recall(fingerprint: PageFingerprint, settings: PageSettings): PipelineResult? =
        pages.keys.firstOrNull { it.settings == settings && it.fingerprint.matches(fingerprint) }?.let(pages::get)

    /**
     * Keeps a page the model finished with at least one bubble answered. A timed-out or wholly
     * unanswered page is left out, so capturing it again is how the reader retries it; a bubble the
     * model could not answer on a finished page (a sound effect, app chrome) fails the same way again.
     */
    @Synchronized
    fun remember(fingerprint: PageFingerprint, settings: PageSettings, page: PipelineResult) {
        val translation = page.translationResult
        if (translation.outcome != TranslationOutcome.SUCCESS || translation.translations.none { it.answered }) return
        pages[Key(fingerprint, settings)] = page
    }

    /** Forgets every remembered page when the owning overlay session ends. */
    @Synchronized
    fun clear() = pages.clear()

    private companion object {
        const val CAPACITY = 30
    }
}
