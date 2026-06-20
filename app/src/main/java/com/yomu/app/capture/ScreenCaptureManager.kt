package com.yomu.app.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionManager: MediaProjectionManager? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    var isProjectionActive: Boolean = false
        private set

    fun getProjectionManager(): MediaProjectionManager {
        if (projectionManager == null) {
            projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager
        }
        return projectionManager!!
    }

    fun startProjection(projection: MediaProjection) {
        mediaProjection?.stop()
        mediaProjection = projection

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopProjection()
            }
        }, mainHandler)

        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        display.getRealMetrics(metrics)

        val density = metrics.densityDpi
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        imageReader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "YomuScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler
        )

        isProjectionActive = true
    }

    fun captureScreen(onBitmapReady: (Bitmap?) -> Unit) {
        if (!isProjectionActive || imageReader == null) {
            onBitmapReady(null)
            return
        }

        val image = imageReader?.acquireLatestImage()

        if (image != null) {
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width

                val bitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                val cropWidth = image.width
                val cropHeight = image.height
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, cropWidth, cropHeight)

                bitmap.recycle()
                onBitmapReady(cropped)
            } catch (e: Exception) {
                onBitmapReady(null)
            } finally {
                image.close()
            }
        } else {
            onBitmapReady(null)
        }
    }

    fun stopProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        isProjectionActive = false
    }
}
