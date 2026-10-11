package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.ui.DownloadsScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueIdentityMappingTest {
    private fun snapshot(id: String) = CoreDownloadSnapshot(id, CoreDownloadState.QUEUED, 0, null, 0, null, null)
    private fun map(items: List<CoreDownloadSnapshot>) =
        CoreGatewayResult(value = items, error = null).toDownloadsScreenState(
            items.associate { it.jobId to "Fixture" },
        )

    @Test fun duplicateQueueIdsFailClosed() {
        assertTrue(map(listOf(snapshot("same"), snapshot("same"))) is DownloadsScreenState.Failed)
    }

    @Test fun blankQueueIdsFailClosed() {
        assertTrue(map(listOf(snapshot(" "))) is DownloadsScreenState.Failed)
    }

    @Test fun percentHandlesSignedLongBoundaryWithoutOverflow() {
        assertEquals(100, boundedDownloadPercent(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(50, boundedDownloadPercent(Long.MAX_VALUE / 2L, Long.MAX_VALUE))
        assertEquals(100, boundedDownloadPercent(Long.MAX_VALUE, Long.MAX_VALUE / 2L))
        assertEquals(100, boundedDownloadPercent(500L, 100L))
    }

    @Test fun percentBoundsUnknownAndInvalidDurableCounters() {
        assertEquals(0, boundedDownloadPercent(500L, null))
        assertEquals(0, boundedDownloadPercent(500L, 0L))
        assertEquals(0, boundedDownloadPercent(500L, -1L))
        assertEquals(0, boundedDownloadPercent(-50L, 100L))
        assertEquals(0, boundedDownloadPercent(0L, 100L))
        assertEquals(50, boundedDownloadPercent(50L, 100L))
    }

    @Test fun distinctQueueIdsRemainReady() {
        assertTrue(map(listOf(snapshot("one"), snapshot("two"))) is DownloadsScreenState.Ready)
    }
}
