package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.ui.DownloadScreenPolicy
import com.ekkus.offlineytplayer.ui.DownloadsScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPresentationMappingTest {
    private fun map(total: Long?): com.ekkus.offlineytplayer.ui.DownloadRowModel {
        val snapshot = CoreDownloadSnapshot(
            jobId = "job-1",
            state = CoreDownloadState.DOWNLOADING,
            bytesDownloaded = 2048,
            totalBytes = total,
            attempt = 1,
            retryAtEpochMs = null,
            lastError = null,
        )
        val result = CoreGatewayResult(value = listOf(snapshot), error = null)
            .toDownloadsScreenState(mapOf("job-1" to "Fixture"))
        return (result as DownloadsScreenState.Ready).rows.single()
    }

    @Test
    fun unknownOrNonpositiveTotalDoesNotInventZeroPercent() {
        for (total in listOf(null, 0L, -1L)) {
            val row = map(total)
            assertNull(row.percent)
            assertTrue(DownloadScreenPolicy.detail(row).contains("Progress unknown"))
            assertFalse(DownloadScreenPolicy.detail(row).contains("0%"))
            assertTrue(DownloadScreenPolicy.detail(row).contains("2048 bytes"))
        }
    }

    @Test
    fun knownTotalStillShowsMeasuredPercentage() {
        val row = map(8192L)
        assertEquals(25, row.percent)
        assertTrue(DownloadScreenPolicy.detail(row).contains("25%"))
        assertFalse(DownloadScreenPolicy.detail(row).contains("Progress unknown"))
    }
}
