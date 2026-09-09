package com.yomu.app.ui.home

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomu.app.db.HistoryDao
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.service.ModelManager
import com.yomu.app.service.OverlayService
import com.yomu.app.translation.EngineSelection
import com.yomu.app.translation.TranslationEngineType
import com.yomu.core.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val modelManager: ModelManager,
    private val historyDao: HistoryDao,
    private val engineSelector: EngineSelection
) : ViewModel() {
    private companion object {
        const val SETUP_COMPLETE = "reading_setup_complete"
        const val HAS_READ = "has_read"
    }

    private val _uiState = MutableStateFlow(HomeUiState(
        setupComplete = sharedPreferences.getBoolean(SETUP_COMPLETE, false),
        hasRead = sharedPreferences.getBoolean(HAS_READ, false),
        selectedEngine = engineSelector.currentEngine()
    ))
    val uiState = _uiState.asStateFlow()
    private var historyJob: Job? = null
    private var countJob: Job? = null
    private var setupJob: Job? = null

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                OverlayService.ACTION_SERVICE_STARTED -> _uiState.update {
                    it.copy(isServiceRunning = true, serviceError = false, confirmation = "Reading is on")
                }
                OverlayService.ACTION_SERVICE_STOPPED -> _uiState.update { it.copy(isServiceRunning = false) }
                OverlayService.ACTION_SERVICE_START_FAILED -> _uiState.update {
                    it.copy(isServiceRunning = false, serviceError = true)
                }
            }
        }
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Constants.PREF_TRANSLATION_ENGINE) {
            _uiState.update { it.copy(selectedEngine = engineSelector.currentEngine()) }
        }
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        ContextCompat.registerReceiver(context, serviceStateReceiver, IntentFilter().apply {
            addAction(OverlayService.ACTION_SERVICE_STARTED)
            addAction(OverlayService.ACTION_SERVICE_STOPPED)
            addAction(OverlayService.ACTION_SERVICE_START_FAILED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        viewModelScope.launch {
            try {
                modelManager.refreshModelList()
                modelManager.getAllModels().collect { models ->
                    _uiState.update { it.copy(models = models) }
                    refreshReadiness()
                    _uiState.update { state ->
                        state.copy(
                            setupVisible = state.setupVisible ||
                                (state.modelsLoading && (!state.setupComplete || state.readiness != Readiness.Ready)),
                            modelsLoading = false
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(modelsLoading = false, setupVisible = true, setupError = true) }
            }
        }
        loadRecentHistory()
        refreshEnvironment()
    }

    private fun refreshReadiness() {
        val permission = Settings.canDrawOverlays(context)
        _uiState.update { state ->
            val readiness = resolveReadiness(state.models.associate { it.id to it.status }, permission)
            state.copy(
                readiness = readiness,
                setupVisible = state.setupVisible ||
                    (state.readiness == Readiness.Ready && readiness != Readiness.Ready)
            )
        }
    }

    @Suppress("DEPRECATION")
    fun refreshEnvironment() {
        refreshReadiness()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val running = manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == OverlayService::class.java.name && it.foreground
        }
        _uiState.update { it.copy(isServiceRunning = running, selectedEngine = engineSelector.currentEngine()) }
        countJob?.cancel()
        countJob = viewModelScope.launch {
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            historyDao.getTranslationCountSince(start)
                .catch { _uiState.update { it.copy(historyError = true) } }
                .collect { count -> _uiState.update { it.copy(pagesTranslatedToday = count) } }
        }
    }

    fun openSetup() {
        _uiState.update { it.copy(setupVisible = true) }
    }

    fun dismissSetup() {
        _uiState.update { it.copy(setupVisible = false) }
    }

    fun prepareSetup() {
        if (setupJob?.isActive == true) return
        setupJob = viewModelScope.launch {
            _uiState.update { it.copy(setupStarted = true, setupError = false, setupDownloadsReady = false) }
            try {
                modelManager.refreshModelList()
                val downloads = listOf(
                    Constants.BUBBLE_DETECTION_MODEL_ID to "Finding speech bubbles",
                    Constants.MANGA_OCR_MODEL_ID to "Reading Japanese",
                    Constants.ML_KIT_JA_EN_MODEL_ID to "Translating into English"
                )
                for ((id, label) in downloads) {
                    if (modelManager.getModel(id)?.status == ModelStatus.READY) continue
                    _uiState.update { it.copy(downloading = label, downloadProgress = 0) }
                    val success = modelManager.downloadModel(id) { progress ->
                        _uiState.update { it.copy(downloadProgress = progress.percentage.coerceIn(0, 100)) }
                    }
                    if (!success) {
                        _uiState.update { it.copy(setupError = true) }
                        return@launch
                    }
                }
                if (!sharedPreferences.contains(Constants.PREF_TRANSLATION_ENGINE)) {
                    engineSelector.selectEngine(TranslationEngineType.ML_KIT)
                }
                _uiState.update { it.copy(setupDownloadsReady = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(setupError = true) }
            } finally {
                _uiState.update { it.copy(downloading = null) }
            }
        }
    }

    fun completeSetup() {
        refreshReadiness()
        if (_uiState.value.readiness != Readiness.Ready || !_uiState.value.setupDownloadsReady) return
        sharedPreferences.edit().putBoolean(SETUP_COMPLETE, true).apply()
        _uiState.update { it.copy(setupComplete = true, setupVisible = false, setupStarted = false) }
    }

    fun loadRecentHistory() {
        historyJob?.cancel()
        _uiState.update { it.copy(historyLoading = true, historyError = false) }
        historyJob = viewModelScope.launch {
            historyDao.getAllTranslations()
                .catch { _uiState.update { it.copy(historyLoading = false, historyError = true) } }
                .collect { translations ->
                    if (translations.isNotEmpty() && !_uiState.value.hasRead) {
                        sharedPreferences.edit().putBoolean(HAS_READ, true).apply()
                    }
                    _uiState.update {
                        it.copy(translations = translations, hasRead = it.hasRead || translations.isNotEmpty(), historyLoading = false)
                    }
                }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                historyDao.clearHistory()
                _uiState.update { it.copy(confirmation = "Recent translations cleared", clearError = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(clearError = true) }
            }
        }
    }

    fun dismissConfirmation() {
        _uiState.update { it.copy(confirmation = null) }
    }

    fun dismissServiceError() {
        _uiState.update { it.copy(serviceError = false) }
    }

    fun stopService() {
        OverlayService.stop(context)
    }

    override fun onCleared() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        context.unregisterReceiver(serviceStateReceiver)
        super.onCleared()
    }
}
