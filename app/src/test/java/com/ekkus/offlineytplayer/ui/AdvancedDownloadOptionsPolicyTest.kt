package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedDownloadOptionsPolicyTest {
    @Test
    fun advancedChoicesAreMovedOffPrimarySetup() {
        val policy = AdvancedDownloadOptionsPolicy()
        assertFalse(policy.subtitleChoiceOnPrimarySetup)
        assertFalse(policy.audioChoiceOnPrimarySetup)
        assertFalse(policy.advancedChoicesOnPrimarySetup)
        assertTrue(policy.primarySetupIsFocused())
    }

    @Test
    fun advancedSubpageKeepsPrimaryControlsVisibleWithoutScrolling() {
        val policy = AdvancedDownloadOptionsPolicy()
        assertTrue(policy.subtitleChoiceVisible)
        assertTrue(policy.audioChoiceVisible)
        assertTrue(policy.advancedChoicesVisible)
        assertFalse(policy.primaryControlsScrollable)
        assertTrue(policy.subpagePrimaryControlsFit())
    }
}
