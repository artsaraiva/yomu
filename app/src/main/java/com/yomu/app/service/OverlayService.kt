package com.yomu.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.yomu.app.capture.ScreenCaptureManager
import com.yomu.core.Constants
import com.yomu.pipeline.TranslationPipeline
import com.yomu.pipeline.typesetting.TypesetBubble
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service() {

    @Inject lateinit var screenCaptureManager: ScreenCaptureManager
    @Inject lateinit var translationPipeline: TranslationPipeline

    private lateinit var windowManager: WindowManager
    private var floatingButton: FrameLayout? = null
    private var overlayView: FrameLayout? = null

    private var isTranslating = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val EXTRA_MEDIA_PROJECTION_DATA = "media_projection_data"
        const val EXTRA_RESULT_CODE = "result_code"
        private const val CHANNEL_NAME = "Yomu Overlay"
        private const val CHANNEL_DESC = "Yomu translation overlay service"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(Constants.OVERLAY_NOTIFICATION_ID, notification)

        val data = intent?.getParcelableExtra<Intent>(EXTRA_MEDIA_PROJECTION_DATA)
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        if (data != null && resultCode == android.app.Activity.RESULT_OK) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data)
            if (projection != null) {
                screenCaptureManager.startProjection(projection)
            }
        }

        if (Settings.canDrawOverlays(this)) {
            showFloatingButton()
        } else {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeFloatingButton()
        removeOverlay()
        screenCaptureManager.stopProjection()
        scope.run { kotlinx.coroutines.Job().cancel() }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.OVERLAY_CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, Constants.OVERLAY_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Yomu")
            .setContentText("Tap the floating button to translate manga")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingButton() {
        if (floatingButton != null) return

        val params = WindowManager.LayoutParams(
            56, 56,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        floatingButton = FrameLayout(this).apply {
            setBackgroundColor(0xFFFF5722.toInt())

            setOnClickListener {
                if (!isTranslating) {
                    startTranslation()
                }
            }
        }

        windowManager.addView(floatingButton, params)
    }

    private fun removeFloatingButton() {
        floatingButton?.let { windowManager.removeView(it) }
        floatingButton = null
    }

    private fun startTranslation() {
        if (isTranslating) return
        isTranslating = true

        scope.launch {
            screenCaptureManager.captureScreen { bitmap ->
                if (bitmap != null) {
                    val result = translationPipeline.processPage(bitmap)
                    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main) {
                        if (result != null) {
                            showTranslationOverlay(result.typesetBubbles)
                        }
                        isTranslating = false
                    }
                } else {
                    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main) {
                        isTranslating = false
                    }
                }
            }
        }
    }

    private fun showTranslationOverlay(bubbles: List<TypesetBubble>) {
        removeOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        )

        overlayView = object : FrameLayout(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                for (bubble in bubbles) {
                    drawBubble(canvas, bubble, paint)
                }
            }
        }.apply {
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { removeOverlay() }
        }

        windowManager.addView(overlayView, params)
    }

    private fun drawBubble(canvas: Canvas, bubble: TypesetBubble, paint: Paint) {
        val bx = bubble.boundingBox[0]
        val by = bubble.boundingBox[1]
        val bw = bubble.boundingBox[2] - bubble.boundingBox[0]
        val bh = bubble.boundingBox[3] - bubble.boundingBox[1]

        paint.color = bubble.backgroundColor
        paint.isAntiAlias = true
        val radius = 8f
        canvas.drawRoundRect(bx, by, bx + bw, by + bh, radius, radius, paint)

        paint.color = bubble.textColor
        paint.textSize = bubble.fontSize * resources.displayMetrics.density
        paint.typeface = Typeface.DEFAULT

        var textY = by + paint.textSize + 8f
        for (line in bubble.textLines) {
            val textX = bx + 8f
            canvas.drawText(line, textX, textY, paint)
            textY += paint.fontSpacing * 1.3f
        }
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }
}
