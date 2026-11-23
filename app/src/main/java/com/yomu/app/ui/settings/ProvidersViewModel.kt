package com.yomu.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomu.app.translation.ApiKeyState
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.ml.ConnectFailure
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
    /** The model picked from the server's list or typed; stored as it changes. */
    val model: String,
    val connection: ServerConnection = ServerConnection.NotConnected,
    /** The Keystore refused the key: it works until the app closes, and must then be entered again. */
    val keyNotSaved: Boolean = false
)

/** Where Connect stands for the address on screen. */
sealed interface ServerConnection {
    /** Connect hasn't reached this address. */
    data object NotConnected : ServerConnection

    data object Connecting : ServerConnection

    /**
     * Connect reached the address and confirmed it. [models] is the server's list, empty when it keeps
     * none, or null when the address was confirmed on an earlier visit and the list hasn't been asked for since.
     */
    data class Connected(val models: List<String>?) : ServerConnection

    /** Connect didn't reach a server that answered; the address stays unconfirmed. */
    data class Failed(val failure: ConnectFailure) : ServerConnection
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

    // Opening the screen sends nothing: a confirmed address shows as connected, without its list.
    private val _state = MutableStateFlow(
        ProvidersUiState(
            address = store.address(),
            apiKey = store.apiKey(),
            model = store.model(),
            connection = if (store.isConfirmed()) ServerConnection.Connected(models = null) else ServerConnection.NotConnected,
            keyNotSaved = store.apiKeyState() == ApiKeyState.NOT_SAVED
        )
    )
    val state: StateFlow<ProvidersUiState> = _state.asStateFlow()

    private var connecting: Job? = null

    /**
     * Keeps [address] as typed and sends nothing. A different address than the saved one drops Connect's
     * confirmation and the model, and stops a Connect still running, so the key only reaches an address
     * Connect confirmed.
     */
    fun editAddress(address: String) {
        val saved = store.address()
        store.saveAddress(address)
        if (store.address() == saved) {
            _state.update { it.copy(address = address) }
            return
        }
        connecting?.cancel()
        _state.update { it.copy(address = address, model = store.model(), connection = ServerConnection.NotConnected) }
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
        // An unchanged key isn't sealed again, so a saved key the Keystore can't open now isn't overwritten.
        val keyState = if (key != store.apiKey()) store.saveApiKey(key) else store.apiKeyState()
        _state.update { it.copy(connection = ServerConnection.Connecting, keyNotSaved = keyState == ApiKeyState.NOT_SAVED) }
        return viewModelScope.launch {
            val connection = when (val result = serverModels.fetch(address, key)) {
                is ServerModels.Listed -> {
                    store.confirm(address)
                    ServerConnection.Connected(result.names)
                }
                ServerModels.Unlisted -> {
                    store.confirm(address)
                    ServerConnection.Connected(emptyList())
                }
                is ServerModels.Failed -> ServerConnection.Failed(result.failure)
            }
            _state.update { it.copy(connection = connection) }
        }.also { connecting = it }
    }

    /** Stores [model], picked from the server's list or typed. */
    fun pickModel(model: String) {
        store.saveModel(model)
        _state.update { it.copy(model = model) }
    }
}
