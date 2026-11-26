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

/** One slot's row on the setup screen: the model setup downloads for it, and how far that download is. */
data class SetupModelRow(val type: ModelType, val deliverable: SlotDeliverable, val state: SetupModelState) {
    val slot: String get() = SLOT_NAMES.getValue(type)

    /** [running] turns a missing model's size into "Waiting", since setup will get to it. */
    fun statusText(running: Boolean): String = when (state) {
        SetupModelState.Missing -> if (running) "Waiting · ${deliverable.sizeBytes.toFileSizeString()}" else deliverable.sizeBytes.toFileSizeString()
        is SetupModelState.Downloading -> "${state.bytesDownloaded.toFileSizeString()} of ${state.totalBytes.toFileSizeString()}"
        SetupModelState.Done -> "Done ✓"
        SetupModelState.Failed -> "Failed"
        SetupModelState.TooBig -> "Too big for this phone."
    }

    /** What TalkBack announces. It leaves out the bytes, so a download is announced when it starts and ends, not on every update. */
    fun announcement(): String {
        val spoken = when (state) {
            SetupModelState.Missing -> "not downloaded"
            is SetupModelState.Downloading -> "downloading"
            SetupModelState.Done -> "downloaded"
            SetupModelState.Failed -> "failed"
            SetupModelState.TooBig -> "too big for this phone"
        }
        return "$slot, ${deliverable.name}, $spoken"
    }

    private companion object {
        val SLOT_NAMES = mapOf(ModelType.DETECTION to "Detection", ModelType.OCR to "OCR", ModelType.LLM to "Translation")
    }
}

data class SetupStep(val label: String, val done: Boolean)

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

/** The checklist under the title: the models, the chosen translator if any, then permission to draw over other apps. */
fun HomeUiState.setupSteps(): List<SetupStep> {
    val rows = setupRows()
    val downloaded = rows.isNotEmpty() && rows.all { it.state == SetupModelState.Done }
    val left = rows.filter { it.state != SetupModelState.Done }.sumOf { it.deliverable.sizeBytes }
    return listOfNotNull(
        SetupStep(if (downloaded) "Models downloaded" else "Download models · ${left.toFileSizeString()}", downloaded),
        setupTranslatorGroup()?.let { SetupStep("Set up ${it.name.replaceFirstChar(Char::lowercase)}", !phoneTranslates) },
        SetupStep("Allow display over other apps", readiness == Readiness.Ready || readiness == Readiness.NeedsModels)
    )
}
