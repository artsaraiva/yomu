package com.yomu.app.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomu.app.detection.DetectionThresholdStore
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.ModelManager
import com.yomu.app.service.ModelSlotSelection
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.translation.ResourceLimit
import com.yomu.app.translation.TranslationModelSelection
import com.yomu.core.Constants
import com.yomu.core.GenerationBound
import com.yomu.core.GenerationParams
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val translationMode: String = "local",
    val targetLanguage: String = "en",
    val sourceLanguage: String = "ja",
    val deliverables: Map<ModelType, List<SlotDeliverable>> = emptyMap(),
    val selectedIds: Map<ModelType, String> = emptyMap(),
    val pendingIds: Map<ModelType, String> = emptyMap(),
    // Total device RAM, for the per-model fit budget (part D). 0 until read.
    val deviceTotalMemBytes: Long = 0L,
    val fontSizeScale: Float = Constants.DEFAULT_FONT_SIZE_SCALE,
    val theme: String = "system",
    val models: List<ModelEntity> = emptyList(),
    /** Percentage done of every download in progress, by model id. */
    val downloads: Map<String, Int> = emptyMap(),
    /** The reader's global sampler profile (#192). */
    val generation: GenerationParams = GenerationParams(),
    /** The reader's bubble confidence threshold (#227). */
    val detectionThreshold: Float = DetectionThresholdStore.DEFAULT,
    /** The reader's caps on the translation model (#79). */
    val resourceLimits: Map<ResourceLimit, Int> = ResourceLimit.entries.associateWith { it.default },
    /** What the app is using right now; null until the Performance screen first samples it. */
    val resources: ResourceReadout? = null,
    /** One message naming every stored value that was recovered to its default, or null. */
    val recoveryWarning: String? = null
) {
    val generationOverridden: Boolean
        get() = GenerationBound.entries.any { it.read(generation) != it.default }

    fun fits(deliverable: SlotDeliverable): Boolean =
        ModelSlotSelection.fits(deliverable.id, deviceTotalMemBytes, resourceLimits.getValue(ResourceLimit.RAM_PERCENT))
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val sharedPreferences: SharedPreferences,
    private val modelSelection: TranslationModelSelection,
    private val slotSelection: ModelSlotSelection,
    private val modelManager: ModelManager
) : ViewModel() {

    private val detectionThresholdStore = DetectionThresholdStore(sharedPreferences)
    private val resourceMonitor = ResourceMonitor(context)

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
            deliverables = ModelType.entries.associateWith(slotSelection::deliverables),
            deviceTotalMemBytes = modelManager.deviceTotalMemBytes(),
            fontSizeScale = sharedPreferences.getFloat(Constants.PREF_FONT_SIZE_SCALE, Constants.DEFAULT_FONT_SIZE_SCALE),
            theme = sharedPreferences.getString(Constants.PREF_THEME, "system") ?: "system",
            detectionThreshold = detectionThresholdStore.load(),
            resourceLimits = storedResourceLimits()
        ).withGenerationProfile().withSlots()
        // The warning is now on screen; forget the bad values so it does not return on every visit.
        if (_uiState.value.recoveryWarning != null) modelSelection.clearRecovered()

        viewModelScope.launch {
            modelManager.refreshModelList()
        }

        viewModelScope.launch {
            modelManager.getAllModels().collect { models ->
                slotSelection.commitPending(models.associate { it.id to it.status })
                _uiState.update { it.copy(models = models).withSlots() }
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

    fun pickModel(type: ModelType, id: String) {
        // Off the main thread: committing a translation model waits out any in-flight generation before swapping the
        // native model, which can take up to a batch timeout.
        viewModelScope.launch {
            val status = modelManager.getModel(id)?.status
            if (!slotSelection.pick(type, id, status, _uiState.value.deviceTotalMemBytes)) return@launch
            _uiState.update { it.withSlots() }
            if (status != ModelStatus.READY) downloadModel(id)
        }
    }

    /** Values the bounds refuse are dropped by the store; the panel just re-reads what stuck. */
    fun setGeneration(bound: GenerationBound, value: Float) {
        viewModelScope.launch {
            modelSelection.saveGeneration(bound, value)
            _uiState.value = _uiState.value.withGenerationProfile()
        }
    }

    fun resetGeneration() {
        viewModelScope.launch {
            modelSelection.resetGeneration()
            _uiState.value = _uiState.value.withGenerationProfile()
        }
    }

    fun setDetectionThreshold(value: Float) {
        detectionThresholdStore.save(value)
        _uiState.update { it.copy(detectionThreshold = detectionThresholdStore.load()) }
    }

    fun resetDetectionThreshold() {
        detectionThresholdStore.reset()
        _uiState.update { it.copy(detectionThreshold = detectionThresholdStore.load()) }
    }

    /** Like [pickModel], off the main thread: a threads or context change waits out any in-flight generation. */
    fun setResourceLimit(limit: ResourceLimit, value: Int) {
        viewModelScope.launch {
            modelSelection.saveResourceLimit(limit, value)
            _uiState.update { it.copy(resourceLimits = storedResourceLimits()) }
        }
    }

    fun resetResourceLimits() {
        viewModelScope.launch {
            modelSelection.resetResourceLimits()
            _uiState.update { it.copy(resourceLimits = storedResourceLimits()) }
        }
    }

    fun refreshResources() {
        viewModelScope.launch {
            val readout = withContext(Dispatchers.Default) { resourceMonitor.sample(modelSelection.lastPageDurationMs()) }
            _uiState.update { it.copy(resources = readout) }
        }
    }

    private fun storedResourceLimits(): Map<ResourceLimit, Int> =
        ResourceLimit.entries.associateWith(modelSelection::resourceLimit)

    private fun SettingsUiState.withGenerationProfile(): SettingsUiState {
        val loaded = modelSelection.generationProfile()
        val recovered = loaded.recovered.map { it.label } +
            if (modelSelection.storedLlmModelRecovered()) listOf("Model") else emptyList()
        return copy(
            generation = loaded.params,
            recoveryWarning = recovered.takeIf { it.isNotEmpty() }
                ?.let { "Some saved settings were invalid and are using their defaults: ${it.joinToString()}." }
        )
    }

    private fun SettingsUiState.withSlots(): SettingsUiState = copy(
        selectedIds = ModelType.entries.mapNotNull { type -> slotSelection.selectedId(type)?.let { type to it } }.toMap(),
        pendingIds = ModelType.entries.mapNotNull { type -> slotSelection.pendingId(type)?.let { type to it } }.toMap()
    )

    fun setFontSizeScale(scale: Float) {
        sharedPreferences.edit().putFloat(Constants.PREF_FONT_SIZE_SCALE, scale).apply()
        _uiState.value = _uiState.value.copy(fontSizeScale = scale)
    }

    private val downloadJobs = mutableMapOf<String, Job>()

    fun downloadModel(modelId: String) {
        if (modelId in downloadJobs) return
        // Lazy so the job is registered before it can finish and unregister itself.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var ready = false
            try {
                setDownloadProgress(modelId, 0)
                ready = modelManager.downloadModel(modelId) { setDownloadProgress(modelId, it.percentage) }
            } finally {
                downloadJobs.remove(modelId)
                // A pick waiting on a download that failed, was refused or was cancelled would otherwise never take over.
                if (!ready) slotSelection.dropPending(modelId)
                _uiState.update { it.copy(downloads = it.downloads - modelId).withSlots() }
            }
        }
        downloadJobs[modelId] = job
        job.start()
    }

    fun cancelDownload(modelId: String) {
        slotSelection.dropPending(modelId)
        _uiState.update { it.withSlots() }
        downloadJobs[modelId]?.cancel()
    }

    private fun setDownloadProgress(modelId: String, percentage: Int) {
        _uiState.update { it.copy(downloads = it.downloads + (modelId to percentage.coerceIn(0, 100))) }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            slotSelection.delete(modelId)
            _uiState.update { it.withSlots() }
        }
    }
}
