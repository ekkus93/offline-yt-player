package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScreenLayoutPolicyTest {
    @Test
    fun playerUsesFixedPortraitVideoAspectRatio() {
        assertEquals(16, PlayerScreenLayoutPolicy.VideoAspectRatioWidth)
        assertEquals(9, PlayerScreenLayoutPolicy.VideoAspectRatioHeight)
        assertEquals(16f / 9f, PlayerScreenLayoutPolicy.videoAspectRatio())
    }

    @Test
    fun playerHasExplicitTransportAndSecondaryControls() {
        assertEquals(
            listOf("Skip back", "Play/Pause", "Skip forward"),
            PlayerScreenLayoutPolicy.transportLabels,
        )
        assertEquals(3, PlayerScreenLayoutPolicy.PrimaryTransportControlCount)
        assertEquals(
            listOf("Speed", "Subtitles", "Audio"),
            PlayerScreenLayoutPolicy.secondaryLabels,
        )
        assertEquals(3, PlayerScreenLayoutPolicy.SecondaryControlCount)
    }

    @Test
    fun playerDoesNotExposeLandscapeOrScrollDependentPrimaryControls() {
        assertFalse(PlayerScreenLayoutPolicy.SupportsLandscapeAction)
        assertFalse(PlayerScreenLayoutPolicy.RequiresPrimaryControlScrolling)
    }

    @Test
    fun compactPortraitBudgetKeepsPrimaryControlsVisible() {
        assertTrue(
            PlayerScreenLayoutPolicy.primaryControlsFit(
                PlayerScreenLayoutPolicy.CompactPortraitHeightDp,
                1.0f,
            ),
        )
        assertTrue(
            PlayerScreenLayoutPolicy.primaryControlsFit(
                PlayerScreenLayoutPolicy.CompactPortraitHeightDp,
                PlayerScreenLayoutPolicy.LargeFontScale,
            ),
        )
    }
}
