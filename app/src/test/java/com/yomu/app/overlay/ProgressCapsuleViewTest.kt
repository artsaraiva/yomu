package com.yomu.app.overlay

import android.animation.ValueAnimator
import android.view.View
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The capsule that carries the stage message out of the floating button (#552). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProgressCapsuleViewTest {
    private val capsule = ProgressCapsuleView(RuntimeEnvironment.getApplication())

    @After
    fun restoreAnimations() = setAnimationScale(1f)

    @Test
    fun `the capsule grows out of the button when it first shows`() {
        capsule.show("Finding text bubbles", step = 1)

        assertTrue(capsule.growing)
    }

    @Test
    fun `with animations off the capsule appears without motion`() {
        setAnimationScale(0f)

        capsule.show("Finding text bubbles", step = 1)

        assertEquals(View.VISIBLE, capsule.visibility)
        assertFalse(capsule.growing)
    }

    @Test
    fun `a later stage does not grow the capsule again`() {
        setAnimationScale(0f)
        capsule.show("Finding text bubbles", step = 1)
        setAnimationScale(1f)

        capsule.show("Reading Japanese text", step = 2)

        assertFalse(capsule.growing)
        assertEquals("Reading Japanese text. Step 2 of 3", capsule.contentDescription)
    }

    @Test
    fun `a hidden capsule stops growing`() {
        capsule.show("Finding text bubbles", step = 1)

        capsule.hide()

        assertEquals(View.GONE, capsule.visibility)
        assertFalse(capsule.growing)
    }

    @Test
    fun `the capsule never asks for more width than it is offered`() {
        capsule.show("Translating on your server", step = 3)

        assertTrue(capsule.sizeWithin(120).x <= 120)
    }

    /** The system's "animator duration scale"; zero is what "Remove animations" sets. The setter is hidden from apps. */
    private fun setAnimationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }
}
