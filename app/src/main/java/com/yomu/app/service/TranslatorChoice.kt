package com.yomu.app.service

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.app.translation.TranslationModelSelection
import com.yomu.app.translation.Translator
import com.yomu.app.translation.TranslatorHomeAction
import com.yomu.app.translation.TranslatorPick
import com.yomu.app.translation.TranslatorPickerEntry
import com.yomu.app.translation.TranslatorSettingsRow
import javax.inject.Inject
import javax.inject.Singleton

/** A translator's rows in the model picker, shown under its [name]. */
data class PickerGroup(val translatorId: String, val name: String, val entries: List<TranslatorPickerEntry>)

/** The translator that takes a page, by id, and the model it asks the server for while it is the network translator. */
data class PageTranslator(val translator: String, val serverModel: String?)

/**
 * Which translator the model picker has chosen: a model on the phone, or an entry another registered
 * translator offers, such as a model on the reader's server (ADR-0023, #534). A selected phone model stays selected
 * meanwhile and translates when that translator can't run, but none is required while it can (#536).
 */
@Singleton
class TranslatorChoice @Inject constructor(
    private val translation: TranslationModelSelection,
    private val server: ServerSettingsStore,
    private val slots: ModelSlotSelection
) {

    /** The groups the picker lists after the phone's: each registered translator that offers entries, in registration order. */
    fun pickerGroups(): List<PickerGroup> {
        val active = translation.activeTranslator().id
        return translation.translators().mapNotNull { translator ->
            translator.pickerEntries(translator.id == active).takeIf { it.isNotEmpty() }
                ?.let { PickerGroup(translator.id, translator.displayName, it) }
        }
    }

    /** The one action Home shows: the first a registered translator offers, in the picker's order, or null for none. */
    fun homeAction(): TranslatorHomeAction? = translation.translators().firstNotNullOfOrNull { it.homeAction(translation.isChosen(it)) }

    /** The rows registered translators add to Settings, in registration order. */
    fun settingsRows(): List<TranslatorSettingsRow> = translation.translators().mapNotNull { it.settingsRow }

    /**
     * Runs [pick], an entry's pick or a confirmation's accept, for the translator [translatorId]. On
     * [TranslatorPick.Select] that translator takes the next page; any other result changed nothing here.
     */
    fun pick(translatorId: String, pick: () -> TranslatorPick): TranslatorPick {
        val translator = translation.translator(translatorId) ?: return TranslatorPick.Refused
        val result = pick()
        if (result == TranslatorPick.Select) translation.selectTranslator(translator)
        return result
    }

    /**
     * The reader turned to the phone's side of the Translator page (#548): its selected model takes the next page.
     * False, changing nothing, when no phone model is selected; then the pages move once one is picked (#536).
     */
    fun selectPhone(): Boolean {
        if (slots.selectedId(ModelType.LLM) == null) return false
        translation.selectOnDevice()
        return true
    }

    /**
     * The reader turned to the server's side of the Translator page (#548): picks the server's stored model as its
     * picker entry would, so the first time asks before any text leaves the phone. Refused until the server is set up.
     */
    fun selectServer(): TranslatorPick {
        val network = translation.translator(Translator.NETWORK_ID)?.takeIf { server.isSetUp() } ?: return TranslatorPick.Refused
        val stored = network.pickerEntries(translation.activeTranslator() === network).firstOrNull { it.name == server.model() }
        return pick(Translator.NETWORK_ID, stored?.pick ?: { TranslatorPick.Refused })
    }

    /** Whether the phone's own model takes the next page, and so is needed (#536). */
    fun phoneTranslates(): Boolean = translation.phoneTranslates()

    /** The server model that takes the next page, or null while the on-device translator does. */
    fun activeServerModel(): String? = forPage().serverModel

    /** The translator that takes the next page, read once so its id and server model can't disagree. */
    fun forPage(): PageTranslator {
        val id = translation.activeTranslator().id
        return PageTranslator(id, server.model().takeIf { id == Translator.NETWORK_ID })
    }

    /**
     * Picks a phone model as [ModelSlotSelection.pick] does; once it is accepted, the on-device translator takes the
     * next page. With no phone model to translate meanwhile (#536), the translator in use keeps the pages until the
     * picked one is downloaded.
     */
    suspend fun pickPhoneModel(id: String, status: ModelStatus?, totalMemBytes: Long): Boolean {
        if (!slots.pick(ModelType.LLM, id, status, totalMemBytes)) return false
        slots.phoneTakesOver = id.takeIf { slots.selectedId(ModelType.LLM) == null }
        if (slots.phoneTakesOver == null) translation.selectOnDevice()
        return true
    }
}
