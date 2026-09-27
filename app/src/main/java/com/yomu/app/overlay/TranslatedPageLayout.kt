package com.yomu.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot

internal open class TranslatedPageLayout(context: Context) : FrameLayout(context) {
    var card: View? = null
    var drawnBounds: List<OverlayBounds> = emptyList()
    var onGesture: (TranslatedPageGestures.Outcome) -> Unit = {}

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val gestures = TranslatedPageGestures(touchSlop)
    private var outcome: TranslatedPageGestures.Outcome = TranslatedPageGestures.Outcome.None
    private var downX = 0f
    private var downY = 0f
    private var canScrollCard = false
    private var scrollingCard = false
    private var directionChosen = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val openCard = card?.let {
            OverlayBounds(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
            canScrollCard = openCard?.contains(downX, downY) == true &&
                card?.let { it.canScrollVertically(-1) || it.canScrollVertically(1) } == true
            scrollingCard = false
            directionChosen = false
        }
        val dx = event.x - downX
        val dy = event.y - downY
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !directionChosen && hypot(dx, dy) > touchSlop) {
            scrollingCard = canScrollCard && abs(dy) > abs(dx)
            directionChosen = true
        }
        outcome = if (scrollingCard) TranslatedPageGestures.Outcome.None else
            gestures.onTouch(event.actionMasked, event.x, event.y, drawnBounds, openCard)
        if (outcome == TranslatedPageGestures.Outcome.DismissPage) requestDisallowInterceptTouchEvent(false)
        // Dispatch cancellation to a pressed child before the callback removes the window.
        val handled = super.dispatchTouchEvent(event)
        if (outcome != TranslatedPageGestures.Outcome.None) onGesture(outcome)
        return handled
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        outcome == TranslatedPageGestures.Outcome.DismissPage

    // The outcome callback invokes performClick after child dispatch for page dismissal.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = true
}
