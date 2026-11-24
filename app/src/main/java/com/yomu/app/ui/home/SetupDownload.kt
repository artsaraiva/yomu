package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.ModelManager
import com.yomu.app.service.ModelSlotSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Setup's Download button (#464): fetches each slot's chosen model in turn and picks it, reporting every
 * step through [state].
 */
internal class SetupDownload(
    private val modelManager: ModelManager,
    private val slotSelection: ModelSlotSelection,
    private val state: MutableStateFlow<HomeUiState>
) {
    /** True once every chosen model is downloaded and picked; false when one failed, or is too big and nothing more was fetched. */
    suspend fun run(): Boolean {
        state.update {
            it.copy(setupStarted = true, setupError = false, setupFailedId = null, setupDownloadsReady = false, onMeteredNetwork = modelManager.isOnMeteredNetwork())
        }
        var current: String? = null
        try {
            modelManager.refreshModelList()
            for (type in ModelType.entries) {
                val id = slotSelection.chosenId(type)
                // Refused before downloading: pick would turn it down afterwards, leaving a download that never takes over.
                // Its row already says it is too big, so setup just returns to the Download button.
                if (!ModelSlotSelection.fits(id, slotSelection.fitBudget(state.value.fitBudget.totalMemBytes))) {
                    state.update { it.copy(setupStarted = false) }
                    return false
                }
                if (modelManager.getModel(id)?.status != ModelStatus.READY) {
                    current = id
                    val size = slotSelection.deliverables(type).firstOrNull { it.id == id }?.sizeBytes ?: 0L
                    state.update { it.copy(setupDownload = ModelManager.DownloadProgress(id, 0L, size, 0)) }
                    val success = modelManager.downloadModel(id) { progress ->
                        state.update { it.copy(setupDownload = progress) }
                    }
                    if (!success) {
                        state.update { it.copy(setupError = true, setupFailedId = id) }
                        return false
                    }
                }
                slotSelection.pick(type, id, ModelStatus.READY, state.value.fitBudget.totalMemBytes)
            }
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.update { it.copy(setupError = true, setupFailedId = current) }
            return false
        } finally {
            state.update { it.copy(setupDownload = null) }
        }
    }
}
