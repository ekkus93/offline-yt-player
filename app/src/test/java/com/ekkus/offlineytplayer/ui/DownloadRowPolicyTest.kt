package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRowPolicyTest {
    @Test
    fun activePausedAndFailedRowsExposeVisibleActions() {
        assertEquals(
            setOf(DownloadRowAction.Pause, DownloadRowAction.Cancel),
            DownloadRowPresentation(DownloadVisualState.Active, 1, 10).actions(),
        )
        assertEquals(
            setOf(DownloadRowAction.Resume, DownloadRowAction.Cancel),
            DownloadRowPresentation(DownloadVisualState.Paused, 1, 10).actions(),
        )
        assertEquals(
            setOf(DownloadRowAction.Retry, DownloadRowAction.Cancel),
            DownloadRowPresentation(DownloadVisualState.Failed, 1, 10, "Network timeout").actions(),
        )
    }

    @Test
    fun progressIncludesBytesPercentageAndBoundedFraction() {
        val row = DownloadRowPresentation(DownloadVisualState.Active, 75, 100)
        assertEquals(75, row.progressPercent)
        assertEquals(0.75, row.progressFraction!!, 0.0001)
        assertEquals(100, DownloadRowPresentation(DownloadVisualState.Active, 120, 100).progressPercent)
    }

    @Test
    fun unknownLengthProgressDoesNotFabricatePercentageOrEta() {
        val row = DownloadProgressPresentationMapper.fromProgress(
            state = DownloadVisualState.Active,
            bytesDownloaded = 1024,
            totalBytes = null,
            metrics = DownloadProgressMetrics(speedBytesPerSecond = 512, etaSeconds = 10),
        )

        assertNull(row.progressFraction)
        assertNull(row.progressPercent)
        assertEquals(512L, row.trustworthySpeedBytesPerSecond())
        assertNull(row.trustworthyEtaSeconds())
        assertNull(row.trustworthySpeedAndEta())
    }

    @Test
    fun speedAndEtaAreHiddenUnlessBothAreTrustworthy() {
        assertNull(DownloadRowPresentation(DownloadVisualState.Active, 1, 10).trustworthySpeedAndEta())
        assertNull(DownloadRowPresentation(DownloadVisualState.Active, 1, 10, speedBytesPerSecond = 0, etaSeconds = 5).trustworthySpeedAndEta())
        assertEquals(
            1024L to 12L,
            DownloadRowPresentation(DownloadVisualState.Active, 1, 10, speedBytesPerSecond = 1024, etaSeconds = 12).trustworthySpeedAndEta(),
        )
    }

    @Test
    fun progressMapperKeepsOnlyRealPositiveMetrics() {
        val row = DownloadProgressPresentationMapper.fromProgress(
            state = DownloadVisualState.Active,
            bytesDownloaded = -1,
            totalBytes = 100,
            metrics = DownloadProgressMetrics(speedBytesPerSecond = -5, etaSeconds = -1),
        )

        assertEquals(0, row.bytesDownloaded)
        assertNull(row.speedBytesPerSecond)
        assertNull(row.etaSeconds)
    }

    @Test
    fun failedRowsKeepHumanReadableErrorReason() {
        val row = DownloadRowPresentation(DownloadVisualState.Failed, 4, 10, "  Server unavailable  ")
        assertEquals("Server unavailable", row.humanReadableError())
        assertTrue(row.actions().contains(DownloadRowAction.Retry))
    }
}
