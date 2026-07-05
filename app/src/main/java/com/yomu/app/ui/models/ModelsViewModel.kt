package com.yomu.app.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.service.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelsUiState(
    val models: List<ModelEntity> = emptyList(),
    val sections: List<ModelSection> = emptyList(),
    val isLoading: Boolean = false,
    val downloadingId: String? = null,
    val downloadProgress: Int = 0
)

data class ModelSection(
    val capability: ModelCapability,
    val models: List<ModelEntity>
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState

    init {
        viewModelScope.launch {
            modelManager.getAllModels().collect { models ->
                val grouped = models.groupBy { it.capability() }
                val sectionOrder = listOf(
                    ModelCapability.BUBBLE_DETECTION,
                    ModelCapability.OCR,
                    ModelCapability.TRANSLATION_ENGINE
                )
                _uiState.value = _uiState.value.copy(
                    models = models,
                    sections = sectionOrder.map { capability ->
                        ModelSection(capability, grouped[capability].orEmpty())
                    },
                    isLoading = false
                )
            }
        }
        refreshModels()
    }

    fun refreshModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            modelManager.refreshModelList()
        }
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloadingId = modelId,
                downloadProgress = 0
            )

            modelManager.downloadModel(modelId) { progress ->
                _uiState.value = _uiState.value.copy(
                    downloadProgress = progress.percentage
                )
            }

            _uiState.value = _uiState.value.copy(
                downloadingId = null,
                downloadProgress = 0
            )
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelManager.deleteModel(modelId)
        }
    }
}
