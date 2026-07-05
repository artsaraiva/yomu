package com.yomu.app.overlay

import android.content.Context
import android.graphics.Color
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

    fun showOrUpdate(message: String) {
        if (statusView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (24 * context.resources.displayMetrics.density).toInt()
            }

            statusView = TextView(context).apply {
                text = message
                setTextColor(Color.WHITE)
                setBackgroundColor(0xCC000000.toInt())
                textSize = 14f
                val horizontalPadding = (12 * context.resources.displayMetrics.density).toInt()
                val verticalPadding = (8 * context.resources.displayMetrics.density).toInt()
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            }
            windowManager.addView(statusView, params)
            return
        }

        statusView?.text = message
    }

    fun remove() {
        statusView?.let { windowManager.removeView(it) }
        statusView = null
    }

    fun messageForStage(stage: TranslationPipeline.Stage): String {
        return when (stage) {
            TranslationPipeline.Stage.BUBBLE_DETECTION -> "Finding text bubbles"
            TranslationPipeline.Stage.OCR -> "Reading Japanese text"
            TranslationPipeline.Stage.CONTEXT_ASSEMBLY -> "Preparing context"
            TranslationPipeline.Stage.TRANSLATION -> "Translating"
            TranslationPipeline.Stage.TYPESETTING -> "Drawing translation"
            TranslationPipeline.Stage.DONE -> "Drawing translation"
            TranslationPipeline.Stage.ERROR -> "Failed"
        }
    }
}
