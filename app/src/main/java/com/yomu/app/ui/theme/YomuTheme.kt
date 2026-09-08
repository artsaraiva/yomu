package com.yomu.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val LocalYomuColors = staticCompositionLocalOf { DayPaper }

private val systemTypography = Typography()
val YomuTypography = systemTypography.copy(
    displayLarge = systemTypography.displayLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold),
    displayMedium = systemTypography.displayMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold),
    displaySmall = systemTypography.displaySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold),
    headlineLarge = systemTypography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = systemTypography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = systemTypography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = systemTypography.titleLarge.copy(fontWeight = FontWeight.Bold)
)

val YomuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun YomuTheme(colors: YomuColors = DayPaper, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalYomuColors provides colors) {
        MaterialTheme(colorScheme = colors.colorScheme(), typography = YomuTypography, shapes = YomuShapes, content = content)
    }
}
