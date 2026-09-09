package com.yomu.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.yomu.app.translation.TranslationEngineType
import com.yomu.app.ui.theme.paperBackground
import com.yomu.app.ui.theme.paperColors
import com.yomu.core.Constants

class QuickSettingsPopup(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onEngineSelected: (TranslationEngineType) -> Unit,
    private val onFontSizeChanged: (Float) -> Unit,
    private val onStopRequested: () -> Unit
) {
    private var popupView: ScrollView? = null
    private val engineButtons = mutableMapOf<TranslationEngineType, Button>()
    private val labels = mutableListOf<TextView>()
    private var fontSizeSeekBar: SeekBar? = null
    private var selectedEngine = TranslationEngineType.ML_KIT
    private var fontSizeScale = Constants.DEFAULT_FONT_SIZE_SCALE
    private val density = context.resources.displayMetrics.density

    fun show(anchorX: Int, anchorY: Int) {
        if (popupView != null) return
        val available = overlayControlSize(context, windowManager)
        val margin = dp(8)
        val width = dp(280).coerceAtMost((available.x - margin * 2).coerceAtLeast(1))
        val content = ScrollView(context).apply {
            addView(createContentView())
            background = context.paperBackground()
            elevation = density
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) { remove(); true } else false
            }
        }
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val height = content.measuredHeight.coerceAtMost((available.y - margin * 2).coerceAtLeast(1))
        val params = WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (anchorX + dp(16)).coerceIn(margin, (available.x - width - margin).coerceAtLeast(margin))
            y = (anchorY + dp(16)).coerceIn(margin, (available.y - height - margin).coerceAtLeast(margin))
        }
        popupView = content
        updateAppearance()
        windowManager.addView(content, params)
        content.alpha = 0f
        content.pivotX = (anchorX - params.x).toFloat().coerceIn(0f, width.toFloat())
        content.pivotY = (anchorY - params.y).toFloat().coerceIn(0f, height.toFloat())
        if (ValueAnimator.areAnimatorsEnabled()) { content.scaleX = 0.96f; content.scaleY = 0.96f }
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    fun remove() {
        popupView?.let {
            it.animate().cancel()
            if (it.isAttachedToWindow) windowManager.removeView(it)
        }
        popupView = null
        engineButtons.clear()
        labels.clear()
        fontSizeSeekBar = null
    }

    fun updateAppearance() {
        val colors = context.paperColors()
        popupView?.background = context.paperBackground()
        labels.forEach { it.setTextColor(colors.ink) }
        fontSizeSeekBar?.apply {
            thumbTintList = ColorStateList.valueOf(colors.accent)
            progressTintList = ColorStateList.valueOf(colors.accent)
            progressBackgroundTintList = ColorStateList.valueOf(colors.inkMuted)
        }
        updateEngineSelection(selectedEngine)
    }

    fun updateEngineSelection(type: TranslationEngineType) {
        selectedEngine = type
        val colors = context.paperColors()
        engineButtons.forEach { (engine, button) ->
            val label = context.getString(engine.labelRes)
            button.text = if (engine == type) "✓ $label" else label
            button.isSelected = engine == type
            button.setTextColor(colors.ink)
            button.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(colors.paperRaised)
                setStroke(dp(if (engine == type) 2 else 1), if (engine == type) colors.accent else colors.inkMuted)
            }
        }
    }

    fun updateFontSizeScale(scale: Float) {
        fontSizeScale = scale.coerceIn(0.5f, 2f)
        fontSizeSeekBar?.progress = ((fontSizeScale - 0.5f) / 1.5f * 100).toInt()
    }

    private fun createContentView(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(TextView(context).apply {
            text = "Quick settings"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            labels.add(this)
        })
        addView(actionButton("Close quick settings") { remove() })
        TranslationEngineType.entries.forEach { engine ->
            val button = actionButton(context.getString(engine.labelRes)) { onEngineSelected(engine) }
            engineButtons[engine] = button
            addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        addView(TextView(context).apply {
            text = "Font size"
            textSize = 14f
            setPadding(0, dp(16), 0, dp(8))
            labels.add(this)
        })
        addView(SeekBar(context).apply {
            max = 100
            progress = ((fontSizeScale - 0.5f) / 1.5f * 100).toInt()
            minimumHeight = dp(48)
            contentDescription = "Translation font size"
            fontSizeSeekBar = this
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        fontSizeScale = 0.5f + progress / 100f * 1.5f
                        onFontSizeChanged(fontSizeScale)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        addView(actionButton("Stop service", onStopRequested))
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(context).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        minHeight = dp(48)
        background = context.paperBackground()
        stateListAnimator = null
        labels.add(this)
        setOnClickListener { action() }
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
