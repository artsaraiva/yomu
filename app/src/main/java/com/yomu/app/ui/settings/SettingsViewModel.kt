package com.yomu.app.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.service.ModelManager
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.app.translation.LlmModelOption
import com.yomu.app.translation.TranslationEngineSelector
import com.yomu.app.translation.TranslationEngineType
import com.yomu.core.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val selectedLlmModelId: String = LlmModelCatalog.DEFAULT.id,
    val llmModels: List<LlmModelOption> = LlmModelCatalog.ALL,
    // Total device RAM, for the per-model capability gate (part D). 0 until read.
    val deviceTotalMemBytes: Long = 0L,
    val fontSizeScale: Float = Constants.DEFAULT_FONT_SIZE_SCALE,
    val theme: String = "dark",
    val models: List<ModelEntity> = emptyList(),
    val downloadingId: String? = null,
    val downloadProgress: Int = 0
) {
    /** Whether the device can run [option] (part D); the default is never gated out. */
    fun canRun(option: LlmModelOption): Boolean =
        deviceTotalMemBytes <= 0L || LlmModelCatalog.canRunOnDevice(option, deviceTotalMemBytes)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
            selectedLlmModelId = translationEngineSelector.currentLlmModel().id,
            deviceTotalMemBytes = deviceTotalMemBytes(),
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

    /** Pick which curated LLM fills the translation slot (#90 part A). Gated entries are ignored. */
    fun setLlmModel(option: LlmModelOption) {
        if (!_uiState.value.canRun(option)) return
        // Off the main thread: selectLlmModel waits out any in-flight generation before swapping the
        // native model, which can take up to a batch timeout.
        viewModelScope.launch {
            translationEngineSelector.selectLlmModel(option)
            _uiState.value = _uiState.value.copy(
                selectedLlmModelId = translationEngineSelector.currentLlmModel().id
            )
        }
    }

    private fun deviceTotalMemBytes(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
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
