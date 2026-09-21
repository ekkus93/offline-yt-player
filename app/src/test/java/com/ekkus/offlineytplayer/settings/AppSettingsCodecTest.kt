package com.ekkus.offlineytplayer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsCodecTest {
    @Test
    fun missingSettingsUseDocumentedDefaultsAndMigrateToCurrentSchema() {
        val settings = AppSettingsCodec.decode(emptyMap())

        assertEquals(AppSettings.CURRENT_SCHEMA_VERSION, settings.schemaVersion)
        assertEquals(AppearancePreference.System, settings.appearance)
        assertFalse(settings.libraryGrid)
    }

    @Test
    fun typedSettingsRoundTripThroughDurableRepresentation() {
        val expected = AppSettings(
            appearance = AppearancePreference.Dark,
            libraryGrid = true,
        )

        val decoded = AppSettingsCodec.decode(AppSettingsCodec.encode(expected))

        assertEquals(expected, decoded)
    }

    @Test
    fun invalidAppearanceFallsBackSafely() {
        val decoded = AppSettingsCodec.decode(
            mapOf(
                AppSettingsCodec.KEY_SCHEMA_VERSION to AppSettings.CURRENT_SCHEMA_VERSION,
                AppSettingsCodec.KEY_APPEARANCE to "FutureTheme",
                AppSettingsCodec.KEY_LIBRARY_GRID to true,
            ),
        )

        assertEquals(AppearancePreference.System, decoded.appearance)
        assertTrue(decoded.libraryGrid)
    }

    @Test
    fun newerUnknownSchemaFallsBackToSafeDefaults() {
        val decoded = AppSettingsCodec.decode(
            mapOf(
                AppSettingsCodec.KEY_SCHEMA_VERSION to 99,
                AppSettingsCodec.KEY_APPEARANCE to AppearancePreference.Dark.name,
                AppSettingsCodec.KEY_LIBRARY_GRID to true,
            ),
        )

        assertEquals(AppSettings(), decoded)
    }
}
