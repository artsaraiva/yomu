package com.yomu.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.yomu.app.capture.ScreenCaptureManager
import com.yomu.app.detection.DetectionThresholdStore
import com.yomu.app.overlay.CloseZoneOverlay
import com.yomu.app.overlay.FloatingButtonView
import com.yomu.app.overlay.FloatingButtonOverlay
import com.yomu.app.overlay.OverlayBounds
import com.yomu.app.overlay.OverlayBubbleState
import com.yomu.app.overlay.QuickSettingsPopup
import com.yomu.app.overlay.TranslationRenderOverlay
import com.yomu.app.overlay.TranslationStatusOverlay
import com.yomu.app.db.entities.ModelType
import com.yomu.app.db.entities.TranslationSessionEntity
import com.yomu.core.Constants
import com.yomu.pipeline.ModelPaths
import com.yomu.pipeline.PipelineResult
import com.yomu.pipeline.TranslationPipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume

@AndroidEntryPoint
class OverlayService : Service() {

    @Inject lateinit var screenCaptureManager: ScreenCaptureManager
    @Inject lateinit var translationPipeline: TranslationPipeline
    @Inject lateinit var sharedPreferences: SharedPreferences
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var modelManager: ModelManager
    @Inject lateinit var readingSelection: ReadingModelSelection
    @Inject lateinit var slotSelection: ModelSlotSelection

    private var readingModelFiles: List<File> = emptyList()

