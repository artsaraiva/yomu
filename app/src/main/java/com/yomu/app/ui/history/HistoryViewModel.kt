package com.yomu.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomu.app.db.HistoryDao
import com.yomu.app.db.entities.TranslationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val translations: List<TranslationEntity> = emptyList(),
    val isEmpty: Boolean = true,
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val clearError: Boolean = false,
    val cleared: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(private val historyDao: HistoryDao) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState
    private var historyJob: Job? = null

    init { loadHistory() }

    fun loadHistory() {
        historyJob?.cancel()
        _uiState.value = _uiState.value.copy(loading = true, loadError = false)
        historyJob = viewModelScope.launch {
            historyDao.getAllTranslations()
                .catch { _uiState.value = _uiState.value.copy(loading = false, loadError = true) }
                .collect { translations ->
                    _uiState.value = _uiState.value.copy(translations = translations, isEmpty = translations.isEmpty(), loading = false)
                }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                historyDao.clearHistory()
                _uiState.value = _uiState.value.copy(cleared = true, clearError = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(clearError = true)
            }
        }
    }

    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(cleared = false)
    }
}
