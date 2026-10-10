package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCompactScreenPolicyTest {
    @Test
    fun compactPortraitHeightPolicyHasRealThresholds() {
        assertTrue(PortraitLayoutPolicy.primaryControlsFit(PortraitLayoutPolicy.CompactPortraitHeightDp, 1.0f))
        assertTrue(PortraitLayoutPolicy.primaryControlsFit(
            PortraitLayoutPolicy.CompactPortraitHeightDp,
            PortraitLayoutPolicy.LargeFontScale,
        ))
        assertFalse(PortraitLayoutPolicy.primaryControlsFit(200, 1.0f))
        assertFalse(PortraitLayoutPolicy.primaryControlsFit(200, PortraitLayoutPolicy.LargeFontScale))
    }
}
