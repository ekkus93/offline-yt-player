package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadResumeCoordinatorTest {
    private class RecordingScheduler(
        private val accepted: Boolean = true,
        private val kind: DownloadSchedulerKind = DownloadSchedulerKind.ForegroundServiceFallback,
    ) : DownloadExecutionScheduler {
        val requests = mutableListOf<DownloadScheduleRequest>()

        override fun schedule(request: DownloadScheduleRequest): DownloadScheduleResult {
            requests += request
            return DownloadScheduleResult(kind = kind, accepted = accepted, jobId = 123)
        }
    }

    @Test
    fun resumeWaitsWithoutMutatingQueueWhenCurrentNetworkViolatesPreference() {
        val control = FakeDownloadControlGateway()
        val scheduler = RecordingScheduler()
        val coordinator = DownloadResumeCoordinator(control, scheduler)

        val result = coordinator.resume(
            DownloadResumeRequest(
                queueItemId = "paused-wifi",
                estimatedDownloadBytes = 10_000,
                networkPreference = DownloadNetworkPreference.WifiOnly,
            ),
            connectivity = DownloadConnectivity.Metered,
        )

        assertEquals(DownloadResumeStatus.WaitingForConnectivity, result.status)
        assertFalse(result.accepted)
        assertTrue(control.resumedJobIds.isEmpty())
        assertTrue(scheduler.requests.isEmpty())
    }

    @Test
    fun resumeTransitionsThroughControlGatewayThenSchedulesSharedDurableWork() {
        val control = FakeDownloadControlGateway()
        val scheduler = RecordingScheduler(kind = DownloadSchedulerKind.UserInitiatedDataTransferJob)
        val coordinator = DownloadResumeCoordinator(control, scheduler)

        val result = coordinator.resume(
            DownloadResumeRequest(
                queueItemId = "paused-job",
                estimatedDownloadBytes = 42_000,
                networkPreference = DownloadNetworkPreference.WifiOnly,
            ),
            connectivity = DownloadConnectivity.Unmetered,
        )

        assertEquals(DownloadResumeStatus.Scheduled, result.status)
        assertTrue(result.accepted)
        assertTrue(result.controlUpdated)
        assertEquals(DownloadSchedulerKind.UserInitiatedDataTransferJob, result.scheduleKind)
        assertEquals(listOf("paused-job"), control.resumedJobIds)
        assertEquals(1, scheduler.requests.size)
        assertEquals("paused-job", scheduler.requests.single().queueItemId)
        assertEquals(42_000, scheduler.requests.single().estimatedDownloadBytes)
        assertEquals(DownloadNetworkPreference.WifiOnly, scheduler.requests.single().networkPreference)
    }

    @Test
    fun resumeReportsSchedulerRejectionAfterDurableControlTransition() {
        val control = FakeDownloadControlGateway()
        val scheduler = RecordingScheduler(accepted = false)
        val coordinator = DownloadResumeCoordinator(control, scheduler)

        val result = coordinator.resume(
            DownloadResumeRequest(queueItemId = "paused-job"),
            connectivity = DownloadConnectivity.Unmetered,
        )

        assertEquals(DownloadResumeStatus.ScheduleRejected, result.status)
        assertFalse(result.accepted)
        assertTrue(result.controlUpdated)
        assertNull(result.error)
        assertEquals(listOf("paused-job"), control.resumedJobIds)
        assertEquals(1, scheduler.requests.size)
    }

    @Test
    fun resumePolicyDocumentsTheRequiredSharedContracts() {
        assertTrue(DownloadResumePolicy.ResumeTransitionsToEligibleQueueState)
        assertTrue(DownloadResumePolicy.ResumeDoesNotBypassScheduler)
        assertTrue(DownloadResumePolicy.ResumeHonorsNetworkPreference)
        assertEquals(
            DownloadResumeDecision.WaitForConnectivity,
            DownloadResumePolicy.decision(DownloadNetworkPreference.AnyNetwork, DownloadConnectivity.None),
        )
        assertEquals(
            DownloadResumeDecision.Schedule,
            DownloadResumePolicy.decision(DownloadNetworkPreference.AnyNetwork, DownloadConnectivity.Metered),
        )
    }
}
