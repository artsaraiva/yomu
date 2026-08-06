package com.yomu.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.yomu.app.translation.TranslationEngineType

class QuickSettingsPopup(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onEngineSelected: (TranslationEngineType) -> Unit,
    private val onFontSizeChanged: (Float) -> Unit,
    private val onStopRequested: () -> Unit
) {
    private var popupView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private val engineButtons = mutableMapOf<TranslationEngineType, Button>()
    private var fontSizeSeekBar: SeekBar? = null
    private var selectedEngine: TranslationEngineType = TranslationEngineType.ML_KIT

    private val density = context.resources.displayMetrics.density
    private val popupWidthPx = (POPUP_WIDTH_DP * density).toInt()

    fun show(anchorX: Int, anchorY: Int) {
        if (popupView != null) return

        val content = createContentView()
        popupView = content

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        val layoutParams = WindowManager.LayoutParams(
            popupWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (anchorX + POPUP_X_OFFSET_DP * density).toInt()
            y = (anchorY + POPUP_Y_OFFSET_DP * density).toInt()
        }
        params = layoutParams

        windowManager.addView(content, layoutParams)
    }

    fun remove() {
        popupView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
        }
        popupView = null
        params = null
    }

    fun updateEngineSelection(type: TranslationEngineType) {
        selectedEngine = type
        engineButtons.forEach { (engine, button) ->
            button.setTextColor(if (engine == type) SELECTED_ENGINE_COLOR else DEFAULT_TEXT_COLOR)
        }
    }

    fun updateFontSizeScale(scale: Float) {
        fontSizeSeekBar?.let {
            it.progress = scaleToProgress(scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE))
        }
    }

    private fun createContentView(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            background = createBackground()
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    remove()
                    true
                } else {
                    false
                }
            }
        }

        val header = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(context).apply {
            text = "Quick settings"
            setTextColor(DEFAULT_TEXT_COLOR)
            textSize = 16f
            setPadding(0, 0, 0, dpToPx(12))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        }
        header.addView(title)

        val closeButton = Button(context).apply {
            text = "✕"
            textSize = 14f
            setTextColor(DEFAULT_TEXT_COLOR)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            minWidth = 0
            minimumWidth = 0
            setPadding(dpToPx(8), 0, dpToPx(8), dpToPx(8))
            setOnClickListener { remove() }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.TOP
            )
        }
        header.addView(closeButton)
        container.addView(header)

        container.addView(createEngineSelector())

        val fontLabel = TextView(context).apply {
            text = "Font size"
            setTextColor(DEFAULT_TEXT_COLOR)
            textSize = 14f
            setPadding(0, dpToPx(16), 0, dpToPx(8))
        }
        container.addView(fontLabel)

        container.addView(createFontSizeSlider())

        val stopButton = Button(context).apply {
            text = "Stop service"
            setTextColor(STOP_BUTTON_COLOR)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(0, dpToPx(12), 0, 0)
            setOnClickListener { onStopRequested() }
        }
        container.addView(stopButton)

        return container
    }

    private fun createEngineSelector(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        TranslationEngineType.entries.forEach { engine ->
            val button = Button(context).apply {
                text = engine.label
                textSize = 12f
                setTextColor(if (engine == selectedEngine) SELECTED_ENGINE_COLOR else DEFAULT_TEXT_COLOR)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener {
                    onEngineSelected(engine)
                }
            }
            engineButtons[engine] = button
            row.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        return row
    }

    private fun createFontSizeSlider(): SeekBar {
        return SeekBar(context).apply {
            max = FONT_SIZE_STEPS
            progress = DEFAULT_FONT_SIZE_PROGRESS
            fontSizeSeekBar = this
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        onFontSizeChanged(progressToScale(progress))
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
    }

    private fun createBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = CORNER_RADIUS_DP * density
            setColor(POPUP_BACKGROUND_COLOR)
        }
    }

    private fun progressToScale(progress: Int): Float {
        return MIN_FONT_SCALE + (progress.toFloat() / FONT_SIZE_STEPS) * (MAX_FONT_SCALE - MIN_FONT_SCALE)
    }

    private fun scaleToProgress(scale: Float): Int {
        return ((scale - MIN_FONT_SCALE) / (MAX_FONT_SCALE - MIN_FONT_SCALE) * FONT_SIZE_STEPS).toInt()
    }

    private fun dpToPx(dp: Int): Int = (dp * density).toInt()

    companion object {
        private const val POPUP_WIDTH_DP = 280
        private const val POPUP_X_OFFSET_DP = 16
        private const val POPUP_Y_OFFSET_DP = 16
        private const val CORNER_RADIUS_DP = 16f
        private const val MIN_FONT_SCALE = 0.5f
        private const val MAX_FONT_SCALE = 2.0f
        private const val FONT_SIZE_STEPS = 100
        private const val DEFAULT_FONT_SIZE_PROGRESS = 50
        private const val POPUP_BACKGROUND_COLOR = 0xDD222222.toInt()
        private const val DEFAULT_TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val SELECTED_ENGINE_COLOR = 0xFF4CAF50.toInt()
        private const val STOP_BUTTON_COLOR = 0xFFFF5252.toInt()

    }
}
