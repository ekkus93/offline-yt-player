package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCompactScreenPolicyTest {
    @Test
    fun primaryControlsFitCompactPortraitAtNormalAndLargeText() {
        assertTrue(PortraitLayoutPolicy.primaryControlsFit(PortraitLayoutPolicy.CompactPortraitHeightDp, 1.0f))
        assertTrue(
            PortraitLayoutPolicy.primaryControlsFit(
                PortraitLayoutPolicy.CompactPortraitHeightDp,
                PortraitLayoutPolicy.LargeFontScale,
            ),
        )
    }

    @Test
    fun libraryPolicyForbidsHorizontalScrolling() {
        assertFalse(LibraryCompactScreenPolicy.AllowsHorizontalScrolling)
        assertTrue(LibraryScreenPolicy.OnlyItemRegionScrolls)
    }

    @Test
    fun largeFontKeepsPrimaryActionsInFixedChrome() {
        assertTrue(LibraryCompactScreenPolicy.PrimaryActionsRemainVisibleAtLargeFont)
        assertTrue(LibraryScreenPolicy.HasFixedTopControls)
        assertTrue(LibraryScreenPolicy.UsesFixedBottomNavigation)
    }
}

internal object LibraryCompactScreenPolicy {
    const val AllowsHorizontalScrolling = false
    const val PrimaryActionsRemainVisibleAtLargeFont = true
}
