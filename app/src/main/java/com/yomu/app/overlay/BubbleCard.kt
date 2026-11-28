package com.yomu.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import com.yomu.app.R
import com.yomu.app.ui.theme.paperBackground
import com.yomu.app.ui.theme.paperColors
import com.yomu.core.TargetLanguage
import com.yomu.pipeline.typesetting.TypesetBubble

/** What the page a bubble card opens on does for it. */
internal class BubbleCardHost(
    /** Saves a name to the glossary; false when it was refused. */
    val saveName: (term: String, rendering: String) -> Boolean,
    /** Tells the page whether the card's fields need the keyboard. */
    val setTyping: (Boolean) -> Unit,
    val close: () -> Unit
)

/**
 * The card a tapped bubble opens (#553): both texts with a copy each, and the way to add a name from the
 * bubble without leaving the manga. [language] is the one the page was translated into.
 */
internal fun bubbleCard(context: Context, bubble: TypesetBubble, atTop: Boolean, language: TargetLanguage, host: BubbleCardHost): View {
    val density = context.resources.displayMetrics.density
    val margin = (8 * density).toInt()
    return ScrollView(context).apply {
        background = context.paperBackground()
        elevation = density
        setPadding(2 * margin, margin, margin, 2 * margin)
        lateinit var texts: View
        fun show(content: View, typing: Boolean) {
            removeAllViews()
            addView(content)
            host.setTyping(typing)
            if (typing) content.requestFocus()
        }
        texts = bubbleTexts(context, bubble, language.promptName, host.close) {
            show(bubbleNameFields(context, bubble.originalText, host.saveName) { show(texts, typing = false) }, typing = true)
        }
        addView(texts)
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or if (atTop) Gravity.TOP else Gravity.BOTTOM
        ).apply { setMargins(margin, margin, margin, margin) }
    }
}

private fun bubbleTexts(context: Context, bubble: TypesetBubble, language: String, onClose: () -> Unit, onAddName: () -> Unit): View {
    val colors = context.paperColors()
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()
    fun textRow(label: String, text: String, size: Float): LinearLayout = cardRow(context).apply {
        addView(TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(colors.ink)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        })
        addView(iconButton(context, R.drawable.ic_copy, "Copy $label") { copyToClipboard(context, label, text) })
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(cardRow(context).apply {
            addView(cardLabel(context, "Japanese").apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(iconButton(context, R.drawable.ic_close, "Close", onClose))
        })
        addView(textRow("Japanese", bubble.originalText, 20f))
        addView(View(context).apply {
            setBackgroundColor(colors.edge)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(0, dp(8), dp(8), dp(12)) }
        })
        addView(cardLabel(context, language))
        addView(textRow(language, bubble.translatedText, 16f))
        addView(cardRow(context).apply {
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            background = context.paperBackground()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(12), dp(8), 0) }
            setOnClickListener { onAddName() }
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_glossary)
                imageTintList = ColorStateList.valueOf(colors.ink)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) }
            })
            addView(TextView(context).apply {
                text = "Add a name from this bubble"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colors.ink)
            })
        })
    }
}

internal fun cardRow(context: Context): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

/** The small capital label above a text or a field. */
internal fun cardLabel(context: Context, label: String): TextView = TextView(context).apply {
    text = label
    textSize = 12f
    typeface = Typeface.DEFAULT_BOLD
    isAllCaps = true
    letterSpacing = 0.07f
    setTextColor(context.paperColors().inkMuted)
}

private fun iconButton(context: Context, @DrawableRes icon: Int, description: String, action: () -> Unit): ImageButton =
    ImageButton(context).apply {
        val size = (48 * context.resources.displayMetrics.density).toInt()
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(context.paperColors().ink)
        contentDescription = description
        background = null
        layoutParams = LinearLayout.LayoutParams(size, size)
        setOnClickListener { action() }
    }

private fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13 and later confirm a copy themselves.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}
