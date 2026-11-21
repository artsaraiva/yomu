package com.yomu.app.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.yomu.app.service.SaveToRecentStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * The Save pages to Recent switch (#454), written through to [SaveToRecentStore] as the reader flips it.
 * Settings > Translation flips it and Home shows what it means, each with its own instance kept current.
 */
@HiltViewModel
class SaveToRecentViewModel @Inject constructor(private val prefs: SharedPreferences) : ViewModel() {

    private val store = SaveToRecentStore(prefs)

    private val _enabled = MutableStateFlow(store.load())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    // Held in a field: SharedPreferences keeps listeners weakly. Home stays open under Settings and must hear the flip.
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SaveToRecentStore.KEY) _enabled.value = store.load()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun setEnabled(enabled: Boolean) {
        store.save(enabled)
        _enabled.value = store.load()
    }

    override fun onCleared() = prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
}
