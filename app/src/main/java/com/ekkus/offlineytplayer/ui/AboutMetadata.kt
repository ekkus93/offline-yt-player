package com.ekkus.offlineytplayer.ui

import com.ekkus.offlineytplayer.BuildConfig

/** Runtime-backed metadata rendered by the About settings page. */
internal data class AboutMetadata(
    val versionName: String,
    val versionCode: Int,
    val sourceRevision: String?,
    val licenses: String,
    val privacy: String,
    val diagnostics: String,
    val support: String,
)

internal object AboutMetadataProvider {
    fun current(): AboutMetadata = AboutMetadata(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        sourceRevision = BuildConfig.SOURCE_REVISION.takeUnless { it.isBlank() || it == "local" },
        licenses = "Open-source notices are distributed with the application and source repository.",
        privacy = "Offline media and application state are stored locally on this device.",
        diagnostics = "Diagnostics are local unless the user explicitly exports them.",
        support = "Support and source: github.com/ekkus93/offline-yt-player",
    )

    fun versionLabel(metadata: AboutMetadata): String =
        "${metadata.versionName} (${metadata.versionCode})"

    fun revisionLabel(metadata: AboutMetadata): String =
        metadata.sourceRevision?.take(12) ?: "local build"
}
