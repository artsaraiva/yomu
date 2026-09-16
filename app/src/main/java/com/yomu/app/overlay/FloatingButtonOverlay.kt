package com.yomu.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager

class FloatingButtonOverlay(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var buttonView: FloatingButtonView? = null
    private var buttonParams: WindowManager.LayoutParams? = null

    fun show(
        initialX: Int,
        initialY: Int,
        onTap: () -> Unit,
        onDragEnd: (x: Int, y: Int) -> Unit,
        onLongPress: (() -> Unit)? = null
    ): FloatingButtonView {
        buttonView?.let { return it }

        val sizePx = (56 * context.resources.displayMetrics.density).toInt()
        val displayMetrics = context.resources.displayMetrics
        val available = overlayControlSize(context, windowManager)
        val margin = (8 * displayMetrics.density).toInt()
        val maxX = (available.x - sizePx - margin).coerceAtLeast(margin)
        val maxY = (available.y - sizePx - margin).coerceAtLeast(margin)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX.coerceIn(margin, maxX)
            y = initialY.coerceIn(margin, maxY)
        }

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        val gestureClassifier = TouchGestureClassifier(touchSlop)
        var startX = params.x
        var startY = params.y
        var downRawX = 0f
        var downRawY = 0f

        val view = FloatingButtonView(context).apply {
            setOnClickListener { onTap() }
            setOnTouchListener { touchedView, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        downRawX = event.rawX
                        downRawY = event.rawY
                        gestureClassifier.onActionDown(event.rawX, event.rawY)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (gestureClassifier.onActionMove(event.rawX, event.rawY)) {
                            val dx = (event.rawX - downRawX).toInt()
                            val dy = (event.rawY - downRawY).toInt()
                            val currentSize = overlayControlSize(context, windowManager)
                            params.x = (startX + dx).coerceIn(margin, (currentSize.x - sizePx - margin).coerceAtLeast(margin))
                            params.y = (startY + dy).coerceIn(margin, (currentSize.y - sizePx - margin).coerceAtLeast(margin))
                            windowManager.updateViewLayout(touchedView, params)
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        when (gestureClassifier.onActionUp(event.rawX, event.rawY)) {
                            TouchGestureClassifier.Gesture.TAP -> touchedView.performClick()
                            TouchGestureClassifier.Gesture.DRAG -> onDragEnd(params.x, params.y)
                            TouchGestureClassifier.Gesture.LONG_PRESS -> onLongPress?.invoke()
                        }
                    }
                }
                true
            }
        }

        buttonParams = params
        buttonView = view
        windowManager.addView(view, params)
        return view
    }

    fun keepInBounds() {
        val view = buttonView ?: return
        val params = buttonParams ?: return
        val available = overlayControlSize(context, windowManager)
        val margin = (8 * context.resources.displayMetrics.density).toInt()
        params.x = params.x.coerceIn(margin, (available.x - params.width - margin).coerceAtLeast(margin))
        params.y = params.y.coerceIn(margin, (available.y - params.height - margin).coerceAtLeast(margin))
        windowManager.updateViewLayout(view, params)
    }

    fun remove() {
        buttonView?.let { windowManager.removeView(it) }
        buttonView = null
        buttonParams = null
    }
}
