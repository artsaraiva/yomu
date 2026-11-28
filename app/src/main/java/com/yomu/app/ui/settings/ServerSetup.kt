package com.yomu.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yomu.app.ui.theme.PaperButton
import com.yomu.app.ui.theme.PaperSurface
import com.yomu.ml.ConnectFailure

internal const val KEY_NOT_SAVED_MESSAGE =
    "Your key couldn't be saved on this phone. Yomu keeps it until the app closes; enter it again after that."

/** What the screen says when Connect fails, one message per cause. */
internal fun connectFailureMessage(failure: ConnectFailure): String = when (failure) {
    ConnectFailure.NOT_AN_ADDRESS -> "Start the address with http:// or https://, for example http://192.168.1.20:11434."
    ConnectFailure.UNREACHABLE -> "Can't reach the server. Check the address, and that the server is running and on the same network."
    ConnectFailure.KEY_REJECTED -> "The server rejected the key. Check it and tap Connect again."
    ConnectFailure.HTTPS_REQUIRED ->
        "This address isn't on your own network, so it needs https://. Plain http:// only reaches nearby addresses."
    ConnectFailure.SERVER_ERROR -> "The server answered, but not with a list of models. Check the address points to an OpenAI-compatible server."
}

/** The server's setup on a screen of its own, which first-run setup opens in place of a phone model (#536). */
@Composable
fun ServerSetupScreen(viewModel: ProvidersViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    SettingsPage("Your server", "A server you run, or a hosted API you have a key for.", onBack) {
        ServerSetup(state, viewModel, onPickListed = viewModel::pickModel)
    }
}

/**
 * The address, key, Connect and model of the reader's server (ADR-0023), as setup's screen and the Translator
 * page's server side both show them. [onPickListed] takes a model picked from the server's list; a typed name is
 * only stored, since each keystroke changes it.
 */
@Composable
internal fun ServerSetup(state: ProvidersUiState, viewModel: ProvidersViewModel, onPickListed: (String) -> Unit) {
    ServerPanel(state, viewModel::editAddress, viewModel::editApiKey, { viewModel.connect() })
    (state.connection as? ServerConnection.Connected)?.let { ModelPanel(it.models, state.model, onPickListed, viewModel::pickModel) }
}

@Composable
private fun ServerPanel(state: ProvidersUiState, onAddress: (String) -> Unit, onKey: (String) -> Unit, onConnect: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error
    PaperSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                state.address, onAddress, Modifier.fillMaxWidth(),
                label = { Text("Server address") },
                placeholder = { Text("http://192.168.1.20:11434") },
                supportingText = { Text("Plain http:// reaches only your own network. Anything else needs https://.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                state.apiKey, onKey, Modifier.fillMaxWidth(),
                label = { Text("API key (optional)") },
                supportingText = { Text("Saved and sent to this address only when you tap Connect.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)
            )
            val connecting = state.connection == ServerConnection.Connecting
            PaperButton(if (connecting) "Connecting…" else "Connect", onConnect, enabled = state.address.isNotBlank() && !connecting)
            when (val connection = state.connection) {
                ServerConnection.NotConnected ->
                    Text("Connect checks the address and lists the server's models.", style = MaterialTheme.typography.bodySmall, color = muted)
                ServerConnection.Connecting -> Unit
                is ServerConnection.Connected -> Text("Connected.", style = MaterialTheme.typography.bodyMedium)
                is ServerConnection.Failed ->
                    Text(connectFailureMessage(connection.failure), style = MaterialTheme.typography.bodyMedium, color = error)
            }
            if (state.keyNotSaved) Text(KEY_NOT_SAVED_MESSAGE, style = MaterialTheme.typography.bodyMedium, color = error)
        }
    }
}

/** The server's list to pick from, or a field for the model's name when there's no list to show. */
@Composable
private fun ModelPanel(models: List<String>?, model: String, onPick: (String) -> Unit, onType: (String) -> Unit) {
    PaperSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Model", style = MaterialTheme.typography.titleSmall)
            if (models.isNullOrEmpty()) {
                val hint = if (models == null) {
                    "Type the model's name, or tap Connect to list the server's models."
                } else {
                    "This server doesn't list its models. Type the name of the one to use."
                }
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    model, onType, Modifier.fillMaxWidth(),
                    label = { Text("Model name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
                )
            } else {
                Column(Modifier.selectableGroup()) {
                    models.forEach { name ->
                        Row(
                            Modifier.fillMaxWidth().selectable(name == model, role = Role.RadioButton, onClick = { onPick(name) }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = name == model, onClick = null)
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
