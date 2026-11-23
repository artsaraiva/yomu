package com.yomu.app.translation

import com.yomu.core.TranslationSlot
import com.yomu.ml.LlamaTranslationBridge

/**
 * One way to fill the translation slot (#478). Each module registers its own through Hilt's
 * `@IntoSet`, and [TranslationModelSelection] hands out the one the reader chose.
 *
 * The slot is built on first use, so a translator the reader never picks costs nothing.
 */
class Translator(
    val id: String,
    val displayName: String,
    private val canRun: () -> Boolean,
    private val slot: Lazy<TranslationSlot>
) {
    /** Whether this translator can take a page now; checked before every page, so it must be cheap. */
    fun canRun(): Boolean = canRun.invoke()

    fun slot(): TranslationSlot = slot.value

    /** Closes the slot if it was ever built; one never used is not built just to be closed. */
    fun close() {
        if (slot.isInitialized()) slot.value.close()
    }

    companion object {
        const val ON_DEVICE_ID = "on-device"

        /** The llama.cpp slot, which is also the fallback when the reader's choice can't run. */
        fun onDevice(llamaSlot: LlamaTranslationBridge): Translator =
            Translator(ON_DEVICE_ID, "On this phone", { true }, lazyOf(llamaSlot))
    }
}
