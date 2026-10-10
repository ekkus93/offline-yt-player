package com.ekkus.offlineytplayer.ui

import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRowControlBindingTest {
    @Test
    fun falseUpdatesAreNotReportedAsSuccessfulActions() {
        val gateway = object : AppDownloadControlGateway by FakeDownloadControlGateway() {
            override fun pause(jobId: String) = CoreGatewayResult(false, null)
            override fun resume(jobId: String) = CoreGatewayResult(false, null)
            override fun cancel(jobId: String) = CoreGatewayResult(false, null)
            override fun retry(jobId: String) = CoreGatewayResult(false, null)
        }
        DownloadRowAction.entries.forEach { action ->
            assertFalse(DownloadRowControlBinding.invoke(action, "job", gateway))
        }
    }

    @Test
    fun explicitFfiErrorRejectsEvenTrueUpdate() {
        val gateway = object : AppDownloadControlGateway by FakeDownloadControlGateway() {
            override fun pause(jobId: String) = CoreGatewayResult(
                true, CoreGatewayError("invalid", "not allowed", false),
            )
        }
        assertFalse(DownloadRowControlBinding.invoke(DownloadRowAction.Pause, "job", gateway))
    }

    @Test
    fun visiblePauseActionCallsTheDownloadControlGateway() {
        val gateway = FakeDownloadControlGateway()

        assertTrue(DownloadRowControlBinding.invoke(DownloadRowAction.Pause, "job-visible", gateway))

        assertEquals(listOf("job-visible"), gateway.pausedJobIds)
    }

    @Test
    fun resumeCancelAndRetryActionsUseTheSameControlGatewayFamily() {
        val gateway = FakeDownloadControlGateway()

        assertTrue(DownloadRowControlBinding.invoke(DownloadRowAction.Resume, "job-visible", gateway))
        assertTrue(DownloadRowControlBinding.invoke(DownloadRowAction.Cancel, "job-visible", gateway))
        assertTrue(DownloadRowControlBinding.invoke(DownloadRowAction.Retry, "job-visible", gateway))

        assertEquals(listOf("job-visible"), gateway.resumedJobIds)
        assertEquals(listOf("job-visible"), gateway.canceledJobIds)
        assertEquals(listOf("job-visible"), gateway.retriedJobIds)
    }
}
