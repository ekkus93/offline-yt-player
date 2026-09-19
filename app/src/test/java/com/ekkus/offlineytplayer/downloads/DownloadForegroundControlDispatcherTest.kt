package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadForegroundControlDispatcherTest {
    @Test
    fun pauseResumeAndCancelUseSharedGatewayPath() {
        val gateway = FakeDownloadControlGateway()

        assertTrue(
            DownloadForegroundControlDispatcher.dispatch(
                DownloadForegroundService.ACTION_PAUSE,
                "job-1",
                gateway,
            ),
        )
        assertTrue(
            DownloadForegroundControlDispatcher.dispatch(
                DownloadForegroundService.ACTION_RESUME,
                "job-1",
                gateway,
            ),
        )
        assertTrue(
            DownloadForegroundControlDispatcher.dispatch(
                DownloadForegroundService.ACTION_CANCEL,
                "job-1",
                gateway,
            ),
        )

        assertEquals(listOf("job-1"), gateway.pausedJobIds)
        assertEquals(listOf("job-1"), gateway.resumedJobIds)
        assertEquals(listOf("job-1"), gateway.canceledJobIds)
    }

    @Test
    fun dispatcherRejectsMissingQueueItemIdInsteadOfGuessing() {
        val gateway = FakeDownloadControlGateway()

        assertFalse(DownloadForegroundControlDispatcher.dispatch(DownloadForegroundService.ACTION_PAUSE, null, gateway))
        assertFalse(DownloadForegroundControlDispatcher.dispatch(DownloadForegroundService.ACTION_PAUSE, "   ", gateway))
        assertTrue(gateway.pausedJobIds.isEmpty())
    }
}
