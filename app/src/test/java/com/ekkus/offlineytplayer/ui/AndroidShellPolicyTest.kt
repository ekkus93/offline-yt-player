package com.ekkus.offlineytplayer.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidShellPolicyTest {
    @Test
    fun bottomNavigationUsesFixedPortraitDestinations() {
        assertEquals(
            listOf("Library", "Downloads", "Add", "Settings"),
            AppDestination.entries.map { it.label },
        )
        assertEquals(PortraitLayoutPolicy.BottomDestinationCount, AppDestination.entries.size)
        assertEquals(AppDestination.Library, AppDestination.entries.first())
    }

    @Test
    fun compactPortraitPrimaryControlsAreBudgeted() {
        assertTrue(
            PortraitLayoutPolicy.primaryControlsFit(
                PortraitLayoutPolicy.CompactPortraitHeightDp,
                1.0f,
            ),
        )
        assertTrue(
            PortraitLayoutPolicy.primaryControlsFit(
                PortraitLayoutPolicy.CompactPortraitHeightDp,
                PortraitLayoutPolicy.LargeFontScale,
            ),
        )
        assertEquals(4, PortraitLayoutPolicy.PrimarySetupControlCount)
        assertEquals(4, PortraitLayoutPolicy.AdvancedOptionsRowCount)
        assertTrue(PortraitLayoutPolicy.SettingsHubRowCount <= PortraitLayoutPolicy.MaxSettingsRows)
    }

    @Test
    fun manifestLocksMainActivityToPortraitAndNoLandscapeResourcesExist() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:name=\".MainActivity\""))
        assertTrue(manifest.contains("android:screenOrientation=\"portrait\""))

        val landscapeDirectories = File("src/main/res")
            .walkTopDown()
            .filter { it.isDirectory && it.name.contains("land", ignoreCase = true) }
            .map { it.relativeTo(File("src/main/res")).path }
            .toList()
        assertTrue("Landscape resources are not allowed: $landscapeDirectories", landscapeDirectories.isEmpty())
    }

    @Test
    fun midnightTransitTokensMeetBasicContrastAndTouchTargets() {
        assertTrue(
            ThemeContrastPolicy.ratio(MidnightTransit.TextPrimary, MidnightTransit.Background) >=
                ThemeContrastPolicy.NormalTextMinimum,
        )
        assertTrue(
            ThemeContrastPolicy.ratio(MidnightTransit.TextSecondary, MidnightTransit.Surface) >=
                ThemeContrastPolicy.NormalTextMinimum,
        )
        assertTrue(
            ThemeContrastPolicy.ratio(MidnightTransit.Primary, MidnightTransit.Background) >=
                ThemeContrastPolicy.LargeTextAndUiMinimum,
        )
        assertEquals(48, MidnightTransit.MinimumTouchTarget.value.toInt())
        assertFalse(MidnightTransit.Error == MidnightTransit.Warning)
    }
}
