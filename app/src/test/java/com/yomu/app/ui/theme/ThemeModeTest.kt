package com.yomu.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun explicitModesOverrideSystemAndLegacyDarkResolvesToNight() {
        for (systemDark in listOf(false, true)) {
            assertEquals(ThemeMode.Day, resolveThemeMode("day", systemDark))
            assertEquals(ThemeMode.Night, resolveThemeMode("night", systemDark))
            assertEquals(ThemeMode.Night, resolveThemeMode("dark", systemDark))
        }
        assertEquals(ThemeMode.Day, resolveThemeMode("system", false))
        assertEquals(ThemeMode.Night, resolveThemeMode("system", true))
        assertEquals(ThemeMode.Day, resolveThemeMode("unknown", false))
        assertEquals(ThemeMode.Night, resolveThemeMode("unknown", true))
    }
}
