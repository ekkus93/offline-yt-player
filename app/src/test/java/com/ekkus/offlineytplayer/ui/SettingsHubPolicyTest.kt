package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHubPolicyTest {
    @Test
    fun hubUsesFixedRequiredRows() {
        assertEquals(
            listOf("Downloads", "Playback", "Storage", "Appearance", "About"),
            SettingsHubPolicy.rows,
        )
        assertEquals(SettingsHubPolicy.RowCount, SettingsHubPolicy.rows.size)
    }

    @Test
    fun compactPortraitBudgetFitsWithoutScrolling() {
        assertTrue(SettingsHubPolicy.fitsWithoutScrolling(640))
        assertFalse(SettingsHubPolicy.fitsWithoutScrolling(SettingsHubPolicy.requiredHeightDp() - 1))
    }
}
