package com.yomu.app.translation

import com.yomu.core.TranslatablePage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitTranslationBridgeTest {

    @Test
    fun translatePage_emptyPageDoesNotLoadTheModel() = runTest {
        val slot = MlKitTranslationBridge()

        val result = slot.translatePage(TranslatablePage(emptyList()))

        assertEquals(emptyMap<Int, String>(), result.byId)
        assertEquals(0L, result.durationMs)
    }
}
