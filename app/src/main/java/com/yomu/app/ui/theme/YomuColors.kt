package com.yomu.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class YomuColors(
    val paper: Int,
    val paperRaised: Int,
    val ink: Int,
    val inkMuted: Int,
    val accent: Int,
    val onAccent: Int,
    val secondary: Int,
    val edge: Int,
    val success: Int,
    val error: Int,
    val warn: Int
) {
    fun colorScheme(): ColorScheme {
        val base = if (this == NightPaper) darkColorScheme() else lightColorScheme()
        return base.copy(
            background = Color(paper), surface = Color(paper),
            surfaceVariant = Color(paperRaised), surfaceTint = Color(paper),
            onBackground = Color(ink), onSurface = Color(ink),
            onSurfaceVariant = Color(inkMuted),
            primary = Color(accent), onPrimary = Color(onAccent),
            primaryContainer = Color(paperRaised), onPrimaryContainer = Color(ink),
            secondary = Color(secondary), tertiary = Color(secondary),
            onSecondary = Color(NightPaper.onAccent), onTertiary = Color(NightPaper.onAccent),
            secondaryContainer = Color(paperRaised), tertiaryContainer = Color(paperRaised),
            onSecondaryContainer = Color(ink), onTertiaryContainer = Color(ink),
            outline = Color(edge), outlineVariant = Color(edge),
            error = Color(error), onError = Color(paperRaised),
            errorContainer = Color(paperRaised), onErrorContainer = Color(ink),
            inverseSurface = Color(ink), inverseOnSurface = Color(paper),
            inversePrimary = Color(paperRaised)
        )
    }
}

val DayPaper = YomuColors(
    paper = 0xFFF4E9D0.toInt(), paperRaised = 0xFFFBF4E4.toInt(),
    ink = 0xFF2E2A24.toInt(), inkMuted = 0xFF6B6353.toInt(),
    accent = 0xFFE4572E.toInt(), onAccent = 0xFFFFF8EC.toInt(),
    secondary = 0xFF2A9D8F.toInt(), edge = 0xFFD8C7A5.toInt(),
    success = 0xFF3A7D44.toInt(), error = 0xFFC1352B.toInt(), warn = 0xFFC67A00.toInt()
)

val NightPaper = YomuColors(
    paper = 0xFF23201B.toInt(), paperRaised = 0xFF2E2A23.toInt(),
    ink = 0xFFEDE3CE.toInt(), inkMuted = 0xFFA99E86.toInt(),
    accent = 0xFFF0724A.toInt(), onAccent = 0xFF1C1915.toInt(),
    secondary = 0xFF3FB8A8.toInt(), edge = 0xFF4A4235.toInt(),
    success = 0xFF6FBF7B.toInt(), error = 0xFFF0776C.toInt(), warn = 0xFFE7A83A.toInt()
)
