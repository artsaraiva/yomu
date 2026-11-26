package com.yomu.app.translation

import com.yomu.core.ERROR_SERVER_FAILED
import com.yomu.core.ERROR_SERVER_HTTPS_REQUIRED
import com.yomu.core.ERROR_SERVER_KEY_REJECTED
import com.yomu.core.ERROR_SERVER_MODEL_NOT_FOUND
import com.yomu.core.ERROR_SERVER_TIMEOUT
import com.yomu.core.ERROR_SERVER_UNREACHABLE

/** The network translator's picker group, Settings row and failure messages (ADR-0023, #534), all read from [ServerSettingsStore]. */
internal object NetworkTranslatorEntries {
    val PROVIDERS = AppDestination("settings/providers")

    private const val PROVIDERS_SUMMARY = "A server you run, or a hosted API you have a key for"

    /**
     * The server models from the last Connect, or the typed name when the server keeps no list; the stored model is
     * checked while the network translator is [active]. Until the server is set up, its one entry opens Providers.
     */
    fun pickerEntries(store: ServerSettingsStore, active: Boolean): List<TranslatorPickerEntry> {
        if (!store.isSetUp()) {
            return listOf(TranslatorPickerEntry("Set up your server", PROVIDERS_SUMMARY, checked = false) { TranslatorPick.Open(PROVIDERS) })
        }
        return models(store).map { model -> TranslatorPickerEntry(model, null, active && model == store.model()) { pick(store, model) } }
    }

    fun settingsRow(store: ServerSettingsStore): TranslatorSettingsRow = TranslatorSettingsRow(
        "Providers",
        { if (store.isSetUp()) "Connected to ${store.address()}" else "$PROVIDERS_SUMMARY." },
        PROVIDERS
    )

    // A server failure is never retried on the phone (ADR-0023), so each names its cause and where to pick a phone model.
    val readerMessages: Map<String, ReaderMessage> = mapOf(
        ERROR_SERVER_UNREACHABLE to "Can't reach your server",
        ERROR_SERVER_KEY_REJECTED to "Your server rejected the key",
        ERROR_SERVER_MODEL_NOT_FOUND to "Your server doesn't have this model",
        ERROR_SERVER_TIMEOUT to "Your server took too long",
        ERROR_SERVER_HTTPS_REQUIRED to "Your server's address needs https://",
        ERROR_SERVER_FAILED to "Your server couldn't translate the page"
    ).mapValues { (_, cause) ->
        ReaderMessage("$cause — or pick a phone model in the model picker on Home", ReaderAction("Tap to open Providers", PROVIDERS))
    }

    private fun models(store: ServerSettingsStore): List<String> = store.serverModels().orEmpty().ifEmpty { listOf(store.model()) }

    /** Stores [model], unless it is the first server pick: then the reader is asked first, before any page's text leaves the phone. */
    private fun pick(store: ServerSettingsStore, model: String): TranslatorPick {
        if (!store.isSetUp() || model !in models(store)) return TranslatorPick.Refused
        if (!store.noticeAccepted()) return notice(store, model, store.address())
        store.saveModel(model)
        return TranslatorPick.Select
    }

    // Refused once the address changed, as the reader agreed to send pages to the old one.
    private fun notice(store: ServerSettingsStore, model: String, address: String) = TranslatorPick.Confirm(
        "Translate on your server?",
        "The text of each page you translate will be sent to $address. The page itself stays on your phone, " +
            "and you can pick a phone model here again at any time.",
        "Send to my server"
    ) {
        if (address != store.address()) {
            TranslatorPick.Refused
        } else {
            store.acceptNotice()
            pick(store, model)
        }
    }
}
