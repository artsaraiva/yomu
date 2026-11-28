package com.yomu.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yomu.app.db.entities.ModelType
import com.yomu.app.ui.home.TranslatorConfirmDialog
import com.yomu.app.ui.theme.PaperButton
import com.yomu.app.ui.theme.PaperSegments

/**
 * The one page that answers what translates the next page (#548): a model on this phone, or the reader's server
 * (ADR-0023). Turning the control to a side hands that side the pages as soon as it can take them; until then the
 * page says who still has them. [openOn] is the side to show first, or null for the one that has the pages.
 */
@Composable
fun TranslatorSettings(settings: SettingsViewModel, server: ProvidersViewModel, openOn: TranslatorSide?, onBack: () -> Unit) {
    val state by settings.uiState.collectAsState()
    val serverState by server.state.collectAsState()
    // Held once the page opens: a new server address hands the pages back to the phone, and the fields must stay put.
    var side by rememberSaveable { mutableStateOf(openOn ?: activeSide(state.translatesOnServer)) }
    // The server's fields decide who translates as they change: a new address hands the pages back to the phone.
    LaunchedEffect(serverState) { settings.refreshSlots() }
    SettingsPage(SettingsSection.Translator.title, "What turns the Japanese into ${state.targetLanguage.promptName}.", onBack) {
        PaperSegments(TranslatorSide.entries, side, TranslatorSide::label) {
            side = it
            if (it == TranslatorSide.Phone) settings.usePhone() else settings.useServer()
        }
        sideNote(side, state.phoneTranslates, state.translatesOnServer, serverState.ready)?.let { note ->
            SideNote(note, onUseServer = settings::useServer.takeIf { side == TranslatorSide.Server && serverState.ready })
        }
        when (side) {
            TranslatorSide.Phone -> {
                state.modelRecoveryWarning?.let { RecoveryWarning(it) }
                PhoneModelList(state, { settings.pickModel(ModelType.LLM, it) }, settings::cancelDownload, settings::deleteModel)
            }
            TranslatorSide.Server -> ServerSetup(serverState, server) {
                server.pickModel(it)
                settings.useServer()
            }
        }
    }
    state.translatorConfirm?.let { TranslatorConfirmDialog(it, settings::acceptTranslatorConfirm, settings::declineTranslatorConfirm) }
}

/** Who has the pages while the side on screen doesn't, with the way to hand them to a server that is ready. */
@Composable
private fun SideNote(note: String, onUseServer: (() -> Unit)?) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        onUseServer?.let { PaperButton("Translate on my server", it) }
    }
}
