package com.yomu.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class CarryOverContextTest {

    private val context = listOf("前の台詞" to "The previous line")

    @Test
    fun `keeps context within the same session`() {
        assertEquals(context, carryOverContext(context, contextSessionId = 7L, sessionId = 7L))
    }

    @Test
    fun `drops context when the session rolls over`() {
        assertEquals(emptyList<Pair<String, String>>(), carryOverContext(context, contextSessionId = 7L, sessionId = 8L))
    }

    @Test
    fun `drops context on the first page of a session`() {
        assertEquals(emptyList<Pair<String, String>>(), carryOverContext(context, contextSessionId = -1L, sessionId = 1L))
    }
}
