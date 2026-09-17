package com.ekkus.offlineytplayer.ui

internal data class DownloadSetupPolicy(
    val thumbnailVisible: Boolean = true,
    val titleVisible: Boolean = true,
    val durationVisible: Boolean = true,
    val curatedQualityChoicesVisible: Boolean = true,
    val estimatedSizeVisibleWhenAvailable: Boolean = true,
    val optionsActionVisible: Boolean = true,
    val downloadActionVisible: Boolean = true,
    val primaryScreenScrollable: Boolean = false,
) {
    fun primaryControlsFit(): Boolean =
        thumbnailVisible && titleVisible && durationVisible && curatedQualityChoicesVisible &&
            optionsActionVisible && downloadActionVisible && !primaryScreenScrollable

    fun estimatedSizeLabel(estimatedBytes: Long?): String? = estimatedBytes
        ?.takeIf { it >= 0L }
        ?.let { bytes -> "${bytes / (1024L * 1024L)} MB estimated" }
}
