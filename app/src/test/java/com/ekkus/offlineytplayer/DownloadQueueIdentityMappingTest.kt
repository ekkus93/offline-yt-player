package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.ui.DownloadsScreenState
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

    @Test fun distinctQueueIdsRemainReady() {
        assertTrue(map(listOf(snapshot("one"), snapshot("two"))) is DownloadsScreenState.Ready)
    }
}
