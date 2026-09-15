package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastPolicyTest {
    @Test
    fun primaryTextMeetsNormalTextContrastOnDarkSurfaces() {
        assertTrue(ThemeContrastPolicy.ratio(MidnightTransit.TextPrimary, MidnightTransit.Background) >= ThemeContrastPolicy.NormalTextMinimum)
        assertTrue(ThemeContrastPolicy.ratio(MidnightTransit.TextPrimary, MidnightTransit.Surface) >= ThemeContrastPolicy.NormalTextMinimum)
        assertTrue(ThemeContrastPolicy.ratio(MidnightTransit.TextSecondary, MidnightTransit.Surface) >= ThemeContrastPolicy.NormalTextMinimum)
    }

    @Test
    fun interactiveSemanticColorsMeetUiContrastOnDarkBackground() {
        assertTrue(ThemeContrastPolicy.ratio(MidnightTransit.Primary, MidnightTransit.Background) >= ThemeContrastPolicy.LargeTextAndUiMinimum)
        assertTrue(ThemeContrastPolicy.ratio(MidnightTransit.Accent, MidnightTransit.Background) >= ThemeContrastPolicy.LargeTextAndUiMinimum)
        assertTrue(ThemeContrastPolicy.ratio(MidnightTransit.FocusIndicator, MidnightTransit.Background) >= ThemeContrastPolicy.LargeTextAndUiMinimum)
        assertTrue(ThemeContrastPolicy.ratio(MidnightTransit.Error, MidnightTransit.Background) >= ThemeContrastPolicy.LargeTextAndUiMinimum)
    }

    @Test
    fun semanticStateTokensAreDistinctAndBounded() {
        assertTrue(MidnightTransit.DisabledContent.alpha in 0f..0.5f)
        assertTrue(MidnightTransit.DisabledContainer.alpha in 0f..0.2f)
        assertTrue(MidnightTransit.PressedOverlay.alpha in 0f..0.2f)
        assertTrue(MidnightTransit.ErrorContainer.alpha in 0f..0.25f)
        assertTrue(MidnightTransit.FocusIndicator != MidnightTransit.Primary)
    }
}
