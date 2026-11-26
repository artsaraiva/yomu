package com.yomu.app.overlay

import android.content.Context
import com.yomu.app.ui.theme.paperColors
import com.yomu.app.ui.theme.paperBackground
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.yomu.pipeline.TranslationPipeline

class TranslationStatusOverlay(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var statusView: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    /** Shows [message]; with [onTap], the line takes touches and runs it when tapped, else touches pass through to the app below. */
    fun showOrUpdate(message: String, onTap: (() -> Unit)? = null) {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (onTap == null) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0)
        if (statusView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (24 * context.resources.displayMetrics.density).toInt()
            }

            statusView = TextView(context).apply {
                text = message
                setTextColor(context.paperColors().ink)
                background = context.paperBackground()
                maxWidth = (overlayControlSize(context, windowManager).x - 32 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
                accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
                textSize = 14f
                val horizontalPadding = (12 * context.resources.displayMetrics.density).toInt()
                val verticalPadding = (8 * context.resources.displayMetrics.density).toInt()
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                setTapAction(onTap)
            }
            this.params = params
            windowManager.addView(statusView, params)
            return
        }

        statusView?.apply {
            text = message
            setTapAction(onTap)
        }
        params?.takeIf { it.flags != flags }?.let {
            it.flags = flags
            windowManager.updateViewLayout(statusView, it)
        }
    }

    private fun TextView.setTapAction(onTap: (() -> Unit)?) {
        setOnClickListener(onTap?.let { tap -> android.view.View.OnClickListener { tap() } })
        isClickable = onTap != null
    }

    fun updateAppearance() {
        statusView?.apply {
            setTextColor(context.paperColors().ink)
            background = context.paperBackground()
        }
    }

    fun remove() {
        statusView?.let { windowManager.removeView(it) }
        statusView = null
        params = null
    }

    /** Returns the status view’s bounds in screen pixels, or null when no status view is shown. */
    fun boundsOnScreen(): OverlayBounds? = statusView?.boundsOnScreen()
}

/**
 * The reader-facing status message for a pipeline stage. While [onServer], the translation stage is marked,
 * since that is when the page's text is sent to the reader's server (ADR-0023); every other stage runs on the phone.
 */
fun stageMessage(stage: TranslationPipeline.Stage, onServer: Boolean): String = when (stage) {
    TranslationPipeline.Stage.BUBBLE_DETECTION -> "Finding text bubbles"
    TranslationPipeline.Stage.OCR -> "Reading Japanese text"
    TranslationPipeline.Stage.CONTEXT_ASSEMBLY -> "Preparing context"
    TranslationPipeline.Stage.TRANSLATION -> if (onServer) "Translating on your server" else "Translating"
    TranslationPipeline.Stage.DONE -> "Drawing translation"
    TranslationPipeline.Stage.ERROR -> "Failed"
}
