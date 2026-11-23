package com.yomu.app.ui.settings

import com.yomu.ml.ConnectFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Providers screen names each failure in its own words (#489). */
class ProvidersMessagesTest {

    @Test
    fun `each Connect failure and the unsaved key has its own message`() {
        val messages = ConnectFailure.entries.map(::connectFailureMessage) + KEY_NOT_SAVED_MESSAGE

        assertEquals(messages.size, messages.distinct().size)
        assertTrue(messages.none(String::isBlank))
    }

    @Test
    fun `the address-not-allowed message says the address needs https`() {
        assertTrue(connectFailureMessage(ConnectFailure.HTTPS_REQUIRED).contains("https://"))
    }
}
