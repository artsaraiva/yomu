package com.yomu.app.translation.hf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HfModelDownloaderTest {

    @Test
    fun `rangeHeader is null when nothing is on disk`() {
        assertNull(HfModelDownloader.rangeHeader(0))
    }

    @Test
    fun `rangeHeader resumes from the existing byte count`() {
        assertEquals("bytes=1024-", HfModelDownloader.rangeHeader(1024))
    }

    @Test
    fun `gated repos answer 401 or 403`() {
        assertTrue(HfModelDownloader.isGated(401))
        assertTrue(HfModelDownloader.isGated(403))
    }

    @Test
    fun `success and not-found are not treated as gated`() {
        assertFalse(HfModelDownloader.isGated(200))
        assertFalse(HfModelDownloader.isGated(206))
        assertFalse(HfModelDownloader.isGated(404))
    }
}
