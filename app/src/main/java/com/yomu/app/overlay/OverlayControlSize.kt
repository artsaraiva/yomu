package com.yomu.app.overlay

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/** Returns this view’s bounds in screen pixels for excluding overlays from page fingerprints. */
internal fun View.boundsOnScreen(): OverlayBounds {
    val at = IntArray(2).also(::getLocationOnScreen)
    return OverlayBounds(at[0].toFloat(), at[1].toFloat(), (at[0] + width).toFloat(), (at[1] + height).toFloat())
}

internal fun overlayControlSize(context: Context, windowManager: WindowManager): Point {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return Point(metrics.bounds.width() - insets.left - insets.right, metrics.bounds.height() - insets.top - insets.bottom)
    }
    val metrics = context.resources.displayMetrics
    return Point(metrics.widthPixels, metrics.heightPixels)
}
