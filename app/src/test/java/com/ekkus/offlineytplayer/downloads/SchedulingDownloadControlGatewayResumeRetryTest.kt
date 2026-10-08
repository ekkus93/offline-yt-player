package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingDownloadControlGatewayResumeRetryTest {
    @Test
    fun resumeAndRetryKickAndroidSchedulerUsingCurrentNetworkPreference() {
        val delegate = FakeDownloadControlGateway()
        val scheduler = RecordingScheduler(true)
        val gateway = SchedulingDownloadControlGateway(
            delegate, scheduler, { AppSettingsSnapshot(wifiOnlyDownloads = true) },
        )

        assertTrue(gateway.resume("paused-job").value == true)
        assertTrue(gateway.retry("failed-job").value == true)
        assertTrue(gateway.pause("paused-job").value == true)
        assertTrue(gateway.cancel("failed-job").value == true)

        assertEquals(listOf("paused-job"), delegate.resumedJobIds)
        assertEquals(listOf("failed-job"), delegate.retriedJobIds)
        assertEquals(listOf("paused-job", "failed-job"), scheduler.requests.map { it.queueItemId })
        assertTrue(scheduler.requests.all { it.networkPreference == DownloadNetworkPreference.WifiOnly })
    }

    @Test
    fun schedulerRejectionIsReportedInsteadOfClaimingResumeSuccess() {
        val scheduler = RecordingScheduler(false)
        val gateway = SchedulingDownloadControlGateway(
            FakeDownloadControlGateway(), scheduler, { AppSettingsSnapshot() },
        )
        val result = gateway.resume("paused-job")

        assertFalse(result.value ?: true)
        assertEquals("scheduler_rejected", result.error?.kind)
        assertTrue(result.error?.retryable == true)
        assertEquals(DownloadNetworkPreference.AnyNetwork, scheduler.requests.single().networkPreference)
    }

    @Test
    fun rejectedDurableTransitionDoesNotScheduleWork() {
        val delegate = object : AppDownloadControlGateway by FakeDownloadControlGateway() {
            override fun resume(jobId: String) = CoreGatewayResult(value = false, error = null)
            override fun retry(jobId: String) = CoreGatewayResult(value = false, error = null)
        }
        val scheduler = RecordingScheduler(true)
        val gateway = SchedulingDownloadControlGateway(
            delegate, scheduler, { AppSettingsSnapshot() },
        )

        assertFalse(gateway.resume("paused-job").value ?: true)
        assertFalse(gateway.retry("failed-job").value ?: true)
        assertTrue(scheduler.requests.isEmpty())
    }

    private class RecordingScheduler(private val accepted: Boolean) : DownloadExecutionScheduler {
        val requests = mutableListOf<DownloadScheduleRequest>()

        override fun schedule(request: DownloadScheduleRequest): DownloadScheduleResult {
            requests += request
            return DownloadScheduleResult(
                kind = DownloadSchedulerKind.ForegroundServiceFallback,
                accepted = accepted,
            )
        }
    }
}
