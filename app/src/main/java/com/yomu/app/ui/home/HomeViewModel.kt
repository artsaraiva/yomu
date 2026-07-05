package com.yomu.app.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomu.app.db.HistoryDao
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.service.ModelManager
import com.yomu.app.service.OverlayService
import com.yomu.core.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val isServiceRunning: Boolean = false,
    val translationMode: String = "local",
    val pagesTranslatedToday: Int = 0,
    val modelStatus: String = "Not downloaded",
    val modelCount: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val modelManager: ModelManager,
    private val historyDao: HistoryDao
) : ViewModel() {

    internal object ModelStatusMapper {
        fun map(models: List<ModelEntity>): Pair<String, Int> {
            if (models.isEmpty()) {
                return "Not downloaded" to 0
            }

            val requiredModels = models.filter { it.isRequired }
            val requiredCount = requiredModels.size
            val readyCount = requiredModels.count { it.status == ModelStatus.READY }

            if (requiredCount > 0 && readyCount == requiredCount) {
                return "Ready" to readyCount
            }

            if (readyCount > 0 && requiredCount > readyCount) {
                return "$readyCount/$requiredCount required ready" to readyCount
            }

            return "Not downloaded" to 0
        }
    }
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                OverlayService.ACTION_SERVICE_STARTED -> _uiState.value = _uiState.value.copy(isServiceRunning = true)
                OverlayService.ACTION_SERVICE_STOPPED -> _uiState.value = _uiState.value.copy(isServiceRunning = false)
                OverlayService.ACTION_SERVICE_START_FAILED -> _uiState.value = _uiState.value.copy(isServiceRunning = false)
            }
        }
    }

    init {
        val mode = sharedPreferences.getString(Constants.PREF_TRANSLATION_MODE, "local") ?: "local"
        _uiState.value = HomeUiState(translationMode = mode)
        viewModelScope.launch {
            modelManager.refreshModelList()
        }
        viewModelScope.launch {
            modelManager.getAllModels().collect { models ->
                val (status, count) = ModelStatusMapper.map(models)
                _uiState.value = _uiState.value.copy(
                    modelStatus = status,
                    modelCount = count
                )
            }
        }
        viewModelScope.launch {
            historyDao.getTranslationCountSince(startOfTodayMs()).collect { count ->
                _uiState.value = _uiState.value.copy(pagesTranslatedToday = count)
            }
        }
        try {
            ContextCompat.registerReceiver(
                context,
                serviceStateReceiver,
                IntentFilter().apply {
                    addAction(OverlayService.ACTION_SERVICE_STARTED)
                    addAction(OverlayService.ACTION_SERVICE_STOPPED)
                    addAction(OverlayService.ACTION_SERVICE_START_FAILED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {
        }
    }

    fun stopService() {
        OverlayService.stop(context)
    }

    fun setTranslationMode(mode: String) {
        sharedPreferences.edit().putString(Constants.PREF_TRANSLATION_MODE, mode).apply()
        _uiState.value = _uiState.value.copy(translationMode = mode)
    }

    override fun onCleared() {
        try {
            context.unregisterReceiver(serviceStateReceiver)
        } catch (_: Exception) {
        }
        super.onCleared()
    }

    private fun startOfTodayMs(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
