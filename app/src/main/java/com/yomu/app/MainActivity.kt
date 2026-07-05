package com.yomu.app

import android.content.Intent
import android.net.Uri
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import com.yomu.app.service.OverlayService
import com.yomu.app.ui.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var mediaProjectionIntent: Intent? = null
    private var mediaProjectionResultCode: Int = RESULT_CANCELED

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            mediaProjectionIntent = data
            mediaProjectionResultCode = result.resultCode
            startOverlayService(data, result.resultCode)
        }
    }

    private fun startOverlayService(data: Intent, resultCode: Int) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Allow display over other apps, then enable Yomu again",
                Toast.LENGTH_SHORT
            ).show()
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_MEDIA_PROJECTION_DATA, data)
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    fun launchScreenCaptureConsent() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Allow display over other apps, then enable Yomu again",
                Toast.LENGTH_SHORT
            ).show()
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
            return
        }

        val cachedIntent = mediaProjectionIntent
        if (cachedIntent != null && mediaProjectionResultCode == RESULT_OK) {
            startOverlayService(cachedIntent, mediaProjectionResultCode)
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = androidx.compose.material3.darkColorScheme(
                    primary = Color(0xFFFF5722),
                    secondary = Color(0xFF4CAF50),
                    surface = Color(0xFF1A1A1A),
                    background = Color(0xFF121212),
                    onPrimary = Color.White,
                    onSecondary = Color.Black,
                    onSurface = Color(0xFFE0E0E0),
                    onBackground = Color(0xFFE0E0E0)
                )
            ) {
                AppNavigation(
                    onRequestScreenCapture = { launchScreenCaptureConsent() }
                )
            }
        }
    }
}
