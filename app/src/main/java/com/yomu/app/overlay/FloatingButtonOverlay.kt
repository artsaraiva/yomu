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

    fun show(
        initialX: Int,
        initialY: Int,
        onTap: () -> Unit,
        onDragEnd: (x: Int, y: Int) -> Unit
    ): FloatingButtonView {
        buttonView?.let { return it }

        val sizePx = (56 * context.resources.displayMetrics.density).toInt()
        val displayMetrics = context.resources.displayMetrics
        val maxX = (displayMetrics.widthPixels - sizePx).coerceAtLeast(0)
        val maxY = (displayMetrics.heightPixels - sizePx).coerceAtLeast(0)

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
            x = initialX.coerceIn(0, maxX)
            y = initialY.coerceIn(0, maxY)
        }

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        val gestureClassifier = TouchGestureClassifier(touchSlop)
        var startX = params.x
        var startY = params.y
        var downRawX = 0f
        var downRawY = 0f

        val view = FloatingButtonView(context).apply {
            setOnClickListener {
                if (currentState == FloatingButtonView.State.IDLE) {
                    onTap()
                }
            }
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
                            params.x = (startX + dx).coerceIn(0, maxX)
                            params.y = (startY + dy).coerceIn(0, maxY)
                            windowManager.updateViewLayout(touchedView, params)
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        when (gestureClassifier.onActionUp(event.rawX, event.rawY)) {
                            TouchGestureClassifier.Gesture.TAP -> touchedView.performClick()
                            TouchGestureClassifier.Gesture.DRAG -> onDragEnd(params.x, params.y)
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> Unit
                }
                true
            }
        }

        buttonView = view
        windowManager.addView(view, params)
        return view
    }

    fun remove() {
        buttonView?.let { windowManager.removeView(it) }
        buttonView = null
    }
}
