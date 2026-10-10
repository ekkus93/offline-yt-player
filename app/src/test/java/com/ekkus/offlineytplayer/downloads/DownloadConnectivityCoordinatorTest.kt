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
    fun malformedSuccessfulQueueCannotBypassConnectivityPolicy() {
        val controls = FakeDownloadControlGateway()
        val core = object : com.ekkus.offlineytplayer.coregateway.AppCoreGateway by FakeCoreGateway() {
            override fun listDownloadQueue(): com.ekkus.offlineytplayer.coregateway.CoreGatewayResult<List<CoreDownloadSnapshot>> =
                com.ekkus.offlineytplayer.coregateway.CoreGatewayResult(null, null)
        }
        val coordinator = DownloadConnectivityCoordinator(
            coreGateway = core,
            controlGateway = controls,
            networkPreference = { DownloadNetworkPreference.WifiOnly },
        )

        val report = coordinator.onConnectivityChanged(DownloadConnectivity.Metered)

        assertEquals(DownloadNetworkDecision.PauseForConnectivity, report.decision)
        assertEquals(1, report.errors.size)
        assertEquals("repository_unavailable", report.errors.single().kind)
        assertTrue(controls.pausedJobIds.isEmpty())
        assertTrue(controls.resumedJobIds.isEmpty())
    }

    @Test
    fun throwingQueueReadReturnsErrorInsteadOfEscapingConnectivityCallback() {
        val delegate = FakeCoreGateway()
        val gateway = object : com.ekkus.offlineytplayer.coregateway.AppCoreGateway by delegate {
            override fun listDownloadQueue(): com.ekkus.offlineytplayer.coregateway.CoreGatewayResult<List<CoreDownloadSnapshot>> =
                throw IllegalStateException("private url query token")
        }
        val controls = FakeDownloadControlGateway()
        val coordinator = DownloadConnectivityCoordinator(
            coreGateway = gateway,
            controlGateway = controls,
            networkPreference = { DownloadNetworkPreference.WifiOnly },
        )
        val report = coordinator.onConnectivityChanged(DownloadConnectivity.Metered)
        assertEquals(DownloadNetworkDecision.PauseForConnectivity, report.decision)
        assertEquals("repository_unavailable", report.errors.single().kind)
        assertTrue(!report.errors.single().message.contains("private url"))
        assertTrue(controls.pausedJobIds.isEmpty())
    }

    @Test
    fun throwingPauseDoesNotInventConnectivityPauseSuccess() {
        val delegate = FakeDownloadControlGateway()
        val controls = object : com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway by delegate {
            override fun pause(jobId: String): com.ekkus.offlineytplayer.coregateway.CoreGatewayResult<Boolean> =
                throw IllegalStateException("private url query token")
        }
        val registry = InMemoryDownloadConnectivityPauseRegistry()
        val coordinator = DownloadConnectivityCoordinator(
            coreGateway = FakeCoreGateway(initialDownloads = listOf(snapshot("job-1", CoreDownloadState.DOWNLOADING))),
            controlGateway = controls,
            networkPreference = { DownloadNetworkPreference.WifiOnly },
            connectivityPauseRegistry = registry,
        )
        val report = coordinator.onConnectivityChanged(DownloadConnectivity.Metered)
        assertEquals("download_control_unavailable", report.errors.single().kind)
        assertTrue(report.pausedJobIds.isEmpty())
        assertTrue(!registry.wasPausedByConnectivity("job-1"))
        assertTrue(!report.errors.single().message.contains("private url"))
    }

    @Test
    fun throwingResumeKeepsConnectivityPauseRegistered() {
        val controls = object : com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway by FakeDownloadControlGateway() {
            override fun resume(jobId: String): com.ekkus.offlineytplayer.coregateway.CoreGatewayResult<Boolean> =
                throw IllegalStateException("private url query token")
        }
        val registry = InMemoryDownloadConnectivityPauseRegistry().apply { markPausedByConnectivity("job-1") }
        val coordinator = DownloadConnectivityCoordinator(
            coreGateway = FakeCoreGateway(initialDownloads = listOf(snapshot("job-1", CoreDownloadState.PAUSED))),
            controlGateway = controls,
            networkPreference = { DownloadNetworkPreference.AnyNetwork },
            connectivityPauseRegistry = registry,
        )
        val report = coordinator.onConnectivityChanged(DownloadConnectivity.Unmetered)
        assertEquals("download_control_unavailable", report.errors.single().kind)
        assertTrue(report.resumedJobIds.isEmpty())
        assertTrue(registry.wasPausedByConnectivity("job-1"))
    }

    @Test
    fun lostConnectivityPausesPendingAndActiveDurableWork() {
        val controls = FakeDownloadControlGateway()
        val pauses = InMemoryDownloadConnectivityPauseRegistry()
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
            connectivityPauseRegistry = pauses,
        )

        val report = coordinator.onConnectivityChanged(DownloadConnectivity.None)

        assertEquals(DownloadNetworkDecision.PauseForConnectivity, report.decision)
        assertEquals(
            listOf(
                "active-downloading",
                "active-resolving",
                "active-verifying",
                "queued",
                "retry-wait",
            ),
            controls.pausedJobIds,
        )
        assertEquals(controls.pausedJobIds, report.pausedJobIds)
        assertTrue(controls.resumedJobIds.isEmpty())
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun restoredEligibleConnectivityResumesOnlyConnectivityPausedWork() {
        val controls = FakeDownloadControlGateway()
        val pauses = InMemoryDownloadConnectivityPauseRegistry().apply {
            markPausedByConnectivity("paused-a")
            markPausedByConnectivity("paused-b")
        }
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
            connectivityPauseRegistry = pauses,
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
        val pauses = InMemoryDownloadConnectivityPauseRegistry()
        val coordinator = DownloadConnectivityCoordinator(
            coreGateway = FakeCoreGateway(
                initialDownloads = listOf(
                    snapshot("active", CoreDownloadState.DOWNLOADING),
                    snapshot("paused", CoreDownloadState.PAUSED),
                ),
            ),
            controlGateway = controls,
            networkPreference = { DownloadNetworkPreference.WifiOnly },
            connectivityPauseRegistry = pauses,
        )

        val report = coordinator.onConnectivityChanged(DownloadConnectivity.Metered)

        assertEquals(DownloadNetworkDecision.PauseForConnectivity, report.decision)
        assertEquals(listOf("active"), controls.pausedJobIds)
        assertTrue(controls.resumedJobIds.isEmpty())
    }

    @Test
    fun userPausedWorkIsNeverAutoResumedByConnectivity() {
        val controls = FakeDownloadControlGateway()
        val pauses = InMemoryDownloadConnectivityPauseRegistry()
        val coordinator = DownloadConnectivityCoordinator(
            coreGateway = FakeCoreGateway(
                initialDownloads = listOf(snapshot("user-paused", CoreDownloadState.PAUSED)),
            ),
            controlGateway = controls,
            networkPreference = { DownloadNetworkPreference.AnyNetwork },
            connectivityPauseRegistry = pauses,
        )

        val report = coordinator.onConnectivityChanged(DownloadConnectivity.Unmetered)

        assertEquals(DownloadNetworkDecision.Allow, report.decision)
        assertTrue(controls.resumedJobIds.isEmpty())
        assertTrue(report.resumedJobIds.isEmpty())
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
