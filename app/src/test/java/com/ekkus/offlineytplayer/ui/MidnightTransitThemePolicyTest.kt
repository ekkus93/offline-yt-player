package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidnightTransitThemePolicyTest {
    @Test
    fun semanticColorTokensMeetContrastPolicy() {
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
        assertTrue(
            ThemeContrastPolicy.ratio(MidnightTransit.Accent, MidnightTransit.Background) >=
                ThemeContrastPolicy.LargeTextAndUiMinimum,
        )
        assertTrue(
            ThemeContrastPolicy.ratio(MidnightTransit.Error, MidnightTransit.Background) >=
                ThemeContrastPolicy.LargeTextAndUiMinimum,
        )
        assertTrue(
            ThemeContrastPolicy.ratio(MidnightTransit.Warning, MidnightTransit.Background) >=
                ThemeContrastPolicy.LargeTextAndUiMinimum,
        )
        assertTrue(
            ThemeContrastPolicy.ratio(MidnightTransit.Success, MidnightTransit.Background) >=
                ThemeContrastPolicy.LargeTextAndUiMinimum,
        )
    }

    @Test
    fun interactionStateTokensAreExplicitAndDistinct() {
        assertFalse(MidnightTransit.DisabledContent == MidnightTransit.TextSecondary)
        assertFalse(MidnightTransit.DisabledContainer == MidnightTransit.Surface)
        assertFalse(MidnightTransit.PressedOverlay == MidnightTransit.Background)
        assertEquals(MidnightTransit.Accent, MidnightTransit.FocusIndicator)
        assertFalse(MidnightTransit.ErrorContainer == MidnightTransit.Error)
    }

    @Test
    fun shapeSpacingAndTouchTargetsAreBoundedSemanticTokens() {
        assertTrue(MidnightTransit.SmallRadius.value > 0)
        assertTrue(MidnightTransit.LargeRadius.value >= MidnightTransit.SmallRadius.value)
        assertTrue(MidnightTransit.ScreenSpacing.value > 0)
        assertTrue(MidnightTransit.SectionSpacing.value > 0)
        assertEquals(48, MidnightTransit.MinimumTouchTarget.value.toInt())
    }

    @Test
    fun themePreferenceExposesDarkLightAndSystemModes() {
        assertEquals(
            listOf(ThemePreference.Dark, ThemePreference.Light, ThemePreference.System),
            ThemePreference.entries,
        )
    }
}
