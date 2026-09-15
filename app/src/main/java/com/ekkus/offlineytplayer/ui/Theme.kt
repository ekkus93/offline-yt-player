package com.ekkus.offlineytplayer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object MidnightTransit {
    val Background = Color(0xFF0B0F17)
    val Surface = Color(0xFF141B25)
    val SurfaceElevated = Color(0xFF1C2633)
    val TextPrimary = Color(0xFFF3F6FA)
    val TextSecondary = Color(0xFFA8B3C2)
    val Primary = Color(0xFF7AA2FF)
    val Accent = Color(0xFF39D0C7)
    val Success = Color(0xFF4FD1A1)
    val Warning = Color(0xFFF2B66D)
    val Error = Color(0xFFFF6B7A)

    val SmallRadius = 14.dp
    val LargeRadius = 18.dp
    val ScreenSpacing = 16.dp
    val SectionSpacing = 12.dp
    val MinimumTouchTarget = 48.dp
}

private val DarkColors = darkColorScheme(
    primary = MidnightTransit.Primary,
    secondary = MidnightTransit.Accent,
    background = MidnightTransit.Background,
    surface = MidnightTransit.Surface,
    surfaceVariant = MidnightTransit.SurfaceElevated,
    onPrimary = MidnightTransit.Background,
    onSecondary = MidnightTransit.Background,
    onBackground = MidnightTransit.TextPrimary,
    onSurface = MidnightTransit.TextPrimary,
    onSurfaceVariant = MidnightTransit.TextSecondary,
    error = MidnightTransit.Error,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF315AA8),
    secondary = Color(0xFF006B66),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EDF4),
    onBackground = Color(0xFF101722),
    onSurface = Color(0xFF101722),
    onSurfaceVariant = Color(0xFF536174),
    error = Color(0xFFBA1A1A),
)

enum class ThemePreference { Dark, Light, System }

@Composable
fun OfflineYTPlayerTheme(
    preference: ThemePreference = ThemePreference.Dark,
    content: @Composable () -> Unit,
) {
    val useDark = when (preference) {
        ThemePreference.Dark -> true
        ThemePreference.Light -> false
        ThemePreference.System -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
