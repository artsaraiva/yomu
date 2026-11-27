package com.yomu.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.yomu.app.detection.BubblesFound
import com.yomu.app.detection.DetectionThresholdStore
import com.yomu.app.ui.theme.YomuColors
import com.yomu.app.ui.theme.paperBackground
import com.yomu.app.ui.theme.paperColors
import com.yomu.core.Constants

/**
 * The panel a long-press on the floating button opens (#551). It docks where [readingPanelFrame] puts it, so
 * the page stays visible, and runs top to bottom by how often a reader needs each row: act on this page,
 * tune how pages read, leave. Nothing in it is drawn in the accent, which belongs to the floating button.
 */
class ReadingPanel(
    private val context: Context,
    private val windowManager: WindowManager,
    private val modelName: () -> String,
    private val languageSwitch: OverlayLanguageSwitch,
    private val recallSwitch: OverlayRecallSwitch,
    private val onFontSizeChanged: (Float) -> Unit,
    private val onThresholdChanged: (Float) -> Unit,
    private val hasLastPage: () -> Boolean,
    private val onTranslateAgainRequested: () -> Unit,
    private val onReportRequested: () -> Unit,
    private val onOpenAppRequested: () -> Unit,
    private val onStopRequested: () -> Unit,
    private val blockTouchesBelow: (onRelease: () -> Unit) -> Unit,
    private val unblockTouchesBelow: () -> Unit
) {
    private var panelView: ScrollView? = null
    private val inkTexts = mutableListOf<TextView>()
    private val mutedTexts = mutableListOf<TextView>()
    private val shapedButtons = mutableListOf<Button>()
    private val lastPageButtons = mutableListOf<Button>()
    private val choiceButtons = mutableMapOf<BubblesFound, Button>()
    private var bubblesLabel: TextView? = null
    private var fontSizeLabel: TextView? = null
    private var smallerText: Button? = null
    private var largerText: Button? = null
    private var fontSizeScale = Constants.DEFAULT_FONT_SIZE_SCALE
    private var threshold = DetectionThresholdStore.DEFAULT
    private val density = context.resources.displayMetrics.density

    /** Opens the panel; [buttonX] is the floating button's left edge, which picks the side in landscape. */
    fun show(buttonX: Int) {
        if (panelView != null) return
        val available = overlayControlSize(context, windowManager)
        val content = ScrollView(context).apply {
            addView(createContentView())
            background = context.paperBackground()
            elevation = density
        }
        val frame = readingPanelFrame(available.x, available.y, buttonX, dp(8), dp(SIDE_WIDTH_DP)) { width ->
            content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            content.measuredHeight
        }
        val params = WindowManager.LayoutParams(
            frame.width, frame.height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = frame.x
            y = frame.y
        }
        panelView = content
        updateAppearance()
        blockTouchesBelow(::remove)
        windowManager.addView(content, params)
        content.alpha = 0f
        if (ValueAnimator.areAnimatorsEnabled()) content.translationY = dp(16).toFloat()
        content.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }

    fun remove() {
        panelView?.let {
            it.animate().cancel()
            if (it.isAttachedToWindow) windowManager.removeView(it)
        }
        panelView = null
        unblockTouchesBelow()
        inkTexts.clear()
        mutedTexts.clear()
        shapedButtons.clear()
        lastPageButtons.clear()
        choiceButtons.clear()
        bubblesLabel = null
        fontSizeLabel = null
        smallerText = null
        largerText = null
    }

    fun updateAppearance() {
        val colors = context.paperColors()
        panelView?.background = context.paperBackground()
        inkTexts.forEach { it.setTextColor(colors.ink) }
        mutedTexts.forEach { it.setTextColor(colors.inkMuted) }
        val dimmable = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(colors.inkMuted, colors.ink)
        )
        shapedButtons.forEach {
            it.setTextColor(dimmable)
            it.background = context.paperBackground()
        }
        languageSwitch.applyColors(colors)
        recallSwitch.applyColors(colors)
        refreshThreshold(colors)
    }

    fun updateFontSizeScale(scale: Float) {
        fontSizeScale = scale.coerceIn(0.5f, 2f)
        refreshFontSize()
    }

    fun refreshLastPageAvailability() = lastPageButtons.forEach { it.isEnabled = hasLastPage() }

    fun updateThreshold(value: Float) {
        threshold = DetectionThresholdStore.snap(value)
        refreshThreshold(context.paperColors())
    }

    private fun createContentView(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
        addView(row().apply {
            gravity = Gravity.TOP
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = weighted()
                addView(languageSwitch.createView())
                addView(TextView(context).apply {
                    text = modelName()
                    textSize = 13f
                    mutedTexts.add(this)
                })
            })
            addView(textButton("✕") { remove() }.apply { contentDescription = "Close reading panel" })
        })
        addView(row(topMargin = 12).apply {
            addView(lastPageButton("Translate again", onTranslateAgainRequested))
            addView(lastPageButton("Report page", onReportRequested).apply { (layoutParams as LinearLayout.LayoutParams).marginStart = dp(8) })
        })
        addView(row(topMargin = 16).apply {
            addView(label("Text size").apply { layoutParams = weighted() })
            addView(shapedButton("−") { stepFontSize(FontSizeSteps.smaller(fontSizeScale)) }.apply {
                contentDescription = "Smaller text"
                smallerText = this
            })
            addView(label("").apply {
                gravity = Gravity.CENTER
                minWidth = dp(64)
                fontSizeLabel = this
            })
            addView(shapedButton("+") { stepFontSize(FontSizeSteps.larger(fontSizeScale)) }.apply {
                contentDescription = "Larger text"
                largerText = this
            })
        })
        addView(label("").apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
            bubblesLabel = this
        })
        addView(row(topMargin = 8).apply {
            BubblesFound.entries.forEachIndexed { index, choice ->
                addView(Button(context).apply {
                    styleAsControl(choice.label) { chooseBubblesFound(choice) }
                    layoutParams = weighted().apply { if (index > 0) marginStart = dp(8) }
                    choiceButtons[choice] = this
                })
            }
        })
        addView(recallSwitch.createView())
        addView(row(topMargin = 8).apply {
            addView(textButton("Open Yomu") { remove(); onOpenAppRequested() })
            addView(View(context).apply { layoutParams = weighted() })
            addView(textButton("Stop reading", onStopRequested))
        })
        refreshFontSize()
        refreshLastPageAvailability()
    }

    private fun stepFontSize(scale: Float) {
        fontSizeScale = scale
        refreshFontSize()
        onFontSizeChanged(scale)
    }

    private fun chooseBubblesFound(choice: BubblesFound) {
        threshold = choice.threshold
        refreshThreshold(context.paperColors())
        onThresholdChanged(threshold)
    }

    private fun refreshFontSize() {
        fontSizeLabel?.text = FontSizeSteps.label(fontSizeScale)
        smallerText?.isEnabled = FontSizeSteps.smaller(fontSizeScale) < fontSizeScale
        largerText?.isEnabled = FontSizeSteps.larger(fontSizeScale) > fontSizeScale
    }

    /** The chosen one of the three is filled with ink; a threshold that matches none is named Custom and fills nothing. */
    private fun refreshThreshold(colors: YomuColors) {
        val chosen = BubblesFound.of(threshold)
        bubblesLabel?.text = if (chosen == null) "Bubbles found · Custom" else "Bubbles found"
        choiceButtons.forEach { (choice, button) ->
            button.isSelected = choice == chosen
            button.setTextColor(if (button.isSelected) colors.paperRaised else colors.ink)
            button.background = if (button.isSelected) {
                GradientDrawable().apply {
                    cornerRadius = 20f * density
                    setColor(colors.ink)
                }
            } else {
                context.paperBackground()
            }
        }
    }

    private fun lastPageButton(label: String, action: () -> Unit): Button = shapedButton(label) { remove(); action() }.apply {
        layoutParams = weighted()
        lastPageButtons.add(this)
    }

    private fun label(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 16f
        inkTexts.add(this)
    }

    /** A button with the paper outline, so it reads as one over any artwork. */
    private fun shapedButton(label: String, action: () -> Unit): Button = Button(context).apply {
        styleAsControl(label, action)
        shapedButtons.add(this)
    }

    private fun textButton(label: String, action: () -> Unit): Button = Button(context).apply {
        styleAsControl(label, action)
        background = null
        inkTexts.add(this)
    }

    private fun Button.styleAsControl(label: String, action: () -> Unit) {
        text = label
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        minWidth = dp(48)
        minimumWidth = dp(48)
        minHeight = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(12), 0, dp(12), 0)
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun row(topMargin: Int = 0): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { this.topMargin = dp(topMargin) }
    }

    private fun weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        /** The panel's width when it docks to a side in landscape. */
        const val SIDE_WIDTH_DP = 360
    }
}
