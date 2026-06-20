package com.yomu.app.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayServiceScopeTest {
    @Test
    fun `cancelling scope cancels active job`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = scope.launch { delay(Long.MAX_VALUE) }

        scope.cancel()
        job.join()

        assertTrue(job.isCancelled)
    }
}
