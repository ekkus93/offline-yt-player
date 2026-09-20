package com.ekkus.offlineytplayer.ui

import java.net.URI

internal data class DownloadSetupState(
    val sourceUrl: String,
    val title: String,
    val durationLabel: String,
    val qualityLabel: String,
    val estimatedSizeLabel: String,
    val readyForDownload: Boolean,
    val thumbnailUrl: String? = null,
    val qualityOptions: List<String> = emptyList(),
)

internal object DownloadSetupRoute {
    fun previewFor(sourceUrl: String): DownloadSetupState? {
        val normalized = sourceUrl.trim().takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if ((uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrBlank()) return null
        return DownloadSetupState(
            sourceUrl = uri.toASCIIString(),
            title = "Shared video",
            durationLabel = "Resolve pending",
            qualityLabel = "Best compatible",
            estimatedSizeLabel = "Calculated after source resolution",
            readyForDownload = true,
        )
    }
}
