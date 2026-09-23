package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingDownloadControlGatewayTest {
    @Test
    fun enqueueSchedulesAcceptedWorkWithCurrentNetworkPreference() {
        val delegate = FakeDownloadControlGateway()
        val scheduler = RecordingDownloadExecutionScheduler(accepted = true)
        val gateway = SchedulingDownloadControlGateway(
            delegate = delegate,
            scheduler = scheduler,
            settingsSnapshot = { AppSettingsSnapshot(wifiOnlyDownloads = true) },
        )

        val result = gateway.enqueue("job-1")

        assertEquals(CoreGatewayResult(value = true, error = null), result)
        assertEquals(listOf("job-1"), delegate.enqueuedJobIds)
        assertEquals("job-1", scheduler.requests.single().queueItemId)
        assertEquals(DownloadNetworkPreference.WifiOnly, scheduler.requests.single().networkPreference)
    }

    @Test
    fun enqueueReportsSchedulerRejectionAfterDurableQueueAcceptsWork() {
        val delegate = FakeDownloadControlGateway()
        val scheduler = RecordingDownloadExecutionScheduler(accepted = false)
        val gateway = SchedulingDownloadControlGateway(
            delegate = delegate,
            scheduler = scheduler,
            settingsSnapshot = { AppSettingsSnapshot(wifiOnlyDownloads = false) },
        )

        val result = gateway.enqueue("job-2")

        assertFalse(result.value ?: true)
        assertEquals("scheduler_rejected", result.error?.kind)
        assertTrue(result.error?.retryable == true)
        assertEquals(DownloadNetworkPreference.AnyNetwork, scheduler.requests.single().networkPreference)
    }

    private class RecordingDownloadExecutionScheduler(
        private val accepted: Boolean,
    ) : DownloadExecutionScheduler {
        val requests = mutableListOf<DownloadScheduleRequest>()

        override fun schedule(request: DownloadScheduleRequest): DownloadScheduleResult {
            requests += request
            return DownloadScheduleResult(
                kind = DownloadSchedulerKind.UserInitiatedDataTransferJob,
                accepted = accepted,
                jobId = 1,
            )
        }
    }
}
