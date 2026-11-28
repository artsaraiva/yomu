package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.GenerationParams
import com.yomu.core.TranslationSlot
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.ml.NetworkTranslationSlot
import com.yomu.ml.NetworkTranslatorConfig
import okhttp3.OkHttpClient

/**
 * One way to fill the translation slot (#478). Each module registers its own through Hilt's
 * `@IntoSet`, and [TranslationModelSelection] hands out the one the reader chose.
 *
 * The slot is built on first use, so a translator the reader never picks costs nothing. Each translator
 * explains its own error codes to the reader (#509), so the pipeline need not know them, and supplies its own
 * picker entries, Settings row (#534) and Home action, so the screens that show them need not know it. The screens those entries
 * open come from the translator's module too, as `TranslatorScreen`s. It may also leave a note on a page it translated (#557).
 */
class Translator(
    val id: String,
    val displayName: String,
    private val canRun: () -> Boolean,
    private val slot: Lazy<TranslationSlot>,
    private val readerMessages: Map<String, ReaderMessage> = emptyMap(),
    private val pickerEntries: (active: Boolean) -> List<TranslatorPickerEntry> = { emptyList() },
    /** The row this translator adds to Settings, or null for none. */
    val settingsRow: TranslatorSettingsRow? = null,
    private val homeAction: (chosen: Boolean) -> TranslatorHomeAction? = { null },
    private val pageNote: () -> String? = { null }
) {
    /** What to tell the reader about a page that ended with [errorCode], or null when this translator has nothing to say. */
    fun readerMessage(errorCode: String): ReaderMessage? = readerMessages[errorCode]

    /**
     * What this translator wants the reader to know after a page it translated, or null for nothing (#557). Asked once
     * for each finished page that has no failure, so a translator with something to say once returns it once.
     */
    fun pageNote(): String? = pageNote.invoke()

    /** The rows this translator adds to the model picker; [active] says whether it takes the next page. Empty for none. */
    fun pickerEntries(active: Boolean): List<TranslatorPickerEntry> = pickerEntries.invoke(active)

    /** The action this translator puts on Home, or null for none; [chosen] says whether the reader chose it, even if it can't run. */
    fun homeAction(chosen: Boolean): TranslatorHomeAction? = homeAction.invoke(chosen)

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

        const val NETWORK_ID = "network"

        /**
         * The network translator (ADR-0023): it can run once [store] is set up. Its slot reads the server's
         * settings and the reader's sampler fields in [prefs] again for every page.
         */
        fun network(store: ServerSettingsStore, prefs: SharedPreferences, client: OkHttpClient): Translator =
            Translator(
                NETWORK_ID, "Your server", store::isSetUp,
                slot = lazy {
                    NetworkTranslationSlot(client, config = {
                        store.translatorConfig()?.copy(generation = readerGeneration(prefs)) ?: NOT_SET_UP
                    })
                },
                readerMessages = NetworkTranslatorEntries.readerMessages,
                pickerEntries = { active -> NetworkTranslatorEntries.pickerEntries(store, active) },
                homeAction = { chosen -> NetworkTranslatorEntries.homeAction(store, chosen) }
            )

        // No address, so the slot fails the page before any request: canRun said yes, then the address changed.
        private val NOT_SET_UP = NetworkTranslatorConfig(baseUrl = "", apiKey = "", model = "")

        // The same profile the on-device slot gets: the reader's saved fields over the selected model's defaults.
        private fun readerGeneration(prefs: SharedPreferences): GenerationParams =
            GenerationProfileStore(prefs).load(LlmModelCatalog.selectedOrDefault(prefs.storedLlmModelId()).generationDefaults).params
    }
}
