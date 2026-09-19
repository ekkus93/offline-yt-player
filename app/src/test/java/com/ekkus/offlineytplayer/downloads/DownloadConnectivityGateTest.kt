package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadConnectivityGateTest {
    private class RecordingScheduler(
        private val accepted: Boolean = true,
        private val kind: DownloadSchedulerKind = DownloadSchedulerKind.ForegroundServiceFallback,
    ) : DownloadExecutionScheduler {
        val requests = mutableListOf<DownloadScheduleRequest>()

        override fun schedule(request: DownloadScheduleRequest): DownloadScheduleResult {
            requests += request
            return DownloadScheduleResult(kind = kind, accepted = accepted, jobId = 456)
        }
    }

    @Test
    fun noNetworkPausesActiveWorkAndRecordsWaitingItem() {
        val control = FakeDownloadControlGateway()
        val scheduler = RecordingScheduler()
        val gate = DownloadConnectivityGate(control, scheduler)

        val result = gate.reconcileActiveWork(
            DownloadConnectivityWorkItem(queueItemId = "active-any"),
            connectivity = DownloadConnectivity.None,
        )

        assertEquals(DownloadConnectivityGateStatus.PausedForConnectivity, result.status)
        assertTrue(result.controlUpdated)
        assertFalse(result.accepted)
        assertEquals(listOf("active-any"), control.pausedJobIds)
        assertEquals(setOf("active-any"), gate.waitingQueueItemIds())
        assertTrue(scheduler.requests.isEmpty())
    }

    @Test
    fun meteredNetworkPausesWifiOnlyWorkWithoutViolatingPreference() {
        val control = FakeDownloadControlGateway()
        val scheduler = RecordingScheduler()
        val gate = DownloadConnectivityGate(control, scheduler)

        val result = gate.reconcileActiveWork(
            DownloadConnectivityWorkItem(
                queueItemId = "wifi-only",
                estimatedDownloadBytes = 99,
                networkPreference = DownloadNetworkPreference.WifiOnly,
            ),
            connectivity = DownloadConnectivity.Metered,
        )

        assertEquals(DownloadConnectivityGateStatus.PausedForConnectivity, result.status)
        assertEquals(listOf("wifi-only"), control.pausedJobIds)
        assertEquals(setOf("wifi-only"), gate.waitingQueueItemIds())
        assertTrue(scheduler.requests.isEmpty())
    }

    @Test
    fun restoredConnectivityAutomaticallyReschedulesEligibleWaitingWork() {
        val control = FakeDownloadControlGateway()
        val scheduler = RecordingScheduler(kind = DownloadSchedulerKind.UserInitiatedDataTransferJob)
        val gate = DownloadConnectivityGate(control, scheduler)
        gate.reconcileActiveWork(
            DownloadConnectivityWorkItem(
                queueItemId = "wifi-only",
                estimatedDownloadBytes = 1234,
                networkPreference = DownloadNetworkPreference.WifiOnly,
            ),
            connectivity = DownloadConnectivity.None,
        )

        val results = gate.onConnectivityChanged(DownloadConnectivity.Unmetered)

        assertEquals(listOf(DownloadConnectivityGateStatus.Rescheduled), results.map { it.status })
        assertTrue(results.single().accepted)
        assertEquals(DownloadSchedulerKind.UserInitiatedDataTransferJob, results.single().scheduleKind)
        assertTrue(gate.waitingQueueItemIds().isEmpty())
        assertEquals(1, scheduler.requests.size)
        assertEquals("wifi-only", scheduler.requests.single().queueItemId)
        assertEquals(1234L, scheduler.requests.single().estimatedDownloadBytes)
        assertEquals(DownloadNetworkPreference.WifiOnly, scheduler.requests.single().networkPreference)
    }

    @Test
    fun connectivityChangeLeavesIneligibleWifiOnlyWorkWaitingOnMeteredNetwork() {
        val gate = DownloadConnectivityGate(FakeDownloadControlGateway(), RecordingScheduler())
        gate.reconcileActiveWork(
            DownloadConnectivityWorkItem("wifi-only", networkPreference = DownloadNetworkPreference.WifiOnly),
            connectivity = DownloadConnectivity.None,
        )

        val results = gate.onConnectivityChanged(DownloadConnectivity.Metered)

        assertEquals(listOf(DownloadConnectivityGateStatus.StillWaiting), results.map { it.status })
        assertEquals(setOf("wifi-only"), gate.waitingQueueItemIds())
    }

    @Test
    fun schedulerRejectionDoesNotForgetWaitingWork() {
        val gate = DownloadConnectivityGate(FakeDownloadControlGateway(), RecordingScheduler(accepted = false))
        gate.reconcileActiveWork(
            DownloadConnectivityWorkItem("waiting-any"),
            connectivity = DownloadConnectivity.None,
        )

        val results = gate.onConnectivityChanged(DownloadConnectivity.Metered)

        assertEquals(listOf(DownloadConnectivityGateStatus.ScheduleRejected), results.map { it.status })
        assertEquals(setOf("waiting-any"), gate.waitingQueueItemIds())
    }

    @Test
    fun policyDocumentsTransitionContracts() {
        assertTrue(DownloadConnectivityGatePolicy.PausesActiveWorkWhenPolicyDisallows)
        assertTrue(DownloadConnectivityGatePolicy.EnforcesWifiOnlyPreference)
        assertTrue(DownloadConnectivityGatePolicy.RequeuesWaitingWorkWhenConstraintsReturn)
    }
}
