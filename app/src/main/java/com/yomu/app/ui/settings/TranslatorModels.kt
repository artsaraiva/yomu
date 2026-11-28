package com.yomu.app.ui.settings

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.service.SlotDeliverable
import com.yomu.core.TargetLanguage
import com.yomu.core.toFileSizeString

/** The two sides of the Translator page (#548); the one that takes the next page is the translator (ADR-0023). */
enum class TranslatorSide(val label: String) { Phone("On this phone"), Server("My server") }

/** The side the page opens on: the one whose translator takes the next page. */
internal fun activeSide(translatesOnServer: Boolean): TranslatorSide = if (translatesOnServer) TranslatorSide.Server else TranslatorSide.Phone

/**
 * What [side] says while its translator doesn't have the pages, or null when it does. The reader can look at a side
 * before it is able to translate, so the page never lets the control alone claim who translates.
 */
internal fun sideNote(side: TranslatorSide, phoneTranslates: Boolean, translatesOnServer: Boolean, serverReady: Boolean): String? = when (side) {
    TranslatorSide.Phone -> when {
        phoneTranslates -> null
        translatesOnServer -> "Your server translates the pages until you pick a model here."
        else -> "Another translator has the pages until you pick a model here."
    }
    TranslatorSide.Server -> when {
        translatesOnServer -> null
        serverReady -> "Your server is ready, and pages still translate on this phone."
        else -> "Pages translate on this phone until your server is connected and a model is picked."
    }
}

/**
 * The phone's translation models as the Translator page groups them, each in catalog order: the ones [downloaded],
 * the ones offered [toDownload], the rest of those folded behind one row as [more], and the ones [tooBig] for this
 * phone folded behind another. Every model is in exactly one group, so none is out of reach.
 */
internal data class PhoneModels(
    val downloaded: List<SlotDeliverable>,
    val toDownload: List<SlotDeliverable>,
    val more: List<SlotDeliverable>,
    val tooBig: List<SlotDeliverable>
)

private const val OFFERED = 3

/**
 * Groups [deliverables]. A downloaded model stays downloaded even when it no longer [fits], so it can still be
 * deleted. A model in [busy] (downloading, or waiting to take over) or whose download failed is never folded away.
 */
internal fun phoneModels(
    deliverables: List<SlotDeliverable>,
    statuses: Map<String, ModelStatus>,
    busy: Set<String>,
    fits: (SlotDeliverable) -> Boolean
): PhoneModels {
    val (downloaded, rest) = deliverables.partition { statuses[it.id] == ModelStatus.READY }
    val (fitting, tooBig) = rest.partition(fits)
    val offered = fitting.take(OFFERED).mapTo(mutableSetOf()) { it.id }
    val (toDownload, more) = fitting.partition { it.id in offered || it.id in busy || statuses[it.id] == ModelStatus.ERROR }
    return PhoneModels(downloaded, toDownload, more, tooBig)
}

/** The two tags on a model's card: how fast it is and the languages it writes. */
internal fun modelTags(deliverable: SlotDeliverable): List<String> = listOfNotNull(
    deliverable.speed.label,
    deliverable.multilingual?.let { if (it) "${TargetLanguage.entries.size} languages" else "English only" }
)

internal fun downloadedLine(deliverable: SlotDeliverable, inUse: Boolean, languageBlock: String?): String = listOfNotNull(
    deliverableStatus(ModelStatus.READY, null, inUse, pending = false),
    deliverable.sizeBytes.toFileSizeString(),
    languageBlock
).joinToString(" · ")

/** What a model that isn't downloaded says under its name: its download's state, or why it can't be picked. Null for neither. */
internal fun downloadNote(status: ModelStatus?, progress: Int?, pending: Boolean, languageBlock: String?): String? = listOfNotNull(
    deliverableStatus(status, progress, selected = false, pending).takeIf { progress != null || pending || status == ModelStatus.ERROR },
    languageBlock
).joinToString(" · ").ifEmpty { null }

/** What a model's detail lists: the facts its card leaves out. */
internal fun modelFacts(deliverable: SlotDeliverable, fits: Boolean): List<String> = listOfNotNull(
    deliverable.sizeBytes.toFileSizeString(),
    "Licence: ${deliverable.licence}",
    "Experimental".takeIf { deliverable.experimental },
    "Needs more memory than this phone has".takeUnless { fits }
)

internal fun moreLabel(count: Int): String = "Show $count more"

internal fun tooBigLabel(count: Int): String =
    if (count == 1) "1 model needs more memory than this phone has" else "$count models need more memory than this phone has"
