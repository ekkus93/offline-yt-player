package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
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
    fun enqueueForwardsSelectedQualityChoiceBeforeScheduling() {
        val delegate = FakeDownloadControlGateway()
        val scheduler = RecordingDownloadExecutionScheduler(accepted = true)
        val gateway = SchedulingDownloadControlGateway(
            delegate = delegate,
            scheduler = scheduler,
            settingsSnapshot = { AppSettingsSnapshot() },
        )

        val result = gateway.enqueue("job-choice", "audio-only")

        assertEquals(CoreGatewayResult(value = true, error = null), result)
        assertEquals(listOf("job-choice"), delegate.enqueuedJobIds)
        assertEquals(listOf("audio-only"), delegate.enqueuedChoiceIds)
        assertEquals("job-choice", scheduler.requests.single().queueItemId)
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

    @Test
    fun resumeRetriesAndroidSchedulingWhenDurableWorkIsAlreadyQueued() {
        val delegate = object : AppDownloadControlGateway by FakeDownloadControlGateway() {
            var resumeCalls = 0
            override fun resume(jobId: String): CoreGatewayResult<Boolean> {
                resumeCalls += 1
                return CoreGatewayResult(value = resumeCalls == 1, error = null)
            }
        }
        val scheduler = RecordingDownloadExecutionScheduler(accepted = false)
        val gateway = SchedulingDownloadControlGateway(
            delegate = delegate,
            scheduler = scheduler,
            settingsSnapshot = { AppSettingsSnapshot() },
        )

        assertEquals("scheduler_rejected", gateway.resume("queued-job").error?.kind)
        scheduler.accepted = true
        assertEquals(CoreGatewayResult(value = true, error = null), gateway.resume("queued-job"))
        assertEquals(2, delegate.resumeCalls)
        assertEquals(listOf("queued-job", "queued-job"), scheduler.requests.map { it.queueItemId })
    }

    @Test
    fun resumeWithoutExecutableQueuedWorkDoesNotSchedule() {
        val delegate = object : AppDownloadControlGateway by FakeDownloadControlGateway() {
            override fun resume(jobId: String) = CoreGatewayResult(value = false, error = null)
            override fun enqueue(jobId: String) = CoreGatewayResult(value = false, error = null)
        }
        val scheduler = RecordingDownloadExecutionScheduler(accepted = true)
        val gateway = SchedulingDownloadControlGateway(
            delegate = delegate,
            scheduler = scheduler,
            settingsSnapshot = { AppSettingsSnapshot() },
        )

        assertEquals(CoreGatewayResult(value = false, error = null), gateway.resume("missing-plan"))
        assertTrue(scheduler.requests.isEmpty())
    }

    private class RecordingDownloadExecutionScheduler(
        var accepted: Boolean,
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
