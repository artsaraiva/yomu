package com.yomu.app.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.yomu.app.translation.TranslationEngineSelector
import com.yomu.app.translation.TranslationEngineType
import com.yomu.core.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SettingsUiState(
    val translationMode: String = "local",
    val targetLanguage: String = "en",
    val sourceLanguage: String = "ja",
    val autoDetect: Boolean = true,
    val selectedEngine: TranslationEngineType = TranslationEngineType.ML_KIT,
    val fontSizeScale: Float = Constants.DEFAULT_FONT_SIZE_SCALE,
    val theme: String = "dark"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val translationEngineSelector: TranslationEngineSelector
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
}
