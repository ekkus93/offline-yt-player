package com.ekkus.offlineytplayer.ui

import com.ekkus.offlineytplayer.BuildMetadata

internal data class AboutSettings(
    val versionBuild: String = BuildMetadata.display,
    val licensesAvailable: Boolean = true,
    val privacyAvailable: Boolean = true,
    val diagnosticsExportAvailable: Boolean = true,
    val legalSourceNoticeRequired: Boolean = true,
)

internal object AboutSettingsPolicy {
    const val LicensesLabel = "Licenses"
    const val PrivacyLabel = "Privacy"
    const val DiagnosticsExportLabel = "Export diagnostics"
    const val LegalSourceNoticeLabel = "Legal and source-service notice"
    const val RedactsSecretsFromDiagnostics = true

    fun sections(settings: AboutSettings = AboutSettings()): List<String> = buildList {
        add("Version ${settings.versionBuild}")
        if (settings.licensesAvailable) add(LicensesLabel)
        if (settings.privacyAvailable) add(PrivacyLabel)
        if (settings.diagnosticsExportAvailable) add(DiagnosticsExportLabel)
        if (settings.legalSourceNoticeRequired) add(LegalSourceNoticeLabel)
    }
}
