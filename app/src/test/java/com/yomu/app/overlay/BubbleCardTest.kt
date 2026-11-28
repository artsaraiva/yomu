package com.yomu.app.overlay

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.yomu.core.TargetLanguage
import com.yomu.pipeline.typesetting.TypesetBubble
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BubbleCardTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup()
    private val saved = mutableListOf<Pair<String, String>>()
    private val typing = mutableListOf<Boolean>()
    private var closes = 0
    private var accepts = true

    @After
    fun tearDown() {
        activity.pause().stop().destroy()
    }

    @Test
    fun `the translation is labelled with the language the page was translated into`() {
        val card = card(TargetLanguage.SPANISH)

        assertEquals(listOf("Japanese", "Spanish"), texts(card).filter { it in LANGUAGES })
        assertTrue(described(card, "Copy Spanish").isClickable)
        assertTrue(described(card, "Copy Japanese").isClickable)
    }

    @Test
    fun `the close button closes the card`() {
        described(card(), "Close").performClick()

        assertEquals(1, closes)
    }

    @Test
    fun `adding a name opens the fields with the bubble's Japanese ready to edit`() {
        val card = card()

        labelled(card, "Add a name from this bubble").performClick()

        assertEquals(listOf("ルフィは来る", ""), fields(card).map { it.text.toString() })
        assertTrue(fields(card).all { it.isEnabled })
        assertEquals(listOf(true), typing)
    }

    @Test
    fun `a name is saved as typed and the card returns to its texts`() {
        val card = card()
        labelled(card, "Add a name from this bubble").performClick()
        fields(card)[0].setText("ルフィ")
        fields(card)[1].setText("Luffy")

        labelled(card, "Save").performClick()

        assertEquals(listOf("ルフィ" to "Luffy"), saved)
        assertEquals(listOf(true, false), typing)
        assertTrue(fields(card).isEmpty())
        assertTrue("Luffy is coming" in texts(card))
    }

    @Test
    fun `a blank name cannot be saved`() {
        val card = card()
        labelled(card, "Add a name from this bubble").performClick()

        assertFalse(labelled(card, "Save").isEnabled)
        fields(card)[1].setText("Luffy")
        assertTrue(labelled(card, "Save").isEnabled)
        fields(card)[0].setText("  ")
        assertFalse(labelled(card, "Save").isEnabled)
    }

    @Test
    fun `a refused name keeps the fields open`() {
        accepts = false
        val card = card()
        labelled(card, "Add a name from this bubble").performClick()
        fields(card)[1].setText("Luffy")

        labelled(card, "Save").performClick()

        assertEquals(2, fields(card).size)
        assertEquals(listOf(true), typing)
    }

    @Test
    fun `cancelling returns to the texts and saves nothing`() {
        val card = card()
        labelled(card, "Add a name from this bubble").performClick()

        labelled(card, "Cancel").performClick()

        assertTrue(saved.isEmpty())
        assertEquals(listOf(true, false), typing)
        assertTrue("Luffy is coming" in texts(card))
    }

    private fun card(language: TargetLanguage = TargetLanguage.ENGLISH): ViewGroup {
        val bubble = TypesetBubble(1, "Luffy is coming", "ルフィは来る", 16f, listOf("Luffy is coming"), floatArrayOf(0f, 0f, 100f, 100f))
        val host = BubbleCardHost(
            saveName = { term, rendering -> accepts.also { if (it) saved += term to rendering } },
            setTyping = { typing += it },
            close = { closes++ }
        )
        return (bubbleCard(activity.get(), bubble, atTop = false, language, host) as ViewGroup).also(activity.get()::setContentView)
    }

    private fun views(view: View): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private fun texts(card: View): List<String> = views(card).filterIsInstance<TextView>().map { it.text.toString() }

    private fun fields(card: View): List<EditText> = views(card).filterIsInstance<EditText>()

    private fun described(card: View, description: String): View = views(card).first { it.contentDescription == description }

    /** The control showing [label]: the labelled view itself, or the clickable row it sits in. */
    private fun labelled(card: View, label: String): View {
        val text = views(card).first { it is TextView && it.text.toString() == label }
        return generateSequence(text) { it.parent as? View }.first { it.isClickable }
    }

    private companion object {
        val LANGUAGES = setOf("Japanese") + TargetLanguage.entries.map { it.promptName }
    }
}
