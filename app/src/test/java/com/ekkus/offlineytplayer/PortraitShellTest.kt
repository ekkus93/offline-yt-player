package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.ui.AppDestination
import com.ekkus.offlineytplayer.ui.PortraitLayoutPolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitShellTest {
    @Test
    fun manifestLocksMainActivityToPortrait() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:screenOrientation=\"portrait\""))
        assertFalse(manifest.contains("landscape"))
    }

    @Test
    fun bottomNavigationIsFixedToRequiredDestinations() {
        assertEquals(listOf("Library", "Downloads", "Add", "Settings"), AppDestination.entries.map { it.label })
        assertEquals(4, PortraitLayoutPolicy.BottomDestinationCount)
    }

    @Test
    fun compactPortraitBudgetKeepsPrimaryControlsVisibleAtLargeFont() {
        assertTrue(PortraitLayoutPolicy.primaryControlsFit(PortraitLayoutPolicy.CompactPortraitHeightDp, PortraitLayoutPolicy.LargeFontScale))
    }

    @Test
    fun settingsHubHasExactlyFiveFixedCategories() {
        assertEquals(5, PortraitLayoutPolicy.SettingsHubRowCount)
    }
}
