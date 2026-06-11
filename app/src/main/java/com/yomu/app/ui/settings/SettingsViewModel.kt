package com.yomu.app.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.yomu.core.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class SettingsUiState(
    val translationMode: String = "local",
    val targetLanguage: String = "en",
    val sourceLanguage: String = "ja",
    val autoDetect: Boolean = true,
    val theme: String = "dark"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    var uiState: SettingsUiState = SettingsUiState()
        private set

    init {
        loadSettings()
    }

    private fun loadSettings() {
        uiState = SettingsUiState(
            translationMode = sharedPreferences.getString(Constants.PREF_TRANSLATION_MODE, "local") ?: "local",
            targetLanguage = sharedPreferences.getString(Constants.PREF_TARGET_LANGUAGE, "en") ?: "en",
            sourceLanguage = sharedPreferences.getString(Constants.PREF_SOURCE_LANGUAGE, "ja") ?: "ja",
            autoDetect = sharedPreferences.getBoolean(Constants.PREF_AUTO_DETECT, true),
            theme = "dark"
        )
    }

    fun setTranslationMode(mode: String) {
        sharedPreferences.edit().putString(Constants.PREF_TRANSLATION_MODE, mode).apply()
        uiState = uiState.copy(translationMode = mode)
    }

    fun setAutoDetect(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(Constants.PREF_AUTO_DETECT, enabled).apply()
        uiState = uiState.copy(autoDetect = enabled)
    }
}
