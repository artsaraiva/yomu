package com.yomu.app.ui.home

import android.content.SharedPreferences
import com.yomu.app.db.HistoryDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update

/** Home's saved translations: keeps [state] in step with the history table, and empties it. */
internal class RecentHistory(
    private val historyDao: HistoryDao,
    private val preferences: SharedPreferences,
    private val state: MutableStateFlow<HomeUiState>
) {
    /** Collects until cancelled. The first page ever saved is remembered, so Home keeps its Recent section once history is cleared. */
    suspend fun load() {
        state.update { it.copy(historyLoading = true, historyError = false) }
        historyDao.getAllTranslations()
            .catch { state.update { it.copy(historyLoading = false, historyError = true) } }
            .collect { translations ->
                if (translations.isNotEmpty() && !state.value.hasRead) {
                    preferences.edit().putBoolean(HAS_READ, true).apply()
                }
                state.update {
                    it.copy(translations = translations, hasRead = it.hasRead || translations.isNotEmpty(), historyLoading = false)
                }
            }
    }

    suspend fun clear() {
        try {
            historyDao.clearHistory()
            state.update { it.copy(confirmation = "Recent translations cleared", clearError = false) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.update { it.copy(clearError = true) }
        }
    }

    companion object {
        const val HAS_READ = "has_read"
    }
}
