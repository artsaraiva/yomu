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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.yomu.app.capture.ScreenCaptureManager
import com.yomu.app.overlay.FloatingButtonView
import com.yomu.core.Constants
import com.yomu.pipeline.ModelPaths
import com.yomu.pipeline.TranslationPipeline
import com.yomu.pipeline.typesetting.TypesetBubble
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service() {

    @Inject lateinit var screenCaptureManager: ScreenCaptureManager
    @Inject lateinit var translationPipeline: TranslationPipeline

    private lateinit var windowManager: WindowManager
    private var floatingButton: FloatingButtonView? = null
    private var overlayView: FrameLayout? = null

    @Volatile
    private var isTranslating = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val ACTION_SERVICE_STARTED = "com.yomu.app.SERVICE_STARTED"
        const val ACTION_SERVICE_STOPPED = "com.yomu.app.SERVICE_STOPPED"
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
        translationPipeline.modelPaths = ModelPaths(
            bubbleDetectionPath = File(filesDir, "${Constants.MODELS_DIR}/${Constants.VISION_MODELS_DIR}/${Constants.BUBBLE_DETECTION_MODEL}").absolutePath,
            ocrEncoderPath = File(filesDir, "${Constants.MODELS_DIR}/${Constants.VISION_MODELS_DIR}/${Constants.OCR_ENCODER_MODEL}").absolutePath,
            ocrDecoderPath = File(filesDir, "${Constants.MODELS_DIR}/${Constants.VISION_MODELS_DIR}/${Constants.OCR_DECODER_MODEL}").absolutePath,
            ocrVocabPath = File(filesDir, "${Constants.MODELS_DIR}/${Constants.VISION_MODELS_DIR}/${Constants.OCR_VOCAB_FILE}").absolutePath,
            translationPath = File(filesDir, "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}/${Constants.TRANSLATION_MODEL_4BIT}").absolutePath
        )
        sendBroadcast(Intent(ACTION_SERVICE_STARTED).setPackage(packageName))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.OVERLAY_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(Constants.OVERLAY_NOTIFICATION_ID, notification)
        }

        val data = intent?.getParcelableExtra<Intent>(EXTRA_MEDIA_PROJECTION_DATA)
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        if (data != null && resultCode == android.app.Activity.RESULT_OK) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data)
            if (projection != null) {
                screenCaptureManager.startProjection(projection)
            }
        }

        showFloatingButton()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sendBroadcast(Intent(ACTION_SERVICE_STOPPED).setPackage(packageName))
        removeFloatingButton()
        removeOverlay()
        screenCaptureManager.stopProjection()
        scope.cancel()
        mainScope.cancel()
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

        val sizePx = (56 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
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

        floatingButton = FloatingButtonView(this).apply {
            setOnClickListener {
                if (currentState == FloatingButtonView.State.IDLE) {
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
        floatingButton?.setState(FloatingButtonView.State.TRANSLATING)

        scope.launch(Dispatchers.IO) {
            screenCaptureManager.captureScreen { bitmap ->
                scope.launch(Dispatchers.IO) {
                    val result = bitmap?.let { translationPipeline.processPage(it) }
                    mainScope.launch {
                        if (result != null) {
                            showTranslationOverlay(result.typesetBubbles)
                        } else {
                            showTranslationFailedToast()
                        }
                        isTranslating = false
                        floatingButton?.setState(FloatingButtonView.State.IDLE)
                    }
                }
            }
        }
    }

    private fun showTranslationFailedToast() {
        Toast.makeText(applicationContext, "Translation failed", Toast.LENGTH_SHORT).show()
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
