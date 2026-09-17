package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsScreenPolicyTest {
    @Test
    fun fixedChromeStaysOutsideScrollableTransferRegion() {
        val policy = DownloadsScreenPolicy()

        assertTrue(policy.fixedAppBar)
        assertTrue(policy.fixedFilterControls)
        assertTrue(policy.fixedBottomNavigation)
        assertTrue(policy.transferListScrolls)
        assertFalse(policy.wholeScreenScrolls)
        assertTrue(policy.fixedChromeIsPreserved())
    }

    @Test
    fun allRequiredTransferStatesHaveExplicitPresentation() {
        val policy = DownloadsScreenPolicy()
        val required = listOf(
            DownloadVisualState.Active,
            DownloadVisualState.Paused,
            DownloadVisualState.Failed,
            DownloadVisualState.Completed,
        )

        assertEquals(4, DownloadVisualState.entries.size)
        assertTrue(required.all(policy::supports))
    }
}
