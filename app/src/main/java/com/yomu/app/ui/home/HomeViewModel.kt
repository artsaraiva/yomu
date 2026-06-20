package com.yomu.app.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.yomu.app.service.ModelManager
import com.yomu.app.service.OverlayService
import com.yomu.core.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val modelManager: ModelManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                OverlayService.ACTION_SERVICE_STARTED -> _uiState.value = _uiState.value.copy(isServiceRunning = true)
                OverlayService.ACTION_SERVICE_STOPPED -> _uiState.value = _uiState.value.copy(isServiceRunning = false)
            }
        }
    }

    init {
        val mode = sharedPreferences.getString(Constants.PREF_TRANSLATION_MODE, "local") ?: "local"
        _uiState.value = HomeUiState(translationMode = mode)
        try {
            ContextCompat.registerReceiver(
                context,
                serviceStateReceiver,
                IntentFilter().apply {
                    addAction(OverlayService.ACTION_SERVICE_STARTED)
                    addAction(OverlayService.ACTION_SERVICE_STOPPED)
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
}
