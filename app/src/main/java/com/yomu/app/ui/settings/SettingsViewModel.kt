package com.yomu.app.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.service.ModelManager
import com.yomu.app.translation.TranslationEngineSelector
import com.yomu.app.translation.TranslationEngineType
import com.yomu.core.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val translationMode: String = "local",
    val targetLanguage: String = "en",
    val sourceLanguage: String = "ja",
    val autoDetect: Boolean = true,
    val selectedEngine: TranslationEngineType = TranslationEngineType.ML_KIT,
    val fontSizeScale: Float = Constants.DEFAULT_FONT_SIZE_SCALE,
    val theme: String = "dark",
    val models: List<ModelEntity> = emptyList(),
    val downloadingId: String? = null,
    val downloadProgress: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val translationEngineSelector: TranslationEngineSelector,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val savedEngineId = sharedPreferences.getString(Constants.PREF_TRANSLATION_ENGINE, null)
        val engine = savedEngineId?.let { TranslationEngineType.fromId(it) } ?: TranslationEngineType.ML_KIT
        _uiState.value = SettingsUiState(
            translationMode = sharedPreferences.getString(Constants.PREF_TRANSLATION_MODE, "local") ?: "local",
            targetLanguage = sharedPreferences.getString(Constants.PREF_TARGET_LANGUAGE, "en") ?: "en",
            sourceLanguage = sharedPreferences.getString(Constants.PREF_SOURCE_LANGUAGE, "ja") ?: "ja",
            autoDetect = sharedPreferences.getBoolean(Constants.PREF_AUTO_DETECT, true),
            selectedEngine = engine,
            fontSizeScale = sharedPreferences.getFloat(Constants.PREF_FONT_SIZE_SCALE, Constants.DEFAULT_FONT_SIZE_SCALE),
            theme = "dark"
        )

        viewModelScope.launch {
            modelManager.refreshModelList()
        }

        viewModelScope.launch {
            modelManager.getAllModels().collect { models ->
                _uiState.value = _uiState.value.copy(models = models)
            }
        }
    }

    fun setTranslationMode(mode: String) {
        sharedPreferences.edit().putString(Constants.PREF_TRANSLATION_MODE, mode).apply()
        _uiState.value = _uiState.value.copy(translationMode = mode)
    }

    fun setAutoDetect(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(Constants.PREF_AUTO_DETECT, enabled).apply()
        _uiState.value = _uiState.value.copy(autoDetect = enabled)
    }

    fun setTranslationEngine(type: TranslationEngineType) {
        translationEngineSelector.selectEngine(type)
        _uiState.value = _uiState.value.copy(selectedEngine = type)
    }

    fun setFontSizeScale(scale: Float) {
        sharedPreferences.edit().putFloat(Constants.PREF_FONT_SIZE_SCALE, scale).apply()
        _uiState.value = _uiState.value.copy(fontSizeScale = scale)
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(downloadingId = modelId, downloadProgress = 0)
            modelManager.downloadModel(modelId) { progress ->
                _uiState.value = _uiState.value.copy(downloadProgress = progress.percentage)
            }
            _uiState.value = _uiState.value.copy(downloadingId = null, downloadProgress = 0)
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelManager.deleteModel(modelId)
        }
    }
}
