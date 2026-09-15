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
import com.yomu.app.translation.TranslationModelSelection
import com.yomu.core.Constants
import com.yomu.core.GenerationBound
import com.yomu.core.GenerationParams
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val translationMode: String = "local",
    val targetLanguage: String = "en",
    val sourceLanguage: String = "ja",
    val selectedLlmModelId: String = LlmModelCatalog.DEFAULT.id,
    val llmModels: List<LlmModelOption> = LlmModelCatalog.ALL,
    // Total device RAM, for the per-model capability gate (part D). 0 until read.
    val deviceTotalMemBytes: Long = 0L,
    val fontSizeScale: Float = Constants.DEFAULT_FONT_SIZE_SCALE,
    val theme: String = "system",
    val models: List<ModelEntity> = emptyList(),
    /** Percentage done of every download in progress, by model id. */
    val downloads: Map<String, Int> = emptyMap(),
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
    private val translationModel: TranslationModelSelection,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        if (sharedPreferences.getString(Constants.PREF_THEME, "system") == "dark") {
            sharedPreferences.edit().putString(Constants.PREF_THEME, "night").apply()
        }
        _uiState.value = SettingsUiState(
            translationMode = sharedPreferences.getString(Constants.PREF_TRANSLATION_MODE, "local") ?: "local",
            targetLanguage = sharedPreferences.getString(Constants.PREF_TARGET_LANGUAGE, "en") ?: "en",
            sourceLanguage = sharedPreferences.getString(Constants.PREF_SOURCE_LANGUAGE, "ja") ?: "ja",
            selectedLlmModelId = translationModel.currentLlmModel().id,
            deviceTotalMemBytes = deviceTotalMemBytes(),
            fontSizeScale = sharedPreferences.getFloat(Constants.PREF_FONT_SIZE_SCALE, Constants.DEFAULT_FONT_SIZE_SCALE),
            theme = sharedPreferences.getString(Constants.PREF_THEME, "system") ?: "system"
        ).withGenerationProfile()
        // The warning is now on screen; forget the bad values so it does not return on every visit.
        if (_uiState.value.recoveryWarning != null) translationModel.clearRecovered()

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

    /** Pick which curated LLM fills the translation slot (#90 part A). Entries that won't fit the device are ignored. */
    fun setLlmModel(option: LlmModelOption) {
        if (!_uiState.value.canRun(option)) return
        // Off the main thread: selectLlmModel waits out any in-flight generation before swapping the
        // native model, which can take up to a batch timeout.
        viewModelScope.launch {
            translationModel.selectLlmModel(option)
            _uiState.value = _uiState.value.copy(
                selectedLlmModelId = translationModel.currentLlmModel().id
            )
        }
    }

    /** Values the bounds refuse are dropped by the store; the panel just re-reads what stuck. */
    fun setGeneration(bound: GenerationBound, value: Float) {
        viewModelScope.launch {
            translationModel.saveGeneration(bound, value)
            _uiState.value = _uiState.value.withGenerationProfile()
        }
    }

    fun resetGeneration() {
        viewModelScope.launch {
            translationModel.resetGeneration()
            _uiState.value = _uiState.value.withGenerationProfile()
        }
    }

    private fun SettingsUiState.withGenerationProfile(): SettingsUiState {
        val loaded = translationModel.generationProfile()
        val recovered = loaded.recovered.map { it.label } +
            if (translationModel.storedLlmModelRecovered()) listOf("Model") else emptyList()
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

    private val downloadJobs = mutableMapOf<String, Job>()

    fun downloadModel(modelId: String) {
        if (modelId in downloadJobs) return
        // Lazy so the job is registered before it can finish and unregister itself.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                setDownloadProgress(modelId, 0)
                modelManager.downloadModel(modelId) { setDownloadProgress(modelId, it.percentage) }
            } finally {
                downloadJobs.remove(modelId)
                _uiState.update { it.copy(downloads = it.downloads - modelId) }
            }
        }
        downloadJobs[modelId] = job
        job.start()
    }

    fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
    }

    private fun setDownloadProgress(modelId: String, percentage: Int) {
        _uiState.update { it.copy(downloads = it.downloads + (modelId to percentage.coerceIn(0, 100))) }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelManager.deleteModel(modelId)
        }
    }
}
