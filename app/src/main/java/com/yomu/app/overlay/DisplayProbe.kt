package com.yomu.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import android.view.View
import android.view.WindowManager

/**
 * An invisible window over the whole display, laid out again each time the display changes shape, such as when the
 * screen turns. From Android 11 its size is the display's: sizes a service can ask for directly (window metrics, the
 * display's real size) kept the old shape after a turn on some phones (#450). Before Android 11 an overlay window stops
 * short of a visible navigation bar, so each layout reports the display's real size instead, which follows a turn on
 * those versions. [onResized] hears each new size while the window is attached.
 */
class DisplayProbe(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onResized: (Size) -> Unit
) {
    private var view: View? = null

    /** The display's size at the window's last layout, or null while detached or before its first layout. */
    var size: Size? = null
        private set

    /** Adds the window, replacing one already added; its first layout reports the display's size. */
    fun attach() {
        detach()
        view = object : View(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                // A layout still queued when the window was removed is not reported.
                if (this@DisplayProbe.view !== this || w <= 0 || h <= 0) return
                val measured = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Size(w, h) else realDisplaySize()
                size = measured
                onResized(measured)
            }
        }.also { windowManager.addView(it, layoutParams()) }
    }

    /** Removes the window and forgets the size, so nothing is reported afterwards. */
    fun detach() {
        val attached = view ?: return
        view = null
        size = null
        windowManager.removeView(attached)
    }

    @Suppress("DEPRECATION")
    private fun realDisplaySize(): Size {
        val metrics = DisplayMetrics()
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
        // Android 12+ drops touches that pass through overlays too opaque together; at alpha 0 this one adds nothing.
        alpha = 0f
        // It measures the display, not the room left for content, so bars and cutouts must not shrink it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }
}
