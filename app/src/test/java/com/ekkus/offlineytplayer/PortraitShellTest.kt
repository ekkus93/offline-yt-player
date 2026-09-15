package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.ui.AppDestination
import com.ekkus.offlineytplayer.ui.PortraitLayoutPolicy
import com.ekkus.offlineytplayer.ui.SettingsSection
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
        assertEquals(listOf("Downloads", "Playback", "Storage", "Appearance", "About"), SettingsSection.entries.map { it.label })
    }

    @Test
    fun settingsSubpagesStayWithinFixedRowBudget() {
        assertEquals(5, PortraitLayoutPolicy.MaxSettingsRows)
        assertTrue(SettingsSection.entries.size <= PortraitLayoutPolicy.MaxSettingsRows)
    }

    @Test
    fun advancedDownloadOptionsUseDedicatedFixedPage() {
        assertEquals(4, PortraitLayoutPolicy.AdvancedOptionsRowCount)
        assertTrue(PortraitLayoutPolicy.AdvancedOptionsRowCount <= PortraitLayoutPolicy.MaxSettingsRows)
    }
}
