package com.ekkus.offlineytplayer.ui

internal enum class PlaybackSubtitleDefault { Off, PreferredLanguage, AnyAvailable }
internal enum class PlaybackAudioDefault { Original, PreferredLanguage }

internal data class PlaybackSettings(
    val rememberPosition: Boolean = true,
    val defaultSpeed: Float = 1.0f,
    val skipIntervalSeconds: Int = 10,
    val subtitleDefault: PlaybackSubtitleDefault = PlaybackSubtitleDefault.Off,
    val audioDefault: PlaybackAudioDefault = PlaybackAudioDefault.Original,
) {
    init {
        require(defaultSpeed in PlaybackSettingsPolicy.MinSpeed..PlaybackSettingsPolicy.MaxSpeed) {
            "default playback speed must be between 0.5x and 2.0x"
        }
        require(skipIntervalSeconds in PlaybackSettingsPolicy.AllowedSkipIntervals) {
            "skip interval must be one of the supported bounded values"
        }
    }
}

internal object PlaybackSettingsPolicy {
    const val MinSpeed = 0.5f
    const val MaxSpeed = 2.0f
    val AllowedSkipIntervals = setOf(5, 10, 15, 30)

    fun normalizeSpeed(value: Float): Float = value.coerceIn(MinSpeed, MaxSpeed)

    fun normalizeSkipInterval(value: Int): Int =
        AllowedSkipIntervals.minBy { candidate -> kotlin.math.abs(candidate - value) }
}
