package com.yomu.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.ml.ServerModelList
import com.yomu.ml.ServerModels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Providers screen shows. */
data class ProvidersUiState(
    /** The address field, as typed. */
    val address: String,
    /** The key field, as typed; saved only when Connect is tapped. */
    val apiKey: String,
    val connection: ServerConnection = ServerConnection.NotConnected
)

/** Where Connect stands for the address on screen. */
sealed interface ServerConnection {
    /** Connect hasn't reached this address. */
    data object NotConnected : ServerConnection

    data object Connecting : ServerConnection

    /** Connect reached the address and confirmed it. [models] is the server's list. */
    data class Connected(val models: List<String>) : ServerConnection
}

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

    /**
     * Saves the address and key on screen, then asks that address for its models, sending the key only
     * when one is set. A server that answers is confirmed. The returned job ends when Connect does.
     */
    fun connect(): Job {
        val address = state.value.address.trim()
        val key = state.value.apiKey.trim()
        store.saveAddress(address)
        if (key != store.apiKey()) store.saveApiKey(key)
        _state.update { it.copy(connection = ServerConnection.Connecting) }
        return viewModelScope.launch {
            val connection = when (val result = serverModels.fetch(address, key)) {
                is ServerModels.Listed -> {
                    store.confirm(address)
                    ServerConnection.Connected(result.names)
                }
                else -> ServerConnection.NotConnected
            }
            _state.update { it.copy(connection = connection) }
        }
    }
}
