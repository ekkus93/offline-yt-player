package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageSettingsPolicyTest {
    private val summary = StorageSummary(
        locationLabel = "Internal app storage",
        usedBytes = 1_000,
        freeBytes = 9_000,
        cacheBytes = 100,
        orphanBytes = 20,
        incompleteBytes = 30,
    )

    @Test
    fun summaryCoversLocationCapacityAndCleanup() {
        assertEquals("Internal app storage", summary.locationLabel)
        assertEquals(1_000, summary.usedBytes)
        assertEquals(9_000, summary.freeBytes)
        assertEquals(150, StorageSettingsPolicy.reclaimableBytes(summary))
    }

    @Test
    fun cleanupActionsAreExplicitAndBounded() {
        assertEquals(
            listOf(
                StorageCleanupAction.ClearCache,
                StorageCleanupAction.RemoveOrphans,
                StorageCleanupAction.RemoveIncomplete,
            ),
            StorageSettingsPolicy.CleanupActions,
        )
    }
}
