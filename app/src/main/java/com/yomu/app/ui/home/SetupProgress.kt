package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.PickerGroup
import com.yomu.app.service.SlotDeliverable
import com.yomu.core.toFileSizeString

/** Where one chosen model stands in setup's download (#464). */
sealed interface SetupModelState {
    data object Missing : SetupModelState
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : SetupModelState
    data object Done : SetupModelState
    data object Failed : SetupModelState
    data object TooBig : SetupModelState
}

/** One model in the reading kit: the one setup downloads for its slot, and how far that download is. */
data class SetupModelRow(val type: ModelType, val deliverable: SlotDeliverable, val state: SetupModelState) {
    val slot: String get() = SLOT_NAMES.getValue(type)

    /** The line under the row's name: its size, or what went wrong. Only the translator names its model, the one a reader chooses. */
    fun detail(): String {
        val status = when (state) {
            SetupModelState.Failed -> "Failed"
            SetupModelState.TooBig -> "Too big for this phone"
            else -> deliverable.sizeBytes.toFileSizeString()
        }
        return listOfNotNull(deliverable.name.takeIf { type == ModelType.LLM }, status).joinToString(" · ")
    }

    /** The row's state as TalkBack says it. */
    fun spokenState(): String = when (state) {
        SetupModelState.Missing -> "Not downloaded"
        is SetupModelState.Downloading -> "Downloading"
        SetupModelState.Done -> "Downloaded"
        SetupModelState.Failed -> "Failed"
        SetupModelState.TooBig -> "Too big for this phone"
    }

    private companion object {
        val SLOT_NAMES = mapOf(ModelType.DETECTION to "Bubble finder", ModelType.OCR to "Japanese reader", ModelType.LLM to "Translator")
    }
}

/** The reading kit (#547): every model setup downloads, shown as one download. */
data class SetupKit(val rows: List<SetupModelRow>, val running: Boolean, val failed: Boolean) {
    val totalBytes: Long get() = rows.sumOf { it.deliverable.sizeBytes }

    /** A finished model counts whole and the running one by the bytes that have arrived. */
    val downloadedBytes: Long
        get() = rows.sumOf {
            when (val state = it.state) {
                SetupModelState.Done -> it.deliverable.sizeBytes
                is SetupModelState.Downloading -> state.bytesDownloaded
                else -> 0L
            }
        }

    val fraction: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f

    val downloaded: Boolean get() = rows.isNotEmpty() && rows.all { it.state == SetupModelState.Done }

    /** The chosen model setup refuses to download, or null. */
    val tooBig: SetupModelRow? get() = rows.firstOrNull { it.state == SetupModelState.TooBig }

    fun progressText(): String = when {
        downloaded -> "Downloaded · ${totalBytes.toFileSizeString()}"
        running -> "${downloadedBytes.toFileSizeString()} of ${totalBytes.toFileSizeString()}"
        else -> totalBytes.toFileSizeString()
    }

    /** What TalkBack announces. It leaves out the bytes, so the download is announced when it starts and ends, not on every update. */
    fun announcement(): String = "Reading kit, " + when {
        tooBig != null -> "a model is too big for this phone"
        failed -> "download failed"
        downloaded -> "downloaded"
        running -> "downloading"
        else -> "not downloaded"
    }
}

fun HomeUiState.setupKit(): SetupKit = SetupKit(setupRows(), setupRunning, setupError)

/** True from tapping Download until every model is in, or one fails. */
val HomeUiState.setupRunning: Boolean get() = setupStarted && !setupError && !setupDownloadsReady

/** True when setup leaves the phone model out: the reader chose another translator, or one already takes the pages (#536). */
val HomeUiState.setupSkipsPhoneModel: Boolean get() = setupTranslatorId != null || !phoneTranslates

/** The slots setup downloads, in order: detection and OCR always, translation unless another translator takes the pages. */
val HomeUiState.setupTypes: List<ModelType> get() = ModelType.entries.filter { it != ModelType.LLM || !setupSkipsPhoneModel }

/** The translators setup offers in place of the phone model: each one with picker entries, until one is chosen. */
fun HomeUiState.setupTranslatorOffers(): List<PickerGroup> = if (setupTranslatorId == null) translatorGroups else emptyList()

/** The picker group of the translator setup hands the pages to, or null. */
fun HomeUiState.setupTranslatorGroup(): PickerGroup? = translatorGroups.firstOrNull { it.translatorId == setupTranslatorId }

/** Each slot's chosen model, in the order setup downloads them. A model too big for the phone stays too big once downloaded, since setup refuses it either way. */
fun HomeUiState.setupRows(): List<SetupModelRow> = setupTypes.mapNotNull { type ->
    val deliverable = deliverables[type].orEmpty().firstOrNull { it.id == chosenIds[type] } ?: return@mapNotNull null
    val download = setupDownload?.takeIf { it.modelId == deliverable.id }
    val state = when {
        !fits(deliverable) -> SetupModelState.TooBig
        models.any { it.id == deliverable.id && it.status == ModelStatus.READY } -> SetupModelState.Done
        download != null -> SetupModelState.Downloading(download.bytesDownloaded, download.totalBytes)
        setupFailedId == deliverable.id -> SetupModelState.Failed
        else -> SetupModelState.Missing
    }
    SetupModelRow(type, deliverable, state)
}
