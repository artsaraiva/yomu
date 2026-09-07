package com.yomu.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class YomuColorsTest {
    @Test
    fun secondaryFilledControlsHaveReadableLabels() {
        for (palette in listOf(DayPaper, NightPaper)) {
            val scheme = palette.colorScheme()
            assertContrast(scheme.onSecondary.toArgb(), scheme.secondary.toArgb(), 4.5)
            assertContrast(scheme.onTertiary.toArgb(), scheme.tertiary.toArgb(), 4.5)
        }
    }

    @Test
    fun palettesUseEveryExactSpecToken() {
        assertEquals(
            listOf("F4E9D0", "FBF4E4", "2E2A24", "6B6353", "E4572E", "FFF8EC", "2A9D8F", "D8C7A5", "3A7D44", "C1352B", "C67A00"),
            tokens(DayPaper).map { "%06X".format(it and 0xFFFFFF) }
        )
        assertEquals(
            listOf("23201B", "2E2A23", "EDE3CE", "A99E86", "F0724A", "1C1915", "3FB8A8", "4A4235", "6FBF7B", "F0776C", "E7A83A"),
            tokens(NightPaper).map { "%06X".format(it and 0xFFFFFF) }
        )
    }

    private fun tokens(palette: YomuColors) = with(palette) {
        listOf(paper, paperRaised, ink, inkMuted, accent, onAccent, secondary, edge, success, error, warn)
    }

    @Test
    fun paperPalettesPreserveTheSpecAndReadableForegrounds() {
        assertEquals(0xFFF4E9D0.toInt(), DayPaper.paper)
        assertEquals(0xFF23201B.toInt(), NightPaper.paper)
        for (palette in listOf(DayPaper, NightPaper)) {
            for (background in listOf(palette.paper, palette.paperRaised)) {
                assertContrast(palette.ink, background, 4.5)
                assertContrast(palette.inkMuted, background, 4.5)
                assertContrast(palette.accent, background, 3.0)
                assertContrast(palette.error, background, 3.0)
            }
            assertContrast(palette.onAccent, palette.accent, 3.0)
            assertContrast(palette.secondary, palette.paperRaised, 3.0)
            assertContrast(palette.warn, palette.paperRaised, 3.0)
            assertContrast(palette.success, palette.paper, 3.0)
            assertContrast(palette.success, palette.paperRaised, 3.0)
            val scheme = palette.colorScheme()
            assertEquals(Color(palette.paper), scheme.background)
            assertEquals(Color(palette.paper), scheme.surface)
            assertEquals(Color(palette.paperRaised), scheme.surfaceVariant)
            assertEquals(Color(palette.ink), scheme.onBackground)
            assertEquals(Color(palette.ink), scheme.onSurface)
            assertEquals(Color(palette.inkMuted), scheme.onSurfaceVariant)
            assertEquals(Color(palette.accent), scheme.primary)
            assertEquals(Color(palette.onAccent), scheme.onPrimary)
            assertEquals(Color(palette.secondary), scheme.secondary)
            assertEquals(Color(palette.secondary), scheme.tertiary)
            assertEquals(Color(palette.edge), scheme.outline)
            assertEquals(Color(palette.edge), scheme.outlineVariant)
            assertEquals(Color(palette.error), scheme.error)
        }
    }

    private fun assertContrast(foreground: Int, background: Int, minimum: Double) {
        val light = luminance(foreground)
        val dark = luminance(background)
        val ratio = (maxOf(light, dark) + 0.05) / (minOf(light, dark) + 0.05)
        assertTrue("${foreground.toUInt().toString(16)} on ${background.toUInt().toString(16)}: $ratio < $minimum", ratio >= minimum)
    }

    private fun luminance(color: Int): Double {
        val channels = listOf(16, 8, 0).map { shift ->
            val value = ((color shr shift) and 255) / 255.0
            if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
    }
}
