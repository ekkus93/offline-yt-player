package com.ekkus.offlineytplayer.coregateway

import java.net.URI

internal object SourceMetadataPolicy {
    const val MaxTitleChars = 512
    const val MaxChannelChars = 256
    const val MaxDescriptionChars = 4096
    const val MaxQualityLabelChars = 128
    const val MaxDiagnosticChars = 512
    // Bound CPU and allocation before running URL/header regexes over provider-controlled text.
    private const val MaxRawPresentationChars = 65_536

    // Protocol-relative and fully percent-encoded HTTP(S) URLs may carry signed tokens.
    private val urlPattern = Regex("(?:https?://|(?<![\\w:])//|https?%3a%2f%2f)[^\\s]+", RegexOption.IGNORE_CASE)
    // URI treats a nested URL as ordinary path text, including embedded credentials.
    // Reject credential-looking or nested-URL paths instead of echoing them to UI/export.
    private val unsafeUrlPathPattern = Regex("(?i)(?:https?(?:://|%(?:25)*3a)|@|%(?:25)*(?:40|3f|23))")

    fun title(value: String): String = present(value, MaxTitleChars, "Untitled video")

    fun channel(value: String): String = present(value, MaxChannelChars, "Unknown channel")

    fun description(value: String): String = present(value, MaxDescriptionChars, "No description")

    fun qualityLabel(value: String): String = present(value, MaxQualityLabelChars, "Unknown quality")

    fun diagnostic(value: String): String = present(value, MaxDiagnosticChars, "Unknown error")

    /** Oversized untrusted strings fail closed before expensive regex scans or line copies. */
    private fun present(value: String, maxChars: Int, fallback: String): String =
        if (value.length > MaxRawPresentationChars) fallback
        else sanitize(redactDiagnosticHeaders(value), maxChars, fallback)

    /** Preserve useful context, but never allow FFI/provider HTTP header values into Compose. */
    private val sensitiveHeaderPattern =
        Regex("(?i)(?:authorization|proxy-authorization|cookie|set-cookie|x-api-key)\\s*:")

    private fun redactDiagnosticHeaders(value: String): String {
        var redactingFoldedHeader = false
        return value.lineSequence().joinToString("\n") { line ->
            val header = sensitiveHeaderPattern.find(line)
            if (redactingFoldedHeader && header == null &&
                (line.startsWith(' ') || line.startsWith('\t'))
            ) {
                // Indented follow-on header values must not reappear without the field name.
                "[REDACTED_HEADER_CONTINUATION]"
            } else {
                redactingFoldedHeader = header != null
                if (header == null) {
                    redactDiagnosticUrls(line)
                } else {
                    // A new indented named header still retains its redacted field label.
                    val prefix = redactDiagnosticUrls(line.substring(0, header.range.first))
                    "$prefix${header.value} [REDACTED]"
                }
            }
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
