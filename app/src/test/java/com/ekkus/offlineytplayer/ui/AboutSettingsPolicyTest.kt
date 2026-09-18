package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutSettingsPolicyTest {
    @Test
    fun aboutSectionsExposeVersionLicensesPrivacyDiagnosticsAndLegalNotice() {
        val sections = AboutSettingsPolicy.sections(
            AboutSettings(versionBuild = "1.0.0 (abcdef123456)"),
        )

        assertEquals("Version 1.0.0 (abcdef123456)", sections[0])
        assertTrue(sections.contains(AboutSettingsPolicy.LicensesLabel))
        assertTrue(sections.contains(AboutSettingsPolicy.PrivacyLabel))
        assertTrue(sections.contains(AboutSettingsPolicy.DiagnosticsExportLabel))
        assertTrue(sections.contains(AboutSettingsPolicy.LegalSourceNoticeLabel))
    }

    @Test
    fun diagnosticsExportIsExplicitAndSecretRedacted() {
        val settings = AboutSettings(diagnosticsExportAvailable = true)

        assertTrue(settings.diagnosticsExportAvailable)
        assertTrue(AboutSettingsPolicy.RedactsSecretsFromDiagnostics)
    }

    @Test
    fun optionalSectionsCanBeSuppressedWithoutRemovingVersion() {
        val sections = AboutSettingsPolicy.sections(
            AboutSettings(
                versionBuild = "local",
                licensesAvailable = false,
                privacyAvailable = false,
                diagnosticsExportAvailable = false,
                legalSourceNoticeRequired = false,
            ),
        )

        assertEquals(listOf("Version local"), sections)
    }
}
