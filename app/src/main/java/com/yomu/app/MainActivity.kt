package com.yomu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import com.yomu.app.ui.navigation.AppNavigation
import com.yomu.core.Constants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
                AppNavigation()
            }
        }
    }
}
