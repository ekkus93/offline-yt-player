package com.ekkus.offlineytplayer.coregateway

internal object SourceMetadataPolicy {
    const val MaxTitleChars = 512
    const val MaxQualityLabelChars = 128

    fun title(value: String): String = sanitize(value, MaxTitleChars, "Untitled video")

    fun qualityLabel(value: String): String = sanitize(value, MaxQualityLabelChars, "Unknown quality")

    private fun sanitize(value: String, maxChars: Int, fallback: String): String {
        val printable = buildString(value.length.coerceAtMost(maxChars)) {
            value.forEach { character ->
                if (length >= maxChars) return@forEach
                when {
                    character == '\n' || character == '\r' || character == '\t' -> append(' ')
                    !character.isISOControl() -> append(character)
                }
            }
        }.trim()
        return printable.ifEmpty { fallback }
    }
}
