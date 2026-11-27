package com.yomu.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager

class FloatingButtonOverlay(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var buttonView: FloatingButtonView? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var touchLayer: View? = null
    private var capsuleView: ProgressCapsuleView? = null
    private var capsuleParams: WindowManager.LayoutParams? = null
    private var progress: Pair<String, Int>? = null
    private val margin = (8 * context.resources.displayMetrics.density).toInt()

    fun show(
        initialX: Int,
        initialY: Int,
        onTap: () -> Unit,
        onDragEnd: (x: Int, y: Int) -> Unit,
        onLongPress: (() -> Unit)? = null,
        onPress: (() -> Unit)? = null
    ): FloatingButtonView {
        buttonView?.let { return it }

        val sizePx = (56 * context.resources.displayMetrics.density).toInt()
        val available = overlayControlSize(context, windowManager)
        val maxX = (available.x - sizePx - margin).coerceAtLeast(margin)
        val maxY = (available.y - sizePx - margin).coerceAtLeast(margin)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
                        onPress?.invoke()
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (gestureClassifier.onActionMove(event.rawX, event.rawY)) {
                            val dx = (event.rawX - downRawX).toInt()
                            val dy = (event.rawY - downRawY).toInt()
                            val currentSize = overlayControlSize(context, windowManager)
                            params.x = (startX + dx).coerceIn(margin, (currentSize.x - sizePx - margin).coerceAtLeast(margin))
                            params.y = (startY + dy).coerceIn(margin, (currentSize.y - sizePx - margin).coerceAtLeast(margin))
                            windowManager.updateViewLayout(touchedView, params)
                            placeCapsule()
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

        // Overlay windows stack in the order they are added; the layer and the capsule go first so the button stays above them.
        val layer = View(context).apply { visibility = View.GONE }
        windowManager.addView(layer, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Edge-to-edge apps take taps under the gesture bar and in cutouts too.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        })
        val capsule = ProgressCapsuleView(context)
        val capsuleLayout = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(capsule, capsuleLayout)
        capsuleView = capsule
        capsuleParams = capsuleLayout
        touchLayer = layer
        buttonParams = params
        buttonView = view
        windowManager.addView(view, params)
        return view
    }

    /** Swallows touches everywhere under the button, so they never reach the app below, and calls [onRelease] as each one ends. */
    @SuppressLint("ClickableViewAccessibility")
    fun blockTouchesBelow(onRelease: () -> Unit) {
        touchLayer?.apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) onRelease()
                true
            }
            visibility = View.VISIBLE
        }
    }

    fun unblockTouchesBelow() {
        touchLayer?.visibility = View.GONE
    }

    /**
     * Shows [message] as step [step] of the run in a capsule that grows out of the button to the side with room.
     * The capsule takes no touches: the button on its end stays the stop target.
     */
    fun showProgress(message: String, step: Int) {
        // The pipeline reports a stage once per bubble; only a new message needs a new layout.
        if (progress == message to step) return
        progress = message to step
        capsuleView?.show(message, step)
        placeCapsule()
    }

    fun hideProgress() {
        progress = null
        capsuleView?.hide()
    }

    /** Lays the shown capsule over the button where it is now; a hidden capsule waits for its next message. */
    private fun placeCapsule() {
        val capsule = capsuleView?.takeIf { it.visibility == View.VISIBLE } ?: return
        val layout = capsuleParams ?: return
        val button = buttonParams ?: return
        val placement = capsulePlacement(
            Point(button.x, button.y), button.width, overlayControlSize(context, windowManager), margin, capsule::sizeWithin
        )
        // Gravity.START counts x from the right edge in a right-to-left locale, which mirrors both windows.
        val mirrored = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        capsule.buttonAtRight = placement.growsLeft != mirrored
        layout.x = placement.x
        layout.y = placement.y
        layout.width = placement.width
        layout.height = placement.height
        windowManager.updateViewLayout(capsule, layout)
    }

    fun keepInBounds() {
        val view = buttonView ?: return
        val params = buttonParams ?: return
        val available = overlayControlSize(context, windowManager)
        params.x = params.x.coerceIn(margin, (available.x - params.width - margin).coerceAtLeast(margin))
        params.y = params.y.coerceIn(margin, (available.y - params.height - margin).coerceAtLeast(margin))
        windowManager.updateViewLayout(view, params)
        placeCapsule()
    }

    fun remove() {
        buttonView?.let { windowManager.removeView(it) }
        touchLayer?.let { windowManager.removeView(it) }
        capsuleView?.let { windowManager.removeView(it) }
        buttonView = null
        buttonParams = null
        touchLayer = null
        capsuleView = null
        capsuleParams = null
        progress = null
    }
}
