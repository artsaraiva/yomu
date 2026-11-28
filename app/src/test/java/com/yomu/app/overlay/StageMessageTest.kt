package com.yomu.app.overlay

import com.yomu.pipeline.TranslationPipeline.Stage
import org.junit.Assert.assertEquals
import org.junit.Test

/** The status line under the floating button, and its marker while a page goes to the reader's server (#490). */
class StageMessageTest {

    @Test
    fun `a page translated on the server is marked while it is translated`() {
        assertEquals("Translating on your server", stageMessage(Stage.TRANSLATION, onServer = true))
    }

    @Test
    fun `a page translated on the phone carries no marker`() {
        assertEquals("Translating", stageMessage(Stage.TRANSLATION, onServer = false))
    }

    @Test
    fun `the stages that run on the phone read the same either way`() {
        for (stage in Stage.entries - Stage.TRANSLATION) {
            assertEquals(stage.name, stageMessage(stage, onServer = false), stageMessage(stage, onServer = true))
        }
        assertEquals("Finding text bubbles", stageMessage(Stage.BUBBLE_DETECTION, onServer = true))
    }

    @Test
    fun `finding, reading and translating are steps one to three of the run`() {
        assertEquals(1, stageStep(Stage.BUBBLE_DETECTION))
        assertEquals(2, stageStep(Stage.OCR))
        assertEquals(3, stageStep(Stage.TRANSLATION))
        assertEquals(3, PROGRESS_STEPS)
    }

    @Test
    fun `preparing the prompt belongs to the translating step`() {
        assertEquals(3, stageStep(Stage.CONTEXT_ASSEMBLY))
    }
}
