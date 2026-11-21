package com.yomu.app.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.yomu.app.service.SaveToRecentStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** The Save pages to Recent switch (#454), written through to [SaveToRecentStore] as the reader flips it. */
@HiltViewModel
class SaveToRecentViewModel @Inject constructor(prefs: SharedPreferences) : ViewModel() {

    private val store = SaveToRecentStore(prefs)

    private val _enabled = MutableStateFlow(store.load())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        store.save(enabled)
        _enabled.value = store.load()
    }
}
