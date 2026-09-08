package com.yomu.app.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import com.yomu.core.Constants

fun Context.paperColors(): YomuColors {
    val preference = getSharedPreferences("yomu_prefs", Context.MODE_PRIVATE).getString(Constants.PREF_THEME, "system") ?: "system"
    val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    return if (resolveThemeMode(preference, systemDark) == ThemeMode.Night) NightPaper else DayPaper
}

fun Context.paperBackground(): GradientDrawable = GradientDrawable().apply {
    val colors = paperColors()
    cornerRadius = 20f * resources.displayMetrics.density
    setColor(colors.paperRaised)
    setStroke((resources.displayMetrics.density * 2).toInt().coerceAtLeast(1), colors.inkMuted)
}
