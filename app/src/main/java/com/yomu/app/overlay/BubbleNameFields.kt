package com.yomu.app.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.yomu.app.ui.theme.paperBackground
import com.yomu.app.ui.theme.paperColors

/**
 * The fields a bubble card swaps its texts for to add a name (#553), the same two as the glossary's own
 * dialog. [japanese] is the bubble's text, there to be cut down to the name. [save] returns false for a
 * name it refuses, which leaves the fields open; [onDone] runs after a save or a cancel.
 */
internal fun bubbleNameFields(context: Context, japanese: String, save: (term: String, rendering: String) -> Boolean, onDone: () -> Unit): View {
    val colors = context.paperColors()
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()
    fun field(text: String): EditText = EditText(context).apply {
        id = View.generateViewId()
        setText(text)
        textSize = 16f
        setSingleLine()
        minHeight = dp(48)
        setTextColor(colors.ink)
        backgroundTintList = ColorStateList.valueOf(colors.inkMuted)
    }
    fun button(label: String, action: () -> Unit): Button = Button(context).apply {
        text = label
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        minHeight = dp(48)
        stateListAnimator = null
        setPadding(dp(16), 0, dp(16), 0)
        setOnClickListener { action() }
    }
    val term = field(japanese)
    val rendering = field("")
    val saveButton = button("Save") { if (save(term.text.toString(), rendering.text.toString())) onDone() }.apply {
        background = context.paperBackground()
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(colors.inkMuted, colors.ink)))
    }
    val refreshSave = { saveButton.isEnabled = term.text.isNotBlank() && rendering.text.isNotBlank() }
    term.doAfterTextChanged { refreshSave() }
    rendering.doAfterTextChanged { refreshSave() }
    refreshSave()
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), dp(8), 0)
        addView(cardLabel(context, "Japanese").apply { labelFor = term.id })
        addView(term)
        addView(cardLabel(context, "Always translate as").apply { labelFor = rendering.id })
        addView(rendering)
        addView(cardRow(context).apply {
            addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
            addView(button("Cancel", onDone).apply {
                background = null
                setTextColor(colors.ink)
            })
            addView(saveButton)
        })
    }
}
