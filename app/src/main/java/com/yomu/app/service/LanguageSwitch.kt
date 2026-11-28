package com.yomu.app.service

import com.yomu.core.TargetLanguage

/** What choosing a target language does (#338). */
sealed interface LanguageSwitch {
    /** The language is set. */
    data object Done : LanguageSwitch

    /**
     * [current] writes English only, so the language stays as it is unless the reader switches to [model].
     * [downloadBytes] is what that switch downloads, 0 when [model] is already downloaded.
     */
    data class Offer(val language: TargetLanguage, val current: SlotDeliverable, val model: SlotDeliverable, val downloadBytes: Long) :
        LanguageSwitch
}
