package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadConnectivityCoordinatorTest {
    @Test
    fun noNetworkPausesActiveDurableJob() {
        val gateway = FakeDownloadControlGateway()
        val coordinator = DownloadConnectivityCoordinator()

        assertTrue(
            coordinator.dispatch(
                preference = DownloadNetworkPreference.AnyNetwork,
                connectivity = DownloadConnectivity.None,
                queueItemId = "job-1",
                gateway = gateway,
            ),
        )

        assertEquals(listOf("job-1"), gateway.pausedJobIds)
        assertTrue(gateway.resumedJobIds.isEmpty())
    }

    @Test
    fun wifiOnlyPausesOnMeteredAndResumesOnlyItsOwnConnectivityPause() {
        val gateway = FakeDownloadControlGateway()
        val coordinator = DownloadConnectivityCoordinator()

        assertTrue(
            coordinator.dispatch(
                preference = DownloadNetworkPreference.WifiOnly,
                connectivity = DownloadConnectivity.Metered,
                queueItemId = "job-2",
                gateway = gateway,
            ),
        )
        assertTrue(
            coordinator.dispatch(
                preference = DownloadNetworkPreference.WifiOnly,
                connectivity = DownloadConnectivity.Unmetered,
                queueItemId = "job-2",
                gateway = gateway,
            ),
        )

        assertEquals(listOf("job-2"), gateway.pausedJobIds)
        assertEquals(listOf("job-2"), gateway.resumedJobIds)
    }

    @Test
    fun restoredNetworkDoesNotResumeUserPausedJobThatConnectivityDidNotPause() {
        val gateway = FakeDownloadControlGateway()
        val coordinator = DownloadConnectivityCoordinator()

        assertFalse(
            coordinator.dispatch(
                preference = DownloadNetworkPreference.AnyNetwork,
                connectivity = DownloadConnectivity.Unmetered,
                queueItemId = "user-paused",
                gateway = gateway,
            ),
        )

        assertTrue(gateway.pausedJobIds.isEmpty())
        assertTrue(gateway.resumedJobIds.isEmpty())
    }

    @Test
    fun missingQueueItemDoesNotGuessWhichJobToMutate() {
        val gateway = FakeDownloadControlGateway()
        val coordinator = DownloadConnectivityCoordinator()

        assertFalse(
            coordinator.dispatch(
                preference = DownloadNetworkPreference.AnyNetwork,
                connectivity = DownloadConnectivity.None,
                queueItemId = null,
                gateway = gateway,
            ),
        )

        assertTrue(gateway.pausedJobIds.isEmpty())
        assertTrue(gateway.resumedJobIds.isEmpty())
    }
}
