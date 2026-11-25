package com.yomu.pipeline.bubble

import android.graphics.Bitmap
import com.yomu.ml.OnnxRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

/** Spike #471: balloon boxes ride alongside the text boxes without changing which text boxes are kept. */
@RunWith(RobolectricTestRunner::class)
class BalloonKeepTest {

    private val page = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    private fun det(label: String, score: Float, vararg box: Float) = OnnxRuntime.Detection(label, score, box)

    private fun detector(detections: List<OnnxRuntime.Detection>): BubbleDetector {
        val onnx = mock(OnnxRuntime::class.java)
        `when`(onnx.loadModel(anyString())).thenReturn(true)
        `when`(onnx.runBitmapInference("m.onnx", page, 0.25f)).thenReturn(detections)
        return BubbleDetector(onnx).also { it.loadModel("m.onnx") }
    }

    @Test
    fun `balloon boxes do not change the kept text boxes and become their hosts`() {
        val text = det("text_bubble", 0.9f, 40f, 50f, 120f, 260f)
        val free = det("text_free", 0.8f, 500f, 600f, 700f, 650f)
        val balloon = det("bubble", 0.95f, 10f, 20f, 300f, 400f)

        val withBalloon = detector(listOf(balloon, text, free)).detect(page, 0.25f)
        val textOnly = detector(listOf(text, free)).detect(page, 0.25f)

        assertEquals(textOnly.map { it.boundingBox }, withBalloon.map { it.boundingBox })
        assertEquals(listOf(10f, 20f, 300f, 400f), withBalloon[0].balloonBox?.let { listOf(it.left, it.top, it.right, it.bottom) })
        assertNull(withBalloon[1].balloonBox)
        assertEquals(listOf("text_bubble", "text_free"), withBalloon.map { it.label })
    }
}
