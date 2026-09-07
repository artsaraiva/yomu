package com.yomu.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.hypot
import com.yomu.app.ui.theme.paperColors

object CloseZoneGeometry {
    private const val BASE_RADIUS_DP = 120
    private const val SNAP_RADIUS_DP = 132
    private const val MAX_VIEW_RADIUS_DP = 140
    private const val BOTTOM_MARGIN_DP = 16
    private const val ACTIVATION_RADIUS_DP = 250
    private const val RELEASE_HIT_RADIUS_MULTIPLIER = 1.5f

    data class ZoneBounds(
        val centerX: Int,
        val centerY: Int,
        val radiusPx: Int
    )

    fun zoneBounds(screenWidth: Int, screenHeight: Int, density: Float): ZoneBounds {
        val radiusPx = (BASE_RADIUS_DP * density).toInt()
        val centerX = screenWidth / 2
        val centerY = screenHeight - (BOTTOM_MARGIN_DP * density).toInt() - radiusPx
        return ZoneBounds(centerX, centerY, radiusPx)
    }

    fun buttonCenter(buttonX: Int, buttonY: Int, buttonSize: Int): Pair<Int, Int> {
        return Pair(buttonX + buttonSize / 2, buttonY + buttonSize / 2)
    }

    fun isWithinZone(
        buttonCenterX: Int,
        buttonCenterY: Int,
        zoneBounds: ZoneBounds,
        radiusMultiplier: Float = 1f
    ): Boolean {
        val dx = buttonCenterX - zoneBounds.centerX
        val dy = buttonCenterY - zoneBounds.centerY
        return hypot(dx.toFloat(), dy.toFloat()) <= zoneBounds.radiusPx * radiusMultiplier
    }

    fun baseRadiusPx(density: Float): Int = (BASE_RADIUS_DP * density).toInt()
    fun snapRadiusPx(density: Float): Int = (SNAP_RADIUS_DP * density).toInt()
    fun maxViewRadiusPx(density: Float): Int = (MAX_VIEW_RADIUS_DP * density).toInt()
    fun activationRadiusPx(density: Float): Int = (ACTIVATION_RADIUS_DP * density).toInt()
    fun releaseHitRadiusMultiplier(): Float = RELEASE_HIT_RADIUS_MULTIPLIER
}

class CloseZoneOverlay(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var closeZoneView: CloseZoneView? = null
    private var zoneBounds: CloseZoneGeometry.ZoneBounds? = null

    fun show() {
        closeZoneView?.let { return }

        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val available = overlayControlSize(context, windowManager)
        val bounds = CloseZoneGeometry.zoneBounds(
            screenWidth = available.x,
            screenHeight = available.y,
            density = density
        )
        zoneBounds = bounds

        val maxViewRadiusPx = CloseZoneGeometry.maxViewRadiusPx(density)
        val viewSizePx = maxViewRadiusPx * 2

        val params = WindowManager.LayoutParams(
            viewSizePx,
            viewSizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.centerX - viewSizePx / 2
            y = bounds.centerY - viewSizePx / 2
        }

        val view = CloseZoneView(context).apply {
            updateAppearance(isWithin = false, proximity = 0f)
        }
        closeZoneView = view
        windowManager.addView(view, params)
    }

    fun updateAppearance() {
        closeZoneView?.invalidate()
    }

    fun remove() {
        closeZoneView?.let { windowManager.removeView(it) }
        closeZoneView = null
    }

    fun updateProximity(buttonX: Int, buttonY: Int, buttonSize: Int) {
        val bounds = zoneBounds ?: return
        val center = CloseZoneGeometry.buttonCenter(buttonX, buttonY, buttonSize)
        val distance = hypot(
            center.first - bounds.centerX.toFloat(),
            center.second - bounds.centerY.toFloat()
        )
        val isWithin = distance <= bounds.radiusPx
        val proximity = if (isWithin) {
            1f
        } else {
            val activationRadiusPx = CloseZoneGeometry.activationRadiusPx(context.resources.displayMetrics.density)
            val normalized = (activationRadiusPx - distance) / (activationRadiusPx - bounds.radiusPx)
            normalized.coerceIn(0f, 1f)
        }
        closeZoneView?.updateAppearance(isWithin, proximity)
    }

    fun isWithinZone(buttonX: Int, buttonY: Int, buttonSize: Int): Boolean {
        val bounds = zoneBounds ?: return false
        val center = CloseZoneGeometry.buttonCenter(buttonX, buttonY, buttonSize)
        return CloseZoneGeometry.isWithinZone(
            center.first,
            center.second,
            bounds,
            CloseZoneGeometry.releaseHitRadiusMultiplier()
        )
    }
}

class CloseZoneView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val baseRadiusPx = CloseZoneGeometry.baseRadiusPx(density).toFloat()
    private val snapRadiusPx = CloseZoneGeometry.snapRadiusPx(density).toFloat()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.paperColors().ink
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }

    private var currentRadius = baseRadiusPx
    private var withinZone = false

    fun updateAppearance(isWithin: Boolean, proximity: Float) {
        currentRadius = baseRadiusPx + proximity * (snapRadiusPx - baseRadiusPx)
        withinZone = isWithin
        contentDescription = if (isWithin) "Release to stop reading" else "Drag here to stop reading"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val colors = context.paperColors()

        backgroundPaint.color = colors.paperRaised
        backgroundPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, currentRadius, backgroundPaint)

        backgroundPaint.color = if (withinZone) colors.error else colors.inkMuted
        backgroundPaint.style = Paint.Style.STROKE
        backgroundPaint.strokeWidth = (if (withinZone) 4f else 2f) * density
        canvas.drawCircle(cx, cy, currentRadius - 2f * density, backgroundPaint)

        xPaint.color = colors.ink
        val crossRadius = currentRadius * 0.4f
        canvas.drawLine(cx - crossRadius, cy - crossRadius, cx + crossRadius, cy + crossRadius, xPaint)
        canvas.drawLine(cx - crossRadius, cy + crossRadius, cx + crossRadius, cy - crossRadius, xPaint)
    }

}
