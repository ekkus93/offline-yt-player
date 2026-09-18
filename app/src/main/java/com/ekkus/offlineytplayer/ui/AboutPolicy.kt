package com.ekkus.offlineytplayer.ui

internal data class AboutInfo(
    val versionName: String,
    val versionCode: Int,
    val sourceRevision: String,
    val licensesLabel: String = "Open-source licenses",
    val privacySummary: String = "Downloaded media and playback state stay on this device unless you explicitly share diagnostics.",
    val legalNotice: String = "Source services are independent third parties. Availability and permitted use remain subject to their terms and applicable law.",
)

internal object AboutPolicy {
    const val MaximumPrimaryRows = 5
    const val DiagnosticsExportOptional = true

    fun primaryRows(info: AboutInfo): List<String> = listOf(
        "Version ${info.versionName} (${info.versionCode})",
        info.licensesLabel,
        "Privacy",
        "Legal",
        "Build ${info.sourceRevision.take(12)}",
    )
}
