package com.yomu.app.service

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.app.translation.TranslationModelSelection
import com.yomu.app.translation.Translator
import javax.inject.Inject
import javax.inject.Singleton

/** What picking a server model does (ADR-0023). */
sealed interface ServerPick {
    /** The model is stored and the network translator takes the next page. */
    data object Done : ServerPick

    /** The server isn't set up, or doesn't list the model: nothing changed. */
    data object Refused : ServerPick

    /**
     * The first server pick: nothing changes until the reader accepts that each page's text is sent to
     * [address], and then [model] is picked ([TranslatorChoice.acceptNotice]).
     */
    data class Notice(val model: String, val address: String) : ServerPick
}

/** The translator that takes a page, by id, and the model it asks the server for while it is the network translator. */
data class PageTranslator(val translator: String, val serverModel: String?)

/**
 * Which translator the model picker has chosen: a model on the phone, or one on the reader's server
 * (ADR-0023). The phone model stays selected while a server model is picked, since it is still required
 * and is what translates when the server isn't set up.
 */
@Singleton
class TranslatorChoice @Inject constructor(
    private val translation: TranslationModelSelection,
    private val server: ServerSettingsStore,
    private val slots: ModelSlotSelection
) {

    /**
     * The server models the picker offers: the names the last Connect listed, or the typed name when the
     * server keeps no list. Null until the server is set up. Read from the settings, so nothing is sent.
     */
    fun serverModels(): List<String>? {
        if (!server.isSetUp()) return null
        return server.serverModels().orEmpty().ifEmpty { listOf(server.model()) }
    }

    /** The server model that takes the next page, or null while the on-device translator does. */
    fun activeServerModel(): String? = forPage().serverModel

    /** The translator that takes the next page, read once so its id and server model can't disagree. */
    fun forPage(): PageTranslator {
        val id = translation.activeTranslator().id
        return PageTranslator(id, server.model().takeIf { id == Translator.NETWORK_ID })
    }

    /** Picks [model] on the server, unless it is the first server pick: then the reader is asked first. */
    fun pickServerModel(model: String): ServerPick {
        if (model !in serverModels().orEmpty()) return ServerPick.Refused
        if (!server.noticeAccepted()) return ServerPick.Notice(model, server.address())
        val network = translation.translator(Translator.NETWORK_ID) ?: return ServerPick.Refused
        server.saveModel(model)
        translation.selectTranslator(network)
        return ServerPick.Done
    }

    /**
     * The reader accepted [notice]: it is not asked again, and its model is picked. Refused when the
     * address changed since, as the reader agreed to send pages to the old one.
     */
    fun acceptNotice(notice: ServerPick.Notice): Boolean {
        if (notice.address != server.address()) return false
        server.acceptNotice()
        return pickServerModel(notice.model) == ServerPick.Done
    }

    /** Picks a phone model as [ModelSlotSelection.pick] does; once it is accepted, the on-device translator takes the next page. */
    suspend fun pickPhoneModel(id: String, status: ModelStatus?, totalMemBytes: Long): Boolean {
        if (!slots.pick(ModelType.LLM, id, status, totalMemBytes)) return false
        translation.translator(Translator.ON_DEVICE_ID)?.let(translation::selectTranslator)
        return true
    }
}
