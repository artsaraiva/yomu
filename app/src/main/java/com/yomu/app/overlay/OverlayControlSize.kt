package com.yomu.app.overlay

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager

internal fun overlayControlSize(context: Context, windowManager: WindowManager): Point {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return Point(metrics.bounds.width() - insets.left - insets.right, metrics.bounds.height() - insets.top - insets.bottom)
    }
    val metrics = context.resources.displayMetrics
    return Point(metrics.widthPixels, metrics.heightPixels)
}
