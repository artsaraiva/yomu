package com.yomu.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.yomu.app.ui.theme.paperBackground
import com.yomu.app.ui.theme.paperColors
import com.yomu.pipeline.typesetting.TypesetBubble

/** Sits in the half of the page away from the bubble, so the reader still sees what they tapped. */
internal fun bubbleCard(context: Context, bubble: TypesetBubble, atTop: Boolean): View {
    val density = context.resources.displayMetrics.density
    val margin = (16 * density).toInt()
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = context.paperBackground()
        elevation = density
        setPadding(margin, margin / 2, margin, margin)
        // Taps on the card's text must not reach the page, which would close the card.
        isClickable = true
        addCardSection(context, "Japanese", bubble.originalText, 18f)
        addCardSection(context, "English", bubble.translatedText, 16f)
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or if (atTop) Gravity.TOP else Gravity.BOTTOM
        ).apply { setMargins(margin, margin, margin, margin) }
    }
}

private fun LinearLayout.addCardSection(context: Context, label: String, text: String, textSize: Float) {
    val colors = context.paperColors()
    val density = context.resources.displayMetrics.density
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            this.text = label
            this.textSize = 13f
            setTextColor(colors.inkMuted)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(Button(context).apply {
            this.text = "Copy"
            contentDescription = "Copy $label"
            this.textSize = 14f
            isAllCaps = false
            minHeight = (48 * density).toInt()
            background = null
            stateListAnimator = null
            setTextColor(colors.accent)
            setOnClickListener { copyToClipboard(context, label, text) }
        })
    })
    addView(TextView(context).apply {
        this.text = text
        this.textSize = textSize
        setTextColor(colors.ink)
    })
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13 and later confirm a copy themselves.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}
