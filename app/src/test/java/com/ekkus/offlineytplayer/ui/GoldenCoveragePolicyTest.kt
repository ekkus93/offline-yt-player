package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldenCoveragePolicyTest {
    @Test
    fun requiredGoldenSurfacesMatchTodoScope() {
        assertEquals(
            listOf(
                GoldenSurface.Library,
                GoldenSurface.Add,
                GoldenSurface.DownloadSetup,
                GoldenSurface.Downloads,
                GoldenSurface.Player,
                GoldenSurface.SettingsHub,
                GoldenSurface.SettingsDownloads,
                GoldenSurface.SettingsPlayback,
                GoldenSurface.SettingsStorage,
                GoldenSurface.SettingsAppearance,
                GoldenSurface.SettingsAbout,
            ),
            GoldenCoveragePolicy.Surfaces,
        )
    }

    @Test
    fun everySurfaceHasCompactAndLargePortraitCoverage() {
        val scenarios = GoldenCoveragePolicy.RequiredScenarios
        assertEquals(GoldenCoveragePolicy.Surfaces.size * GoldenCoveragePolicy.Profiles.size, scenarios.size)
        for (surface in GoldenCoveragePolicy.Surfaces) {
            assertTrue(GoldenScenario(surface, GoldenProfile.CompactPortrait) in scenarios)
            assertTrue(GoldenScenario(surface, GoldenProfile.LargePortrait) in scenarios)
        }
    }

    @Test
    fun goldenGateIsDeterministicAndDocumentsBitmapBoundary() {
        assertTrue(GoldenCoveragePolicy.UsesDeterministicHostSideManifest)
        assertTrue(GoldenCoveragePolicy.BitmapGoldensDeferredUntilStableComposeHarness)
    }
}
