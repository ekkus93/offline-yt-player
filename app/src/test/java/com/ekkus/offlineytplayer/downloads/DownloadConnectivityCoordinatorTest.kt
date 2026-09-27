package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.FakeCoreGateway
import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadConnectivityCoordinatorTest {
    @Test
    fun lostConnectivityPausesOnlyActiveDurableWork() {
        val controls = FakeDownloadControlGateway()
        val coordinator = DownloadConnectivityCoordinator(
            coreGateway = FakeCoreGateway(
                initialDownloads = listOf(
                    snapshot("active-downloading", CoreDownloadState.DOWNLOADING),
                    snapshot("active-resolving", CoreDownloadState.RESOLVING),
                    snapshot("active-verifying", CoreDownloadState.VERIFYING),
                    snapshot("already-paused", CoreDownloadState.PAUSED),
                    snapshot("completed", CoreDownloadState.COMPLETED),
                    snapshot("queued", CoreDownloadState.QUEUED),
                    snapshot("retry-wait", CoreDownloadState.RETRY_WAIT),
                ),
            ),
            controlGateway = controls,
            networkPreference = { DownloadNetworkPreference.AnyNetwork },
        )

        val report = coordinator.onConnectivityChanged(DownloadConnectivity.None)

        assertEquals(DownloadNetworkDecision.PauseForConnectivity, report.decision)
        assertEquals(
            listOf("active-downloading", "active-resolving", "active-verifying"),
            controls.pausedJobIds,
        )
        assertEquals(controls.pausedJobIds, report.pausedJobIds)
        assertTrue(controls.resumedJobIds.isEmpty())
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun restoredEligibleConnectivityResumesPausedWork() {
        val controls = FakeDownloadControlGateway()
        val coordinator = DownloadConnectivityCoordinator(
            coreGateway = FakeCoreGateway(
                initialDownloads = listOf(
                    snapshot("done", CoreDownloadState.COMPLETED),
                    snapshot("paused-a", CoreDownloadState.PAUSED),
                    snapshot("paused-b", CoreDownloadState.PAUSED),
                ),
            ),
            controlGateway = controls,
            networkPreference = { DownloadNetworkPreference.WifiOnly },
        )

        val report = coordinator.onConnectivityChanged(DownloadConnectivity.Unmetered)

        assertEquals(DownloadNetworkDecision.Allow, report.decision)
        assertEquals(listOf("paused-a", "paused-b"), controls.resumedJobIds)
        assertEquals(controls.resumedJobIds, report.resumedJobIds)
        assertTrue(controls.pausedJobIds.isEmpty())
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun meteredConnectivityKeepsWifiOnlyPausedAndPausesActiveWork() {
        val controls = FakeDownloadControlGateway()
        val coordinator = DownloadConnectivityCoordinator(
            coreGateway = FakeCoreGateway(
                initialDownloads = listOf(
                    snapshot("active", CoreDownloadState.DOWNLOADING),
                    snapshot("paused", CoreDownloadState.PAUSED),
                ),
            ),
            controlGateway = controls,
            networkPreference = { DownloadNetworkPreference.WifiOnly },
        )

        val report = coordinator.onConnectivityChanged(DownloadConnectivity.Metered)

        assertEquals(DownloadNetworkDecision.PauseForConnectivity, report.decision)
        assertEquals(listOf("active"), controls.pausedJobIds)
        assertTrue(controls.resumedJobIds.isEmpty())
    }
}

private fun snapshot(jobId: String, state: CoreDownloadState): CoreDownloadSnapshot = CoreDownloadSnapshot(
    jobId = jobId,
    state = state,
    bytesDownloaded = 128,
    totalBytes = 1024,
    attempt = 1,
    retryAtEpochMs = null,
    lastError = null,
)
