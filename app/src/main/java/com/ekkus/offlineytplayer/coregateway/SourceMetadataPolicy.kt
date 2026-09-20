package com.ekkus.offlineytplayer.coregateway

internal object SourceMetadataPolicy {
    const val MaxTitleChars = 512
    const val MaxQualityLabelChars = 128

    fun title(value: String): String = value.trim().take(MaxTitleChars)

    fun qualityLabel(value: String): String = value.trim().take(MaxQualityLabelChars)
}
