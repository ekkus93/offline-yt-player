package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSetupPolicyTest {
    @Test
    fun primarySetupSurfaceIsCompleteAndNonScrolling() {
        val policy = DownloadSetupPolicy()
        assertTrue(policy.thumbnailVisible)
        assertTrue(policy.titleVisible)
        assertTrue(policy.durationVisible)
        assertTrue(policy.curatedQualityChoicesVisible)
        assertTrue(policy.estimatedSizeVisibleWhenAvailable)
        assertTrue(policy.optionsActionVisible)
        assertTrue(policy.downloadActionVisible)
        assertFalse(policy.primaryScreenScrollable)
        assertTrue(policy.primaryControlsFit())
    }

    @Test
    fun estimatedSizeIsShownOnlyWhenAvailable() {
        val policy = DownloadSetupPolicy()
        assertEquals("10 MB estimated", policy.estimatedSizeLabel(10L * 1024L * 1024L))
        assertNull(policy.estimatedSizeLabel(null))
        assertNull(policy.estimatedSizeLabel(-1L))
    }
}
