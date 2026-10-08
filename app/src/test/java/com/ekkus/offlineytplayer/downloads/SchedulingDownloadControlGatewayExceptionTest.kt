package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingDownloadControlGatewayExceptionTest {
    @Test
    fun thrownSchedulingFailureReturnsRecoverableError() {
        val delegate = FakeDownloadControlGateway()
        val scheduler = object : DownloadExecutionScheduler {
            override fun schedule(request: DownloadScheduleRequest): DownloadScheduleResult {
                throw IllegalStateException("scheduler unavailable")
            }
        }
        val gateway = SchedulingDownloadControlGateway(
            delegate, scheduler, { AppSettingsSnapshot() },
        )

        val results = listOf(gateway.enqueue("queued"), gateway.resume("paused"), gateway.retry("failed"))
        results.forEach { result ->
            assertFalse(result.value ?: true)
            assertEquals("scheduler_rejected", result.error?.kind)
            assertTrue(result.error?.retryable == true)
        }
        assertEquals(listOf("queued"), delegate.enqueuedJobIds)
        assertEquals(listOf("paused"), delegate.resumedJobIds)
        assertEquals(listOf("failed"), delegate.retriedJobIds)
    }
}
