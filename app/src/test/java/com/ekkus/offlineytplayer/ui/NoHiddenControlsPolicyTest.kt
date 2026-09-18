package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoHiddenControlsPolicyTest {
    @Test
    fun everyPrimarySurfaceHasVisibleActionsWithoutScrolling() {
        assertEquals(PrimaryControlSurface.entries.size, NoHiddenControlsPolicy.Budgets.size)
        for (surface in PrimaryControlSurface.entries) {
            assertTrue(NoHiddenControlsPolicy.primaryActionsVisibleWithoutScrolling(surface))
        }
    }

    @Test
    fun noSurfaceUsesHorizontalControlScrolling() {
        for (surface in PrimaryControlSurface.entries) {
            assertTrue(NoHiddenControlsPolicy.noHorizontalControlScrolling(surface))
        }
    }

    @Test
    fun noSurfaceDependsOnLandscapeOnlyAffordances() {
        for (surface in PrimaryControlSurface.entries) {
            assertTrue(NoHiddenControlsPolicy.noLandscapeOnlyAffordance(surface))
        }
    }

    @Test
    fun compactAndLargePortraitProfilesAreBothInScope() {
        assertTrue(NoHiddenControlsPolicy.TargetProfilesIncludeCompactPortrait)
        assertTrue(NoHiddenControlsPolicy.TargetProfilesIncludeLargePortrait)
    }
}
