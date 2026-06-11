package com.yomu.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.yomu.app.capture.ScreenCaptureManager
import com.yomu.app.detection.MangaDetector
import com.yomu.core.Constants
import com.yomu.pipeline.TranslationPipeline
import com.yomu.pipeline.typesetting.TypesetBubble
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service() {

    @Inject lateinit var screenCaptureManager: ScreenCaptureManager
    @Inject lateinit var mangaDetector: MangaDetector
    @Inject lateinit var translationPipeline: TranslationPipeline

    private lateinit var windowManager: WindowManager
    private var floatingButton: View? = null
    private var overlayView: FrameLayout? = null
    private var currentTypesetBubbles: List<TypesetBubble> = emptyList()

    private var isShowingOverlay = false
    private var isTranslating = false

    companion object {
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
        showFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(Constants.OVERLAY_NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeFloatingButton()
        removeOverlay()
        screenCaptureManager.stopProjection()
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

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
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

        floatingButton = View(this).apply {
            setBackgroundColor(Color(0xFF, 0x57, 0x22).toArgb())
            val size = 56
            layoutParams = FrameLayout.LayoutParams(size, size)
            setOnClickListener {
                if (!isTranslating) {
                    startTranslation()
                }
            }

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        params.x = (event.rawX - size / 2).toInt()
                        params.y = (event.rawY - size / 2).toInt()
                        windowManager.updateViewLayout(this, params)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = (event.rawX - size / 2).toInt()
                        params.y = (event.rawY - size / 2).toInt()
                        windowManager.updateViewLayout(this, params)
                    }
                }
                true
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

        kotlinx.coroutines.MainScope().launch(Dispatchers.Default) {
            val bitmap = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<android.graphics.Bitmap?> { cont ->
                    screenCaptureManager.captureScreen { bitmap ->
                        cont.resume(bitmap)
                    }
                }
            }

            if (bitmap != null) {
                val result = translationPipeline.processPage(bitmap)
                withContext(Dispatchers.Main) {
                    if (result != null) {
                        showTranslationOverlay(result.typesetBubbles)
                    }
                    isTranslating = false
                }
            } else {
                withContext(Dispatchers.Main) {
                    isTranslating = false
                }
            }
        }
    }

    private fun showTranslationOverlay(bubbles: List<TypesetBubble>) {
        removeOverlay()
        currentTypesetBubbles = bubbles

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

        overlayView = FrameLayout(this).apply {
            setBackgroundColor(Color.Transparent.toArgb())
            setOnClickListener {
                removeOverlay()
            }
        }

        windowManager.addView(overlayView, params)
        isShowingOverlay = true
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        isShowingOverlay = false
        currentTypesetBubbles = emptyList()
    }
}
