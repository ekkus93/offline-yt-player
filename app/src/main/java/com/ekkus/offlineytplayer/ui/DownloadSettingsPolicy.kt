package com.ekkus.offlineytplayer.ui

internal enum class DefaultQuality { BestCompatible, High, Medium, Low, AudioOnly }
internal enum class SubtitleDefault { Off, PreferredLanguage, AnyAvailable }
internal enum class RetryPreference { Manual, Automatic }

internal data class DownloadSettings(
    val defaultQuality: DefaultQuality = DefaultQuality.BestCompatible,
    val wifiOnly: Boolean = false,
    val concurrentDownloadLimit: Int = 2,
    val subtitleDefault: SubtitleDefault = SubtitleDefault.Off,
    val retryPreference: RetryPreference = RetryPreference.Automatic,
) {
    init {
        require(concurrentDownloadLimit in 1..3) { "concurrent download limit must be between 1 and 3" }
    }
}

internal object DownloadSettingsPolicy {
    const val MinConcurrentDownloads = 1
    const val MaxConcurrentDownloads = 3

    fun normalizeConcurrentLimit(value: Int): Int =
        value.coerceIn(MinConcurrentDownloads, MaxConcurrentDownloads)
}
