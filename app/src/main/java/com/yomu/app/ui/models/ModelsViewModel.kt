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
    val isLoading: Boolean = false,
    val downloadingId: String? = null,
    val downloadProgress: Int = 0
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
                _uiState.value = _uiState.value.copy(
                    models = models,
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

            val success = modelManager.downloadModel(modelId) { progress ->
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
