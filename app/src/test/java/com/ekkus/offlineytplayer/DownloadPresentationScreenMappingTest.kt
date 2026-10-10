package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.ui.DownloadsScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPresentationScreenMappingTest {
    private val snapshot = CoreDownloadSnapshot(
        jobId = "job-1",
        state = CoreDownloadState.DOWNLOADING,
        bytesDownloaded = 25,
        totalBytes = 100,
        attempt = 1,
        retryAtEpochMs = null,
        lastError = null,
    )

    @Test
    fun missingPresentationTitleFailsClosed() {
        val state = CoreGatewayResult(value = listOf(snapshot), error = null)
            .toDownloadsScreenState(emptyMap())

        assertEquals(
            DownloadsScreenState.Failed("Download presentation metadata is unavailable."),
            state,
        )
    }

    @Test
    fun blankPresentationTitleFailsClosed() {
        val state = CoreGatewayResult(value = listOf(snapshot), error = null)
            .toDownloadsScreenState(mapOf("job-1" to "   "))

        assertTrue(state is DownloadsScreenState.Failed)
    }

    @Test
    fun persistedPresentationTitleIsUsedWithoutFabrication() {
        val state = CoreGatewayResult(value = listOf(snapshot), error = null)
            .toDownloadsScreenState(mapOf("job-1" to "Saved title"))

        state as DownloadsScreenState.Ready
        assertEquals("Saved title", state.rows.single().title)
    }
}
