package com.ekkus.offlineytplayer.coregateway

import java.net.URI

internal object SourceMetadataPolicy {
    const val MaxTitleChars = 512
    const val MaxChannelChars = 256
    const val MaxDescriptionChars = 4096
    const val MaxQualityLabelChars = 128
    const val MaxDiagnosticChars = 512

    private val urlPattern = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    // URI treats a nested URL as ordinary path text, including embedded credentials.
    // Reject credential-looking or nested-URL paths instead of echoing them to UI/export.
    private val unsafeUrlPathPattern = Regex("(?i)(?:https?(?:://|%3a)|@|%40)")

    fun title(value: String): String = sanitize(redactDiagnosticHeaders(value), MaxTitleChars, "Untitled video")

    fun channel(value: String): String = sanitize(redactDiagnosticHeaders(value), MaxChannelChars, "Unknown channel")

    fun description(value: String): String = sanitize(redactDiagnosticHeaders(value), MaxDescriptionChars, "No description")

    fun qualityLabel(value: String): String = sanitize(redactDiagnosticHeaders(value), MaxQualityLabelChars, "Unknown quality")

    fun diagnostic(value: String): String =
        sanitize(redactDiagnosticHeaders(value), MaxDiagnosticChars, "Unknown error")

    /** Preserve useful context, but never allow FFI/provider HTTP header values into Compose. */
    private val sensitiveHeaderPattern =
        Regex("(?i)(?:authorization|proxy-authorization|cookie|set-cookie|x-api-key)\\s*:")

    private fun redactDiagnosticHeaders(value: String): String =
        value.lineSequence().joinToString("\n") { line ->
            val header = sensitiveHeaderPattern.find(line)
            if (header == null) {
                redactDiagnosticUrls(line)
            } else {
                // The suffix may include other credentials; fail closed after the first header.
                val prefix = redactDiagnosticUrls(line.substring(0, header.range.first))
                "$prefix${header.value} [REDACTED]"
            }
        }

    private fun redactDiagnosticUrls(value: String): String = urlPattern.replace(value) { match ->
        runCatching {
            val uri = URI(match.value)
            if (unsafeUrlPathPattern.containsMatchIn(uri.rawPath.orEmpty())) {
                "[REDACTED_URL]"
            } else URI(
                uri.scheme,
                if (uri.rawUserInfo == null) null else "REDACTED",
                uri.host,
                uri.port,
                uri.rawPath,
                if (uri.rawQuery == null) null else "REDACTED",
                if (uri.rawFragment == null) null else "REDACTED",
            ).toASCIIString()
        }.getOrElse { "[REDACTED_URL]" }
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
