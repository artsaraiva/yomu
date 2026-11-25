package com.yomu.pipeline.bubble

import com.yomu.ml.OnnxRuntime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalloonBoxTest {

    private fun text(confidence: Float, vararg box: Float) = OnnxRuntime.Detection("text_bubble", confidence, box)
    private fun balloon(confidence: Float, vararg box: Float) = OnnxRuntime.Detection(OnnxRuntime.BALLOON_LABEL, confidence, box)

    // Two text boxes in their balloons, a free line, and a nested text box NMS must drop.
    private val texts = listOf(
        text(0.80f, 40f, 50f, 120f, 260f),
        text(0.60f, 60f, 100f, 100f, 200f),
        text(0.70f, 400f, 60f, 460f, 200f),
        text(0.90f, 600f, 600f, 700f, 640f)
    )
    // Each balloon holds a text box whole and scores higher than it: in the text NMS, the
    // containment rule would drop the text box or the #370 swap would put the balloon in its place.
    // The last two overlap without nesting (0.85), so balloon NMS keeps both and both hold one text box.
    private val balloons = listOf(
        balloon(0.95f, 10f, 20f, 160f, 300f),
        balloon(0.92f, 380f, 40f, 480f, 220f),
        balloon(0.91f, 300f, 30f, 465f, 230f)
    )

    @Test
    fun `kept text boxes are the same with and without balloons`() {
        val without = keptTextWithBalloons(texts, 0.25f).map { it.detection }
        val with = keptTextWithBalloons(texts + balloons, 0.25f).map { it.detection }

        assertEquals(3, without.size)
        assertEquals(without, with)
    }

    @Test
    fun `each text box gets the smallest balloon holding it`() {
        val kept = keptTextWithBalloons(texts + balloons, 0.25f).associateBy { it.detection.bbox[0] }

        assertArrayEquals(floatArrayOf(10f, 20f, 160f, 300f), kept.getValue(40f).balloon, 0f)
        assertArrayEquals(floatArrayOf(380f, 40f, 480f, 220f), kept.getValue(400f).balloon, 0f)
        assertNull("a free line has no balloon", kept.getValue(600f).balloon)
    }

    @Test
    fun `a balloon holding under 0_90 of a text box is not its balloon`() {
        // The balloon covers the top half of the text box only.
        val kept = keptTextWithBalloons(listOf(text(0.8f, 0f, 0f, 100f, 200f), balloon(0.9f, 0f, 0f, 100f, 100f)), 0.25f)

        assertNull(kept.single().balloon)
    }
}
