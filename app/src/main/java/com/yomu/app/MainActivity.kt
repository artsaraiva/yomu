package com.yomu.app

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import com.yomu.app.service.OverlayService
import com.yomu.app.ui.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var mediaProjectionIntent: Intent? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            mediaProjectionIntent = result.data
            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_MEDIA_PROJECTION_DATA, result.data)
                putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
            }
            startService(intent)
        }
    }

    fun launchScreenCaptureConsent() {
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
