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
import com.yomu.app.translation.EngineSelection
import com.yomu.app.translation.TranslationEngineType
import com.yomu.core.Constants
import com.yomu.core.GenerationBound
import com.yomu.core.GenerationParams
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
    val selectedEngine: TranslationEngineType = TranslationEngineType.ML_KIT,
    val selectedLlmModelId: String = LlmModelCatalog.DEFAULT.id,
    val llmModels: List<LlmModelOption> = LlmModelCatalog.ALL,
    // Total device RAM, for the per-model capability gate (part D). 0 until read.
    val deviceTotalMemBytes: Long = 0L,
    val fontSizeScale: Float = Constants.DEFAULT_FONT_SIZE_SCALE,
    val theme: String = "system",
    val models: List<ModelEntity> = emptyList(),
    val downloadingId: String? = null,
    val downloadProgress: Int = 0,
    /** The reader's global sampler profile (#192). */
    val generation: GenerationParams = GenerationParams(),
    /** One message naming every stored value that was recovered to its default, or null. */
    val recoveryWarning: String? = null
) {
    val generationOverridden: Boolean
        get() = GenerationBound.entries.any { it.read(generation) != it.default }

    /** Whether the device can run [option] (part D); the default is never gated out. */
    fun canRun(option: LlmModelOption): Boolean =
        deviceTotalMemBytes <= 0L || LlmModelCatalog.canRunOnDevice(option, deviceTotalMemBytes)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val engineSelection: EngineSelection,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        if (sharedPreferences.getString(Constants.PREF_THEME, "system") == "dark") {
            sharedPreferences.edit().putString(Constants.PREF_THEME, "night").apply()
        }
        val savedEngineId = sharedPreferences.getString(Constants.PREF_TRANSLATION_ENGINE, null)
        val engine = savedEngineId?.let { TranslationEngineType.fromId(it) } ?: TranslationEngineType.ML_KIT
        _uiState.value = SettingsUiState(
            translationMode = sharedPreferences.getString(Constants.PREF_TRANSLATION_MODE, "local") ?: "local",
            targetLanguage = sharedPreferences.getString(Constants.PREF_TARGET_LANGUAGE, "en") ?: "en",
            sourceLanguage = sharedPreferences.getString(Constants.PREF_SOURCE_LANGUAGE, "ja") ?: "ja",
            selectedEngine = engine,
            selectedLlmModelId = engineSelection.currentLlmModel().id,
            deviceTotalMemBytes = deviceTotalMemBytes(),
            fontSizeScale = sharedPreferences.getFloat(Constants.PREF_FONT_SIZE_SCALE, Constants.DEFAULT_FONT_SIZE_SCALE),
            theme = sharedPreferences.getString(Constants.PREF_THEME, "system") ?: "system"
        ).withGenerationProfile()

        viewModelScope.launch {
            modelManager.refreshModelList()
        }

        viewModelScope.launch {
            modelManager.getAllModels().collect { models ->
                _uiState.value = _uiState.value.copy(models = models)
            }
        }
    }

    fun setTheme(theme: String) {
        require(theme in listOf("system", "day", "night"))
        sharedPreferences.edit().putString(Constants.PREF_THEME, theme).apply()
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    fun setTranslationMode(mode: String) {
        sharedPreferences.edit().putString(Constants.PREF_TRANSLATION_MODE, mode).apply()
        _uiState.value = _uiState.value.copy(translationMode = mode)
    }

    fun setTranslationEngine(type: TranslationEngineType) {
        engineSelection.selectEngine(type)
        _uiState.value = _uiState.value.copy(selectedEngine = type)
    }

    /** Pick which curated LLM fills the translation slot (#90 part A). Entries that won't fit the device are ignored. */
    fun setLlmModel(option: LlmModelOption) {
        if (!_uiState.value.canRun(option)) return
        // Off the main thread: selectLlmModel waits out any in-flight generation before swapping the
        // native model, which can take up to a batch timeout.
        viewModelScope.launch {
            engineSelection.selectLlmModel(option)
            _uiState.value = _uiState.value.copy(
                selectedLlmModelId = engineSelection.currentLlmModel().id
            )
        }
    }

    /** Values the bounds refuse are dropped by the store; the panel just re-reads what stuck. */
    fun setGeneration(bound: GenerationBound, value: Float) {
        viewModelScope.launch {
            engineSelection.saveGeneration(bound, value)
            _uiState.value = _uiState.value.withGenerationProfile()
        }
    }

    fun resetGeneration() {
        viewModelScope.launch {
            engineSelection.resetGeneration()
            _uiState.value = _uiState.value.withGenerationProfile()
        }
    }

    private fun SettingsUiState.withGenerationProfile(): SettingsUiState {
        val loaded = engineSelection.generationProfile()
        val recovered = loaded.recovered.map { it.label } +
            if (engineSelection.storedLlmModelRecovered()) listOf("Model") else emptyList()
        return copy(
            generation = loaded.params,
            recoveryWarning = recovered.takeIf { it.isNotEmpty() }
                ?.let { "Some saved settings were invalid and are using their defaults: ${it.joinToString()}." }
        )
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
