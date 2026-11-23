package com.yomu.app.ui.settings

import androidx.lifecycle.ViewModel
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.ml.ServerModelList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** What the Providers screen shows. */
data class ProvidersUiState(
    /** The address field, as typed. */
    val address: String,
    /** The key field, as typed; saved only when Connect is tapped. */
    val apiKey: String
)

/**
 * The Providers screen, which sets up the network translator's server (ADR-0023). Typing changes only
 * the fields; Connect is the one thing that contacts the server.
 */
@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val store: ServerSettingsStore,
    private val serverModels: ServerModelList
) : ViewModel() {

    private val _state = MutableStateFlow(ProvidersUiState(address = store.address(), apiKey = store.apiKey()))
    val state: StateFlow<ProvidersUiState> = _state.asStateFlow()

    fun editAddress(address: String) {
        _state.update { it.copy(address = address) }
    }

    fun editApiKey(key: String) {
        _state.update { it.copy(apiKey = key) }
    }
}
