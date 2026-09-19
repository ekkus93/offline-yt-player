package com.ekkus.offlineytplayer.ui

import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRowControlBindingTest {
    @Test
    fun visiblePauseActionCallsTheDownloadControlGateway() {
        val gateway = FakeDownloadControlGateway()

        assertTrue(DownloadRowControlBinding.invoke(DownloadRowAction.Pause, "job-visible", gateway))

        assertEquals(listOf("job-visible"), gateway.pausedJobIds)
    }

    @Test
    fun resumeAndCancelActionsUseTheSameControlGatewayFamily() {
        val gateway = FakeDownloadControlGateway()

        assertTrue(DownloadRowControlBinding.invoke(DownloadRowAction.Resume, "job-visible", gateway))
        assertTrue(DownloadRowControlBinding.invoke(DownloadRowAction.Cancel, "job-visible", gateway))
        assertFalse(DownloadRowControlBinding.invoke(DownloadRowAction.Retry, "job-visible", gateway))

        assertEquals(listOf("job-visible"), gateway.resumedJobIds)
        assertEquals(listOf("job-visible"), gateway.canceledJobIds)
    }
}
