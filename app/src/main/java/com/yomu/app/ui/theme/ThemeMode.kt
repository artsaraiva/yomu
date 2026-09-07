package com.yomu.app.ui.theme

enum class ThemeMode { Day, Night }

fun resolveThemeMode(settingsTheme: String, systemInDarkMode: Boolean): ThemeMode = when (settingsTheme) {
    "day" -> ThemeMode.Day
    "night", "dark" -> ThemeMode.Night
    else -> if (systemInDarkMode) ThemeMode.Night else ThemeMode.Day
}
