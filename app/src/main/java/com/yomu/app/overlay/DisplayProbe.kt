package com.yomu.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import android.view.View
import android.view.WindowManager

/**
 * An invisible window over the whole display that reports the display's size in pixels to [onResized] each time it
 * changes, such as when the screen turns. Touches and focus pass through it to the app underneath.
 */
class DisplayProbe(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onResized: (Size) -> Unit
) {
    private var attachedView: View? = null

    /** The display's size as last reported, or null before the window's first layout and after [detach]. */
    var size: Size? = null
        private set

    /** Adds the probe window, first removing one already added; each layout at a new size then reports the display's size. */
    fun attach() {
        detach()
        val probeWindow = object : View(context) {
            override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
                // The system can still lay out a window removed after the layout was queued.
                if (attachedView !== this || width <= 0 || height <= 0) return
                val displaySize = displaySize(width, height)
                size = displaySize
                onResized(displaySize)
            }
        }
        attachedView = probeWindow
        windowManager.addView(probeWindow, layoutParams())
    }

    /** Removes the probe window, if one is added, and forgets the size; nothing is reported after this. */
    fun detach() {
        val probeWindow = attachedView ?: return
        attachedView = null
        size = null
        windowManager.removeView(probeWindow)
    }

    private fun displaySize(windowWidth: Int, windowHeight: Int): Size {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Size(windowWidth, windowHeight)
        // Before Android 11 an overlay window stops short of a visible navigation bar.
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Size(metrics.widthPixels, metrics.heightPixels)
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        // Android 12+ drops touches that pass through overlays which are too opaque together.
        alpha = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Fitted to system bars or a cutout, the window would be smaller than the display it measures.
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }
}