    private lateinit var windowManager: WindowManager
    private lateinit var closeZoneOverlay: CloseZoneOverlay
    private lateinit var floatingButtonOverlay: FloatingButtonOverlay
    private lateinit var translationRenderOverlay: TranslationRenderOverlay
    private var floatingButton: FloatingButtonView? = null
    private lateinit var statusOverlay: TranslationStatusOverlay
    private var quickSettingsPopup: QuickSettingsPopup? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        when (key) {
            Constants.PREF_THEME -> updateOverlayAppearance()
            Constants.PREF_FONT_SIZE_SCALE -> translationPipeline.fontSizeScale = preferences.getFloat(
                key,
                Constants.DEFAULT_FONT_SIZE_SCALE
            )
            DetectionThresholdStore.KEY -> translationPipeline.confidenceThreshold = DetectionThresholdStore(preferences).load()
        }
    }

    private var buttonPositionX: Int = 0
    private var buttonPositionY: Int = 0

    private var translationJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_SERVICE_STARTED = "com.yomu.app.SERVICE_STARTED"
        const val ACTION_SERVICE_STOPPED = "com.yomu.app.SERVICE_STOPPED"
        const val ACTION_SERVICE_START_FAILED = "com.yomu.app.SERVICE_START_FAILED"
        const val EXTRA_MEDIA_PROJECTION_DATA = "media_projection_data"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_FAILURE_REASON = "failure_reason"
        private const val CHANNEL_NAME = "Yomu Overlay"
        private const val CHANNEL_DESC = "Yomu translation overlay service"
        private const val RELEASE_WAIT_MS = 2_000L

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
        closeZoneOverlay = CloseZoneOverlay(this, windowManager)
        floatingButtonOverlay = FloatingButtonOverlay(this, windowManager, closeZoneOverlay)
        translationRenderOverlay = TranslationRenderOverlay(this, windowManager)
        statusOverlay = TranslationStatusOverlay(this, windowManager)
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        createNotificationChannel()
        val detectionFiles = modelManager.modelFiles(readingSelection.selected(ModelType.DETECTION))
        val ocrFiles = modelManager.modelFiles(readingSelection.selected(ModelType.OCR))
        readingModelFiles = detectionFiles + ocrFiles
        translationPipeline.modelPaths = ModelPaths(
            bubbleDetectionPath = detectionFiles[0].absolutePath,
            ocrEncoderPath = ocrFiles[0].absolutePath,
            ocrDecoderPath = ocrFiles[1].absolutePath,
            ocrVocabPath = ocrFiles[2].absolutePath
        )
        translationPipeline.fontSizeScale = sharedPreferences.getFloat(
            Constants.PREF_FONT_SIZE_SCALE,
            Constants.DEFAULT_FONT_SIZE_SCALE
        )
        translationPipeline.confidenceThreshold = DetectionThresholdStore(sharedPreferences).load()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            notifyStartFailed("Overlay permission missing")
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
        if (data == null || resultCode != android.app.Activity.RESULT_OK) {
            notifyStartFailed("MediaProjection consent missing")
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, data)
        if (projection == null) {
            notifyStartFailed("MediaProjection initialization failed")
            stopSelf()
            return START_NOT_STICKY
        }

        screenCaptureManager.startProjection(projection)

        showFloatingButton()
        sendBroadcast(Intent(ACTION_SERVICE_STARTED).setPackage(packageName))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        removeQuickSettingsPopup()
        closeZoneOverlay.remove()
        floatingButtonOverlay.keepInBounds()
        updateOverlayAppearance()
    }

    private fun updateOverlayAppearance() {
        floatingButton?.updateAppearance()
        closeZoneOverlay.updateAppearance()
        quickSettingsPopup?.updateAppearance()
        statusOverlay.updateAppearance()
    }

    override fun onDestroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        translationJob?.let { job ->
            // The models are freed below: let an in-flight native call take the abort first (#76).
            job.cancel()
            runBlocking { withTimeoutOrNull(RELEASE_WAIT_MS) { job.join() } }
        }
        removeQuickSettingsPopup()
        removeFloatingButton()
        closeZoneOverlay.remove()
        translationRenderOverlay.remove()
        statusOverlay.remove()
        screenCaptureManager.stopProjection()
        translationPipeline.release()
        translationPipeline.close()
        scope.cancel()
        mainScope.cancel()
        sendBroadcast(Intent(ACTION_SERVICE_STOPPED).setPackage(packageName))
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
        val savedX = sharedPreferences.getInt(Constants.PREF_BUTTON_POSITION_X, 0)
        val savedY = sharedPreferences.getInt(Constants.PREF_BUTTON_POSITION_Y, 200)
        buttonPositionX = savedX
        buttonPositionY = savedY
        floatingButton = floatingButtonOverlay.show(
            initialX = savedX,
            initialY = savedY,
            onTap = {
                if (floatingButton?.currentState == FloatingButtonView.State.TRANSLATING) {
                    cancelTranslation()
                } else {
                    startTranslation()
                }
            },
            onDragEnd = { x, y -> persistButtonPosition(x, y) },
            onLongPress = { showQuickSettings() },
            onClose = { stopSelf() }
        )
    }

    private fun removeFloatingButton() {
        floatingButtonOverlay.remove()
        floatingButton = null
    }

    private fun showQuickSettings() {
        quickSettingsPopup?.let {
            it.show(buttonPositionX, buttonPositionY)
            return
        }

        val popup = QuickSettingsPopup(
            context = this,
            windowManager = windowManager,
            onFontSizeChanged = { scale ->
                sharedPreferences.edit().putFloat(Constants.PREF_FONT_SIZE_SCALE, scale).apply()
            },
            onStopRequested = { stopSelf() }
        )
        quickSettingsPopup = popup
        popup.updateFontSizeScale(
            sharedPreferences.getFloat(Constants.PREF_FONT_SIZE_SCALE, Constants.DEFAULT_FONT_SIZE_SCALE)
        )
        popup.show(buttonPositionX, buttonPositionY)
    }

    private fun removeQuickSettingsPopup() {
        quickSettingsPopup?.remove()
        quickSettingsPopup = null
    }

    private fun cancelTranslation() {
        translationJob?.cancel()
        translationRenderOverlay.remove()
        statusOverlay.remove()
        floatingButton?.setState(FloatingButtonView.State.IDLE)
    }

    private fun startTranslation() {
        // A cancelled job is no longer active; the new one queues behind it on the pipeline's lock.
        if (translationJob?.isActive == true) return
        removeQuickSettingsPopup()
        floatingButton?.setState(FloatingButtonView.State.TRANSLATING)

        translationJob = scope.launch {
            val job = coroutineContext.job
            val statuses = modelManager.getAllModels().first().associate { it.id to it.status }
            if (!slotSelection.ready(statuses) || !readingModelFiles.all { it.exists() }) {
                failTranslation(job, "Download a model in Settings")
                return@launch
            }

            updateStatus(job, "Capturing screen")
            if (!screenCaptureManager.isProjectionActive) {
                failTranslation(job, "No active MediaProjection")
                return@launch
            }

            Log.i(TAG, "Capture begin projectionActive=${screenCaptureManager.isProjectionActive}")
            val bitmap = captureBitmap()
            Log.i(TAG, "Capture result isNull=${bitmap == null}")
            if (bitmap == null) {
                failTranslation(job, "Capture returned no frame")
                return@launch
            }

            val callback = object : TranslationPipeline.PipelineCallback {
                override fun onStageProgress(stage: TranslationPipeline.Stage, progress: Float) {
                    Log.i(TAG, "Pipeline progress stage=$stage progress=$progress")
                    updateStatus(job, statusOverlay.messageForStage(stage))
                }

                override fun onError(stage: TranslationPipeline.Stage, message: String) {
                    Log.e(TAG, "Pipeline error stage=$stage message=$message")
                    updateStatus(job, "Failed: $message")
                }

                override fun onComplete(result: PipelineResult) {
                    Log.i(TAG, "Pipeline complete bubbles=${result.typesetBubbles.size} timeMs=${result.totalTimeMs}")
                    updateStatus(job, "Drawing translation")
                }
            }

            val ocrStates = mutableListOf<OverlayBubbleState>()

            val onOcrComplete: (Int, String, android.graphics.RectF) -> Unit = { bubbleId, ocrText, bounds ->
                val state = OverlayBubbleState(
                    bubbleId = bubbleId,
                    bounds = OverlayBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                    ocrText = ocrText
                )
                ocrStates.add(state)
                mainScope.launch {
                    // A cancel already cleared the screen; a late OCR hit must not redraw it.
                    if (job.isActive) translationRenderOverlay.showOcrBubbles(ocrStates, bitmap.width, bitmap.height)
                }
            }

            val session = sessionManager.getOrCreateSession("manual")

            val result = translationPipeline.processPage(
                bitmap,
                callback = callback,
                onOcrComplete = onOcrComplete
            )
            if (result != null && result.typesetBubbles.isNotEmpty()) {
                saveSessionResult(session, result)
            }
            withContext(Dispatchers.Main) {
                if (result != null) {
                    translationRenderOverlay.show(
                        result.typesetBubbles,
                        result.pageWidth,
                        result.pageHeight
                    )
                    statusOverlay.remove()
                } else {
                    showTranslationFailedToast()
                    delay(1500)
                    statusOverlay.remove()
                }
                floatingButton?.setState(FloatingButtonView.State.IDLE)
            }
        }
    }

    private suspend fun captureBitmap() = suspendCancellableCoroutine { continuation ->
        screenCaptureManager.captureScreen { bitmap ->
            if (continuation.isActive) {
                continuation.resume(bitmap)
            }
        }
    }

    private suspend fun saveSessionResult(session: TranslationSessionEntity, result: PipelineResult) {
        val translatedText = result.translationResult.translations.joinToString("\n") { translation ->
            "[${translation.bubbleId}] ${translation.originalText} → ${translation.translatedText}"
        }
        if (translatedText.isBlank()) return
        sessionManager.saveTranslation(
            sessionId = session.id,
            sourceImagePath = "",
            translatedText = translatedText,
            sourceLanguage = Constants.DEFAULT_SOURCE_LANGUAGE,
            targetLanguage = Constants.DEFAULT_TARGET_LANGUAGE,
            bubbleCount = result.typesetBubbles.size,
            translationTimeMs = result.totalTimeMs
        )
    }

    private suspend fun failTranslation(job: Job, reason: String) {
        Log.e(TAG, reason)
        updateStatus(job, "Failed: $reason")
        withContext(Dispatchers.Main) {
            showTranslationFailedToast(reason)
            delay(1500)
            statusOverlay.remove()
            floatingButton?.setState(FloatingButtonView.State.IDLE)
        }
    }

    private fun showTranslationFailedToast(message: String = "Translation failed") {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun persistButtonPosition(x: Int, y: Int) {
        buttonPositionX = x
        buttonPositionY = y
        sharedPreferences.edit()
            .putInt(Constants.PREF_BUTTON_POSITION_X, x)
            .putInt(Constants.PREF_BUTTON_POSITION_Y, y)
            .apply()
    }

    private fun updateStatus(job: Job, message: String) {
        mainScope.launch {
            if (job.isActive) statusOverlay.showOrUpdate(message)
        }
    }

    private fun notifyStartFailed(reason: String) {
        Log.e(TAG, reason)
        sendBroadcast(
            Intent(ACTION_SERVICE_START_FAILED)
                .setPackage(packageName)
                .putExtra(EXTRA_FAILURE_REASON, reason)
        )
    }

}
