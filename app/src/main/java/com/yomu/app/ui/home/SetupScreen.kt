package com.yomu.app.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yomu.app.ui.theme.*

@Composable
fun SetupScreen(state: HomeUiState, viewModel: HomeViewModel) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Set up reading", style = MaterialTheme.typography.headlineLarge)
        Text("Your manga, in English", style = MaterialTheme.typography.headlineSmall)
        Text("Yomu finds the speech bubbles, reads the Japanese and translates it on your device. Your pages stay with you.")
        PaperSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Read in the apps you love", style = MaterialTheme.typography.titleLarge)
                Text("Turn reading on and leave it on while you browse. Tap Yomu’s floating button whenever you want to translate a page.")
                Text("First, connect to Wi-Fi for a one-time download. We’ll prepare the parts that read the page and a lightweight English translator. You can change the engine in Settings.")
            }
        }
        when {
            state.setupError -> PaperError(
                "Setup couldn't finish. Check Wi-Fi and available storage, then try again. Completed downloads are kept.",
                viewModel::prepareSetup
            )
            !state.setupStarted -> PaperButton("Set up on this device", viewModel::prepareSetup, Modifier.fillMaxWidth())
            !state.setupDownloadsReady -> PaperSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.downloading ?: "Preparing reading…", style = MaterialTheme.typography.titleMedium)
                    if (state.downloadProgress == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else {
                        LinearProgressIndicator(progress = state.downloadProgress / 100f, modifier = Modifier.fillMaxWidth())
                        Text("${state.downloadProgress}%")
                    }
                    Text("Keep Yomu open while the download finishes.", style = MaterialTheme.typography.bodySmall)
                }
            }
            state.readiness != Readiness.Ready -> PaperSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Allow reading over other apps", style = MaterialTheme.typography.titleLarge)
                    Text("Yomu needs permission to show its floating button and English text over your manga. Enable ‘Allow display over other apps’, then come back here.")
                    PaperButton("Open permission settings", {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    })
                }
            }
            else -> PaperSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Check, contentDescription = "Ready")
                    Text("You're ready to read", style = MaterialTheme.typography.titleLarge)
                    Text("When you turn reading on, Android will ask to share your screen with Yomu for that reading session.")
                    PaperButton("Go to Home", viewModel::completeSetup)
                }
            }
        }
        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = viewModel::dismissSetup) { Text("Back to Home") }
    }
}
