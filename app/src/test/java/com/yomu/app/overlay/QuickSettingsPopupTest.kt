package com.yomu.app.overlay

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RadioButton
import android.widget.TextView
import com.yomu.core.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuickSettingsPopupTest {
    private val windowManager = mock(WindowManager::class.java)
    private var releaseOutside: (() -> Unit)? = null
    private var unblocks = 0
    private var unavailable: String? = null
    private val chosen = mutableListOf<TargetLanguage>()
    private val popup = QuickSettingsPopup(
        context = RuntimeEnvironment.getApplication(),
        windowManager = windowManager,
        modelName = { "Model" },
        targetLanguage = { TargetLanguage.ENGLISH },
        languageUnavailable = { language -> unavailable.takeIf { language != TargetLanguage.ENGLISH } },
        onLanguageChosen = { chosen += it },
        onFontSizeChanged = {},
        onThresholdChanged = {},
        hasLastPage = { false },
        onReportRequested = {},
        onOpenAppRequested = {},
        onStopRequested = {},
        blockTouchesBelow = { releaseOutside = it },
        unblockTouchesBelow = { unblocks++ }
    )

    /** An outside tap must close the popup through the touch layer, not by watching it pass through to the app (#352). */
    @Test
    fun releaseOutsideClosesThePopupAndUnblocksTouches() {
        popup.show(0, 0)
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        verify(windowManager).addView(any(View::class.java), params.capture())
        assertEquals(0, params.value.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)

        checkNotNull(releaseOutside).invoke()
        assertEquals(1, unblocks)

        popup.show(0, 0)
        verify(windowManager, times(2)).addView(any(View::class.java), any(WindowManager.LayoutParams::class.java))
    }

    private fun shownContent(): View {
        popup.show(0, 0)
        val view = ArgumentCaptor.forClass(View::class.java)
        verify(windowManager).addView(view.capture(), any(WindowManager.LayoutParams::class.java))
        return view.value
    }

    private fun View.withText(text: String): TextView? = when {
        this is TextView && this.text.toString() == text -> this
        this is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).withText(text) }
        else -> null
    }

    @Test
    fun theLanguageChipOpensTheLanguagesAndChoosingOneSwitchesToIt() {
        val content = shownContent()
        val spanish = checkNotNull(content.withText("Spanish"))
        val languages = spanish.parent as View
        assertEquals(View.GONE, languages.visibility)

        checkNotNull(content.withText("Japanese → English")).performClick()
        assertEquals(View.VISIBLE, languages.visibility)
        spanish.performClick()

        assertEquals(listOf(TargetLanguage.SPANISH), chosen)
    }

    @Test
    fun otherLanguagesAreDisabledWithTheReasonWhenNoMultilingualModelFits() {
        unavailable = "No Multilingual model fits this device"
        val content = shownContent()

        assertFalse(checkNotNull(content.withText("French · No Multilingual model fits this device")).isEnabled)
        assertTrue((checkNotNull(content.withText("English")) as RadioButton).isChecked)
    }
}
