package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.ui.DownloadsScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressMetricsTrackerTest {
    private fun row(
        bytes: Long,
        state: CoreDownloadState = CoreDownloadState.DOWNLOADING,
        attempt: Int = 1,
        total: Long? = 10_240L,
        id: String = "queue-1",
    ) = CoreDownloadSnapshot(id, state, bytes, total, attempt, null, null)

    @Test fun realDurableByteDeltasFeedSpeedAndEtaIntoDownloadsUi() {
        val tracker = DownloadProgressMetricsTracker()
        assertTrue(tracker.observe(listOf(row(0)), 1_000).isEmpty())
        val rates = tracker.observe(listOf(row(2_048)), 2_000)
        assertEquals(2_048L, rates.getValue("queue-1").bytesPerSecond)
        assertEquals(4L, rates.getValue("queue-1").etaSeconds)
        val state = CoreGatewayResult(listOf(row(2_048)), null)
            .toDownloadsScreenState(mapOf("queue-1" to "Persisted download"), rates)
        assertTrue(state is DownloadsScreenState.Ready)
        val display = (state as DownloadsScreenState.Ready).rows.single()
        assertEquals("2 KiB/s", display.speedLabel)
        assertEquals("ETA 4s", display.etaLabel)
    }

    @Test fun firstSampleStallAndAbsentTotalNeverInventRateOrEta() {
        val tracker = DownloadProgressMetricsTracker()
        assertTrue(tracker.observe(listOf(row(50)), 1_000).isEmpty())
        assertTrue(tracker.observe(listOf(row(50)), 3_000).isEmpty())
        val rate = tracker.observe(listOf(row(1_074, total = null)), 4_000).getValue("queue-1")
        assertEquals("1 KiB/s", formatDownloadSpeed(rate))
        assertEquals("ETA unavailable", formatDownloadEta(rate))
        assertEquals("Speed unavailable", formatDownloadSpeed(null))
        assertEquals("ETA unavailable", formatDownloadEta(null))
    }

    @Test fun retriesPausedJobsAndCounterResetsCannotReuseStaleSpeed() {
        val tracker = DownloadProgressMetricsTracker()
        tracker.observe(listOf(row(0)), 1_000)
        assertTrue(tracker.observe(listOf(row(2_048, attempt = 2)), 2_000).isEmpty())
        assertTrue(tracker.observe(listOf(row(0, attempt = 2)), 3_000).isEmpty())
        assertEquals(1_024L, tracker.observe(listOf(row(1_024, attempt = 2)), 4_000)
            .getValue("queue-1").bytesPerSecond)
        assertTrue(tracker.observe(listOf(row(1_024, state = CoreDownloadState.PAUSED)), 5_000).isEmpty())
        assertTrue(tracker.observe(listOf(row(1_500, attempt = 3)), 6_000).isEmpty())
        tracker.clear()
        assertTrue(tracker.observe(listOf(row(2_000, attempt = 3)), 7_000).isEmpty())
    }

    @Test fun removedJobsInvalidCountersAndShortIntervalsAreNotExtrapolated() {
        val tracker = DownloadProgressMetricsTracker()
        tracker.observe(listOf(row(0, id = "first")), 1_000)
        assertTrue(tracker.observe(listOf(row(9_000, id = "first")), 1_100).isEmpty())
        assertFalse(tracker.observe(listOf(row(2_048, id = "first")), 2_000).isEmpty())
        assertTrue(tracker.observe(emptyList(), 3_000).isEmpty())
        assertTrue(tracker.observe(listOf(row(3_000, id = "first")), 4_000).isEmpty())
        assertTrue(tracker.observe(listOf(row(-1L, id = "first")), 5_000).isEmpty())
        assertTrue(tracker.observe(listOf(row(5_000, id = "first")), 6_000).isEmpty())
    }
}
