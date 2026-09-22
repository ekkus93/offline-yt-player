package com.ekkus.offlineytplayer.coregateway

internal object SourceMetadataPolicy {
    const val MaxTitleChars = 512
    const val MaxChannelChars = 256
    const val MaxDescriptionChars = 4096
    const val MaxQualityLabelChars = 128
    const val MaxDiagnosticChars = 512

    private val UrlPattern = Regex("(?i)https?://\\S+")
    private val SecretAssignmentPattern = Regex("(?i)\\b(token|access_token|auth|authorization|signature|sig|key|api_key)=([^\\s&]+)")

    fun title(value: String): String = sanitize(value, MaxTitleChars, "Untitled video")

    fun channel(value: String): String = sanitize(value, MaxChannelChars, "Unknown channel")

    fun description(value: String): String = sanitize(value, MaxDescriptionChars, "No description")

    fun qualityLabel(value: String): String = sanitize(value, MaxQualityLabelChars, "Unknown quality")

    fun diagnostic(value: String): String = sanitize(redactDiagnostic(value), MaxDiagnosticChars, "Unknown error")

    private fun redactDiagnostic(value: String): String {
        val withoutUrls = UrlPattern.replace(value, "[redacted-url]")
        return SecretAssignmentPattern.replace(withoutUrls) { match -> "${match.groupValues[1]}=[redacted]" }
    }

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
