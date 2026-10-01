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
    val subtitleOptions: List<String> = emptyList(),
    val audioOptions: List<String> = emptyList(),
    val containerOptions: List<String> = emptyList(),
    val qualityChoiceIdsByLabel: Map<String, String> = emptyMap(),
    val qualityEstimatedBytesByLabel: Map<String, Long?> = emptyMap(),
    val qualitySeparateAudioByLabel: Map<String, Boolean> = emptyMap(),
    val subtitleTrackIdsByLabel: Map<String, String> = emptyMap(),
    val audioFormatIdsByLabel: Map<String, String> = emptyMap(),
    val selectedQualityChoiceId: String? = null,
    val selectedSubtitleTrackId: String? = null,
    val selectedAudioFormatId: String? = null,
    val sourceProvider: String? = null,
    val sourceMediaId: String? = null,
)

internal object SetupThumbnailPolicy {
    const val MaxBytes = 2 * 1024 * 1024
    const val ConnectTimeoutMs = 4_000
    const val ReadTimeoutMs = 4_000

    fun acceptedUrl(value: String?): String? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() && it.length <= 4_096 } ?: return null
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1) return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "ytimg.com" && !host.endsWith(".ytimg.com")) return null
        return uri.toASCIIString()
    }
}

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
