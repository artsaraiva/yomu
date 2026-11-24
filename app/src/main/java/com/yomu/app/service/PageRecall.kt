package com.yomu.app.service

import com.yomu.app.translation.Translator
import com.yomu.core.GenerationParams
import com.yomu.core.GlossaryEntry
import com.yomu.core.RuntimeLimits
import com.yomu.core.TargetLanguage
import com.yomu.core.TranslationOutcome
import com.yomu.pipeline.PipelineResult

/**
 * Every setting that shapes a translated page: a change to any of them makes the next capture a fresh translation.
 * A page report records them all, so none may name the server's address (ADR-0023).
 */
data class PageSettings(
    /** The translator that took the page: on the phone, or the network translator. */
    val translator: String = Translator.ON_DEVICE_ID,
    /** The model the network translator asked for, or null for a page translated on the phone. */
    val serverModel: String? = null,
    val translationModel: String?,
    val detectionModel: String?,
    val ocrModel: String?,
    val generation: GenerationParams,
    val runtime: RuntimeLimits,
    val detectionThreshold: Float,
    val fontScale: Float,
    val targetLanguage: TargetLanguage,
    val glossary: List<GlossaryEntry>
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

    /** The reader's switch (#351): while off nothing is recalled or remembered, and turning it off forgets every page. */
    var enabled = true
        @Synchronized set(value) {
            field = value
            if (!value) {
                pages.clear()
                timesTurnedOff++
            }
        }

    /** How often recall has been turned off: a page begun at a lower count began before an off. */
    var timesTurnedOff = 0
        @Synchronized get
        private set

    /** Returns a matching page and marks it most recently used, or null if recall is off or no fingerprint and settings match. */
    @Synchronized
    fun recall(fingerprint: PageFingerprint, settings: PageSettings): PipelineResult? {
        if (!enabled) return null
        return pages.keys.firstOrNull { it.settings == settings && it.fingerprint.matches(fingerprint) }?.let(pages::get)
    }

    /**
     * Keeps a page the model finished with at least one bubble answered. A timed-out or wholly
     * unanswered page is left out, so capturing it again is how the reader retries it; a bubble the
     * model could not answer on a finished page (a sound effect, app chrome) fails the same way again.
     * A page begun before recall was last turned off is left out too, even when recall is on again.
     */
    @Synchronized
    fun remember(fingerprint: PageFingerprint, settings: PageSettings, page: PipelineResult, begunAt: Int = timesTurnedOff) {
        val translation = page.translationResult
        if (!enabled || begunAt != timesTurnedOff || translation.outcome != TranslationOutcome.SUCCESS || translation.translations.none { it.answered }) return
        pages.keys.removeAll { it.settings == settings && it.fingerprint.matches(fingerprint) }
        pages[Key(fingerprint, settings)] = page
    }

    /** Forgets every remembered page when the owning overlay session ends. */
    @Synchronized
    fun clear() = pages.clear()

    private companion object {
        const val CAPACITY = 30
    }
}
